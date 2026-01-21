package com.TwinStar.TwinStar.post.service;

import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.comment.repository.CommentLikeRepository;
import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.follow.repository.FollowRepository;
import com.TwinStar.TwinStar.hashTag.domain.HashTag;
import com.TwinStar.TwinStar.hashTag.domain.PostHashTag;
import com.TwinStar.TwinStar.hashTag.repository.PostHashTagRepository;
import com.TwinStar.TwinStar.hashTag.service.HashTagService;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.domain.PostFile;
import com.TwinStar.TwinStar.post.dto.*;
import com.TwinStar.TwinStar.post.repository.PostFileRepository;
import com.TwinStar.TwinStar.post.repository.PostLikeRepository;
import com.TwinStar.TwinStar.post.repository.PostRepository;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.UserListResDto;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostFileRepository postFileRepository;
    private final HashTagService hashTagService;
    private final PostHashTagRepository postHashTagRepository;
    private final FollowRepository followRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostLikeRepository postLikeRepository;

    private final S3Client s3Client;
    private final TransactionTemplate transactionTemplate;
    private final Executor imageUploadExecutor; // 커스텀 스레드 풀

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    @Value("${cloud.aws.region.static}")
    private String region;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostFileRepository postFileRepository
            , HashTagService hashTagService, PostHashTagRepository postHashTagRepository, FollowRepository followRepository, CommentRepository commentRepository, CommentLikeRepository commentLikeRepository, PostLikeRepository postLikeRepository, S3Client s3Client, PlatformTransactionManager transactionManager, @Qualifier("imageUploadExecutor") Executor imageUploadExecutor) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postFileRepository = postFileRepository;
        this.hashTagService = hashTagService;
        this.postHashTagRepository = postHashTagRepository;
        this.followRepository = followRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.s3Client = s3Client;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.imageUploadExecutor = imageUploadExecutor;
        
        // 트랜잭션 설정
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); // 격리 수준: READ_COMMITTED
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED); // 전파 속성: REQUIRED (기본값)
        this.transactionTemplate.setTimeout(30); // 타임아웃: 30초
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 존재하지 않습니다.");
        }
        Long userId = Long.valueOf(authentication.getName());
        // getReferenceById를 사용하여 불필요한 SELECT 쿼리 방지 (프록시 객체 반환)
        return userRepository.getReferenceById(userId);
    }

    // 업로드 결과를 담을 내부 클래스 (메모리 최적화)
    @Getter
    @AllArgsConstructor
    private static class UploadResult {
        private String fileUrl;
        private String fileName;
    }

    public Long save(PostCreateReqDto dto) {
        User user = getCurrentUser();

        List<UploadResult> uploadResults = new ArrayList<>();

        try {
            // 1. S3 병렬 업로드 (CompletableFuture 사용 - 메모리 최적화 & 커스텀 스레드 풀)
            if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
                List<CompletableFuture<UploadResult>> futures = dto.getImageFile().stream()
                        .map(file -> CompletableFuture.supplyAsync(() -> {
                            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                            String fileUrl = uploadImage(file, fileName);
                            return new UploadResult(fileUrl, fileName);
                        }, imageUploadExecutor)) // 커스텀 스레드 풀 사용
                        .collect(Collectors.toList());

                // 모든 업로드가 끝날 때까지 대기 후 결과 수집
                uploadResults = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());
            }

            // final 변수로 만들어 람다 내부에서 사용 가능하게 함
            List<UploadResult> finalUploadResults = uploadResults;

            // 2. DB 저장 (트랜잭션 내부)
            return transactionTemplate.execute(status -> {
                Post post = postRepository.save(dto.toEntity(user));

                // 이미지 일괄 저장 (Bulk Insert)
                if (!finalUploadResults.isEmpty()) {
                    List<PostFile> postFiles = finalUploadResults.stream()
                            .map(result -> new PostFile(post, result.getFileUrl()))
                            .collect(Collectors.toList());
                    postFileRepository.saveAll(postFiles);
                }

                List<String> tagNames = Optional.ofNullable(dto.getHashTag()).orElse(Collections.emptyList());
                if (!tagNames.isEmpty()) {
                    List<HashTag> hashTags = hashTagService.findOrCreateHashTags(tagNames);

                    List<PostHashTag> postHashTags = hashTags.stream()
                            .map(hashTag -> PostHashTag.builder()
                                    .post(post)
                                    .hashTag(hashTag)
                                    .build())
                            .collect(Collectors.toList());

                    postHashTagRepository.saveAll(postHashTags);
                }

                return post.getId();
            });
        } catch (Exception e) {
            // 3. 예외 발생 시 S3 파일 삭제 (보상 트랜잭션)
            for (UploadResult result : uploadResults) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(result.getFileName())
                            .build());
                } catch (Exception s3Ex) {
                    log.error("Failed to delete file from S3 during rollback: {}", result.getFileName(), s3Ex);
                }
            }
            throw e;
        }
    }

    // 파일명(Key)을 외부에서 지정할 수 있도록 오버로딩
    public String uploadImage(MultipartFile file, String fileName) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(fileName)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패", e);
        }
    }

    // 기존 메서드 유지 (다른 곳에서 사용할 수 있으므로)
    public String uploadImage(MultipartFile file) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        return uploadImage(file, fileName);
    }

    @Transactional
    public void delete(Long postId) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증 위임
        if (!post.getUser().getId().equals(loginUser.getId())) {
             throw new AccessDeniedException("해당 게시물에 대한 권한이 없습니다.");
        }
        
        postRepository.delete(post);
    }

    @Transactional
    public void Update(Long postId, PostUpdateReqDto dto) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증
        if (!post.getUser().getId().equals(loginUser.getId())) {
             throw new AccessDeniedException("해당 게시물에 대한 권한이 없습니다.");
        }
        
        post.updateContent(dto.getContent());

        hashTagService.removeAllHashtagsFromPost(post);
        
        // 해시태그 일괄 처리 (Bulk Insert)
        List<String> tagNames = dto.getHashTag();
        if (tagNames != null && !tagNames.isEmpty()) {
            List<HashTag> hashTags = hashTagService.findOrCreateHashTags(tagNames);
            
            List<PostHashTag> postHashTags = hashTags.stream()
                    .map(hashTag -> PostHashTag.builder()
                            .post(post)
                            .hashTag(hashTag)
                            .build())
                    .collect(Collectors.toList());
            
            postHashTagRepository.saveAll(postHashTags);
        }

    }

    @Transactional(readOnly = true)
    public PostUpdateResDto getUpdateDataRes(Long postId) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증
        if (!post.getUser().getId().equals(loginUser.getId())) {
             throw new AccessDeniedException("해당 게시물에 대한 권한이 없습니다.");
        }
        
        List<String> postUrlList = post.getFileUrls();
        List<String> postHashTagList = hashTagService.getHashTagsByPost(post);
        return post.formEntity(postHashTagList, postUrlList);

    }

    @Transactional(readOnly = true)
    public Page<PostListResDto> getList(int page, int size) {
        User loginUser = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")); // 최신순 정렬

        Page<Post> postPage = postRepository.findFeedPostsForUser(loginUser.getId(), pageable);
        
        if (postPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> postIds = postPage.getContent().stream().map(Post::getId).collect(Collectors.toList());
        List<Long> authorIds = postPage.getContent().stream().map(post -> post.getUser().getId()).collect(Collectors.toList());

        Map<Long, Long> likeCounts = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));
        
        Map<Long, Long> commentCounts = commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        Set<Long> likedPostIds = postLikeRepository.findLikedPostIdsByUserId(loginUser.getId(), postIds);

        Set<Long> followingAuthorIds = followRepository.findFollowingUserIdsIn(loginUser.getId(), authorIds);

        // 게시물 파일 일괄 조회 (N+1 해결)
        List<PostFile> allPostFiles = postFileRepository.findByPostIn(postPage.getContent());
        Map<Long, List<String>> postFileMap = allPostFiles.stream()
                .collect(Collectors.groupingBy(
                        pf -> pf.getPost().getId(),
                        Collectors.mapping(PostFile::getFileUrl, Collectors.toList())
                ));

        // 게시물 해시태그 일괄 조회 (N+1 해결)
        List<PostHashTag> allPostHashTags = postHashTagRepository.findByPostIn(postPage.getContent());
        Map<Long, List<String>> postHashTagMap = allPostHashTags.stream()
                .collect(Collectors.groupingBy(
                        pht -> pht.getPost().getId(),
                        Collectors.mapping(pht -> pht.getHashTag().getHashTagName(), Collectors.toList())
                ));

        return postPage.map(post -> {
            Long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
            Long commentCount = commentCounts.getOrDefault(post.getId(), 0L);

            // 미리 조회한 해시태그 목록 사용
            List<String> hashTags = postHashTagMap.getOrDefault(post.getId(), Collections.emptyList());

            boolean isLiked = likedPostIds.contains(post.getId());
            String isLike = isLiked ? "Y" : "N";

            boolean isFollowed = followingAuthorIds.contains(post.getUser().getId()) || loginUser.getId().equals(post.getUser().getId());
            String isFollow = isFollowed ? "Y" : "N";

            // 미리 조회한 파일 URL 사용
            List<String> fileUrls = postFileMap.getOrDefault(post.getId(), Collections.emptyList());

            // PostListResDto 생성 시 fileUrls 전달 (기존 fromEntity 수정 필요 또는 직접 빌더 사용)
            return PostListResDto.builder()
                    .userId(post.getUser().getId())
                    .nickName(post.getUser().getNickName())
                    .profileImage(post.getUser().getProfileImg())
                    .postId(post.getId())
                    .imageList(fileUrls) // 최적화된 파일 목록 사용
                    .content(post.getContent())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .createdTime(post.getCreatedTime())
                    .isUpdate((post.getUpdatedTime() != null && !post.getUpdatedTime().equals(post.getCreatedTime())) ? "Y" : "N")
                    .hashTag(hashTags)
                    .isLike(isLike)
                    .isFollow(isFollow)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public Page<PostListResDto> getHashtagPostList(String hashtag, int page, int size) {
        User loginUser = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")); // 최신순 정렬

        // 해시태그로 게시물 검색 (페이징)
        Page<Post> postPage = postRepository.findByHashTag(hashtag, pageable);

        if (postPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> postIds = postPage.getContent().stream().map(Post::getId).collect(Collectors.toList());
        List<Long> authorIds = postPage.getContent().stream().map(post -> post.getUser().getId()).collect(Collectors.toList());

        // 좋아요 수, 댓글 수 일괄 조회 (N+1 해결)
        Map<Long, Long> likeCounts = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        Map<Long, Long> commentCounts = commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        // 로그인한 유저가 좋아요한 게시물 ID 목록 조회 (N+1 해결)
        Set<Long> likedPostIds = postLikeRepository.findLikedPostIdsByUserId(loginUser.getId(), postIds);

        // 로그인한 유저가 팔로우한 작성자 ID 목록 조회 (N+1 해결)
        Set<Long> followingAuthorIds = followRepository.findFollowingUserIdsIn(loginUser.getId(), authorIds);

        // 게시물 파일 일괄 조회 (N+1 해결)
        List<PostFile> allPostFiles = postFileRepository.findByPostIn(postPage.getContent());
        Map<Long, List<String>> postFileMap = allPostFiles.stream()
                .collect(Collectors.groupingBy(
                        pf -> pf.getPost().getId(),
                        Collectors.mapping(PostFile::getFileUrl, Collectors.toList())
                ));

        // 게시물 해시태그 일괄 조회 (N+1 해결)
        List<PostHashTag> allPostHashTags = postHashTagRepository.findByPostIn(postPage.getContent());
        Map<Long, List<String>> postHashTagMap = allPostHashTags.stream()
                .collect(Collectors.groupingBy(
                        pht -> pht.getPost().getId(),
                        Collectors.mapping(pht -> pht.getHashTag().getHashTagName(), Collectors.toList())
                ));

        return postPage.map(post -> {
            Long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
            Long commentCount = commentCounts.getOrDefault(post.getId(), 0L);

            // 미리 조회한 해시태그 목록 사용
            List<String> hashTags = postHashTagMap.getOrDefault(post.getId(), Collections.emptyList());

            boolean isLiked = likedPostIds.contains(post.getId());
            String isLike = isLiked ? "Y" : "N";

            boolean isFollowed = followingAuthorIds.contains(post.getUser().getId()) || loginUser.getId().equals(post.getUser().getId());
            String isFollow = isFollowed ? "Y" : "N";

            // 미리 조회한 파일 URL 사용
            List<String> fileUrls = postFileMap.getOrDefault(post.getId(), Collections.emptyList());

            return PostListResDto.builder()
                    .userId(post.getUser().getId())
                    .nickName(post.getUser().getNickName())
                    .profileImage(post.getUser().getProfileImg())
                    .postId(post.getId())
                    .imageList(fileUrls) // 최적화된 파일 목록 사용
                    .content(post.getContent())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .createdTime(post.getCreatedTime())
                    .isUpdate((post.getUpdatedTime() != null && !post.getUpdatedTime().equals(post.getCreatedTime())) ? "Y" : "N")
                    .hashTag(hashTags)
                    .isLike(isLike)
                    .isFollow(isFollow)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public PostDetailResDto getDetail(Long postId) {
        User user = getCurrentUser();

        // 게시물 조회 (없으면 예외 발생)
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다."));

        // 게시물 좋아요 개수 조회
        Long postLikeCount = postRepository.countPostLikes(postId);

        // 댓글 목록 조회 (N+1 해결)
        List<Comment> comments = commentRepository.findByPostWithUser(post);
        
        // 댓글 ID 목록 추출
        List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
        
        // 댓글 좋아요 수 일괄 조회 (N+1 해결)
        Map<Long, Long> commentLikeCounts = new HashMap<>();
        Set<Long> likedCommentIds = new HashSet<>();
        
        if (!commentIds.isEmpty()) {
            commentLikeCounts = commentLikeRepository.countByCommentIds(commentIds).stream()
                    .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));
            
            // 로그인한 유저가 좋아요한 댓글 ID 목록 조회 (N+1 해결)
            likedCommentIds = commentLikeRepository.findLikedCommentIdsByUserId(user.getId(), commentIds);
        }

        // final 변수로 만들어 람다 내부에서 사용 가능하게 함
        Map<Long, Long> finalCommentLikeCounts = commentLikeCounts;
        Set<Long> finalLikedCommentIds = likedCommentIds;

        List<CommentListResDto> commentList = comments.stream()
                .map(comment -> {
                    Long commentLikeCount = finalCommentLikeCounts.getOrDefault(comment.getId(), 0L);
                    boolean isCommentLiked = finalLikedCommentIds.contains(comment.getId());
                    String isCommentLike = isCommentLiked ? "Y" : "N";
                    return CommentListResDto.fromEntity(comment, commentLikeCount, isCommentLike);
                })
                .collect(Collectors.toList());

        // 해시태그 목록 조회
        List<String> hashTags = post.getHashTag().stream()
                .map(postHashTag -> postHashTag.getHashTag().getHashTagName())
                .collect(Collectors.toList());

        // 사용자의 좋아요 여부 확인
        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, user.getId());
        String isLike = isLiked ? "Y" : "N";

        String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(user,post.getUser(), YN.Y)||user.equals(post.getUser()) ? "Y" : "N";

        // DTO 변환 후 반환
        return PostDetailResDto.fromEntity(post, postLikeCount, commentList, hashTags, isLike, isFollow);
    }

    @Transactional(readOnly = true)
    public Page<UserListResDto> getLikeList(Long postId, Pageable pageable) {
        User loginUser = getCurrentUser();

        // 해당 게시물을 좋아요 한 유저 목록 조회 (페이징)
        Page<User> likedUsers = postLikeRepository.findUsersWhoLikedPost(postId, pageable);

        // 각 유저와 로그인한 유저 간의 팔로우 여부 확인
        return likedUsers.map(user -> {
            String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,user, YN.Y)||user.equals(loginUser) ? "Y" : "N";

            return new UserListResDto().toUserListResDto(user, isFollow);
        });
    }
}
