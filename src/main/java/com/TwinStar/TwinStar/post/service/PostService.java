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
import com.TwinStar.TwinStar.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
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
    private final UserService userService;

    private final S3Client s3Client;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    @Value("${cloud.aws.region.static}")
    private String region;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostFileRepository postFileRepository
            , HashTagService hashTagService, PostHashTagRepository postHashTagRepository, FollowRepository followRepository, CommentRepository commentRepository, CommentLikeRepository commentLikeRepository, PostLikeRepository postLikeRepository, UserService userService, S3Client s3Client) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postFileRepository = postFileRepository;
        this.hashTagService = hashTagService;
        this.postHashTagRepository = postHashTagRepository;
        this.followRepository = followRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.userService = userService;
        this.s3Client = s3Client;
    }

    public Long save(PostCreateReqDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()-> new EntityNotFoundException("user is not found."));
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        User postWriteUser = post.getUser();

        if (!loginUser.equals(postWriteUser)){ return ; }
        postRepository.delete(post);
    }

    public void Update(Long postId, PostUpdateReqDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        User postWriteUser = post.getUser();

        if (!loginUser.equals(postWriteUser)){ return ; }
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Post post = postRepository.findById(postId).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        User postWriteUser = post.getUser();
        if (!loginUser.equals(postWriteUser)){ return new PostUpdateResDto(); }
        List<String> postUrlList = post.getFileUrls();
        List<String> postHashTagList = hashTagService.getHashTagsByPost(post);
        return post.formEntity(postHashTagList, postUrlList);

    }

    @Transactional(readOnly = true)
    public Page<PostListResDto> getList(int page, int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName()))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")); // 최신순 정렬

        return postRepository.findFeedPostsForUser(loginUser.getId(), pageable)
                .map(post -> {
                    Long likeCount = postRepository.countPostLikes(post.getId());
                    Long commentCount = postRepository.countPostComments(post.getId());
                    String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,post.getUser(), YN.Y)||loginUser.equals(post.getUser()) ? "Y" : "N";

                    List<String> hashTags = post.getHashTag().stream()
                            .map(postHashTag -> postHashTag.getHashTag().getHashTagName())
                            .collect(Collectors.toList());

                    boolean isLiked = postLikeRepository.existsByPostIdAndUserId(post.getId(), loginUser.getId());
                    String isLike = isLiked ? "Y" : "N";

                    return PostListResDto.fromEntity(post, likeCount, commentCount, hashTags, isLike,isFollow);
                });
    }

    @Transactional(readOnly = true)
    public PostDetailResDto getDetail(Long postId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(() -> new EntityNotFoundException("User not found"));

        // 게시물 조회 (없으면 예외 발생)
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다."));

        // 게시물 좋아요 개수 조회
        Long postLikeCount = postRepository.countPostLikes(postId);

        // 댓글 목록 조회
        List<Comment> comments = commentRepository.findByPost(post);
        List<CommentListResDto> commentList = comments.stream()
                .map(comment -> {
                    Long commentLikeCount = commentRepository.countCommentLikes(comment.getId());
                    boolean isCommentLiked = commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), user.getId());
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
        // 현재 로그인한 사용자 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName()))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // 해당 게시물을 좋아요 한 유저 목록 조회 (페이징)
        Page<User> likedUsers = postLikeRepository.findUsersWhoLikedPost(postId, pageable);

        // 각 유저와 로그인한 유저 간의 팔로우 여부 확인
        return likedUsers.map(user -> {
            String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,user, YN.Y)||user.equals(loginUser) ? "Y" : "N";

            return new UserListResDto().toUserListResDto(user, isFollow);
        });
    }

    public Page<PostListResDto> getHashtagPostList(String hashtag,Integer page, Integer size) {
        User loginUser = userService.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")); // 최신순 정렬

        return postRepository.findVisiblePostsByHashtags(hashtag,loginUser.getId(), pageable)
                .map(post -> {
                    Long likeCount = postRepository.countPostLikes(post.getId());
                    Long commentCount = postRepository.countPostComments(post.getId());
                    String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,post.getUser(), YN.Y)||loginUser.equals(post.getUser()) ? "Y" : "N";

                    List<String> hashTags = post.getHashTag().stream()
                            .map(postHashTag -> postHashTag.getHashTag().getHashTagName())
                            .collect(Collectors.toList());

                    boolean isLiked = postLikeRepository.existsByPostIdAndUserId(post.getId(), loginUser.getId());
                    String isLike = isLiked ? "Y" : "N";

                    return PostListResDto.fromEntity(post, likeCount, commentCount, hashTags, isLike,isFollow);
                });
    }
}
