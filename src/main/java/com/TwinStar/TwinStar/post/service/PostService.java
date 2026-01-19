package com.TwinStar.TwinStar.post.service;

import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.comment.repository.CommentLikeRepository;
import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.common.domain.Visibility;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
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
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    @Value("${cloud.aws.region.static}")
    private String region;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostFileRepository postFileRepository
            , HashTagService hashTagService, PostHashTagRepository postHashTagRepository, FollowRepository followRepository, CommentRepository commentRepository, CommentLikeRepository commentLikeRepository, PostLikeRepository postLikeRepository, S3Client s3Client) {
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
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 존재하지 않습니다.");
        }
        Long userId = Long.valueOf(authentication.getName());
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }

    public Long save(PostCreateReqDto dto) {
        User user = getCurrentUser();
        Post post = postRepository.save(dto.toEntity(user));
        for (MultipartFile file : dto.getImageFile()){
            String fileUrl = uploadImage(file);
            postFileRepository.save(new PostFile(post,fileUrl));
        }
        for (String tag: Optional.ofNullable(dto.getHashTag()).orElse(Collections.emptyList()) ){
            HashTag hashTag = hashTagService.findOrCreateHashTag(tag);
            PostHashTag postHashTag = PostHashTag.builder()
                    .post(post)
                    .hashTag(hashTag)
                    .build();
            postHashTagRepository.save(postHashTag);
        }
        return post.getId();
    }

    public String uploadImage(MultipartFile file) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

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

    public void delete(Long postId) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증 위임
        post.validateOwner(loginUser);
        
        postRepository.delete(post);
    }

    public void Update(Long postId, PostUpdateReqDto dto) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증 위임
        post.validateOwner(loginUser);
        
        post.updateContent(dto.getContent());

        hashTagService.removeAllHashtagsFromPost(post);
        for (String tag: dto.getHashTag()){
            HashTag hashTag = hashTagService.findOrCreateHashTag(tag);
            PostHashTag postHashTag = PostHashTag.builder()
                    .post(post)
                    .hashTag(hashTag)
                    .build();
            postHashTagRepository.save(postHashTag);
        }

    }

    public PostUpdateResDto getUpdateDataRes(Long postId) {
        User loginUser = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        
        // 권한 검증 위임
        post.validateOwner(loginUser);
        
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

        return postPage.map(post -> {
            Long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
            Long commentCount = commentCounts.getOrDefault(post.getId(), 0L);

            List<String> hashTags = post.getHashTag().stream()
                    .map(postHashTag -> postHashTag.getHashTag().getHashTagName())
                    .collect(Collectors.toList());

            boolean isLiked = likedPostIds.contains(post.getId());
            String isLike = isLiked ? "Y" : "N";

            boolean isFollowed = followingAuthorIds.contains(post.getUser().getId()) || loginUser.getId().equals(post.getUser().getId());
            String isFollow = isFollowed ? "Y" : "N";

            return PostListResDto.fromEntity(post, likeCount, commentCount, hashTags, isLike, isFollow);
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

        return postPage.map(post -> {
            Long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
            Long commentCount = commentCounts.getOrDefault(post.getId(), 0L);

            List<String> hashTags = post.getHashTag().stream()
                    .map(postHashTag -> postHashTag.getHashTag().getHashTagName())
                    .collect(Collectors.toList());

            boolean isLiked = likedPostIds.contains(post.getId());
            String isLike = isLiked ? "Y" : "N";

            boolean isFollowed = followingAuthorIds.contains(post.getUser().getId()) || loginUser.getId().equals(post.getUser().getId());
            String isFollow = isFollowed ? "Y" : "N";

            return PostListResDto.fromEntity(post, likeCount, commentCount, hashTags, isLike, isFollow);
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

        // 댓글 목록 조회
        List<Comment> comments = commentRepository.findByPost(post);
        
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
