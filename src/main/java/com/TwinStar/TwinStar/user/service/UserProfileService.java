package com.TwinStar.TwinStar.user.service;

import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.common.domain.Visibility;
import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.common.exception.DuplicateNicknameException;
import com.TwinStar.TwinStar.common.exception.PrivateAccountException;
import com.TwinStar.TwinStar.common.exception.SuspendedAccountException;
import com.TwinStar.TwinStar.common.service.S3Service;
import com.TwinStar.TwinStar.follow.repository.FollowRepository;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.domain.PostFile;
import com.TwinStar.TwinStar.post.dto.ProfilePostResDto;
import com.TwinStar.TwinStar.post.repository.PostLikeRepository;
import com.TwinStar.TwinStar.post.repository.PostRepository;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.domain.UserStatus;
import com.TwinStar.TwinStar.user.dto.ProfileTextUpdateDto;
import com.TwinStar.TwinStar.user.dto.UserProfileDto;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 사용자 프로필 관리 서비스
 * - 프로필 조회
 * - 프로필 이미지 수정
 * - 프로필 텍스트 수정
 * - 계정 공개 범위 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final String DEFAULT_PROFILE_IMG = "https://i.pinimg.com/474x/3b/73/a1/3b73a13983f88f84e130bb3fb29e17.jpg";

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final S3Service s3Service;

    /**
     * 프로필 조회
     * @param receiveUserId 조회할 사용자 ID
     * @return 사용자 프로필 정보
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     * @throws SuspendedAccountException 계정이 정지된 경우
     * @throws PrivateAccountException 비공개 계정인 경우
     */
    public UserProfileDto searchProfile(Long receiveUserId) throws NoSuchElementException, RuntimeException {
        User currentUser = getCurrentUser();

        // 조회하려는 사용자
        User targetUser = userRepository.findById(receiveUserId)
                .orElseThrow(() -> new EntityNotFoundException("user not found"));

        // 탈퇴한 계정 체크
        if (targetUser.getDelYn() == YN.Y) {
            throw new EntityNotFoundException("해당 계정은 탈퇴한 사용자입니다.");
        }

        // 정지된 계정 체크
        if (targetUser.getUserStatus() == UserStatus.BAN) {
            throw new SuspendedAccountException("해당 계정은 정지되었습니다.");
        }

        // 팔로워/팔로잉 수 계산
        Long followingCount = followRepository.countByReceiveUserAndFollowYn(targetUser, YN.Y);
        Long followerCount = followRepository.countByUserAndFollowYn(targetUser, YN.Y);

        // 자신의 프로필이 아닐 경우 비공개 설정 체크
        if (!currentUser.equals(targetUser)) {
            boolean isFollow = followRepository.existsByUserAndReceiveUser(currentUser, targetUser);

            if (targetUser.getIdVisibility() == Visibility.ONLYME) {
                throw new PrivateAccountException("이 계정은 비공개 상태입니다.");
            }

            if (targetUser.getIdVisibility() == Visibility.FOLLOW && !isFollow) {
                throw new PrivateAccountException("이 계정은 비공개 상태입니다.");
            }
        }

        // 프로필 이미지 URL 설정
        String profileImgUrl = (targetUser.getProfileImg() != null) ? targetUser.getProfileImg() : DEFAULT_PROFILE_IMG;

        // 게시물 목록과 파일 정보를 함께 조회 (N+1 문제 해결)
        List<Post> posts = postRepository.findByUserIdWithFiles(receiveUserId);
        if (posts.isEmpty()) {
            return UserProfileDto.profileSearch(targetUser, followerCount, followingCount, profileImgUrl, new ArrayList<>());
        }

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        // 좋아요 수, 댓글 수 일괄 조회
        Map<Long, Long> postLikeCounts = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        Map<Long, Long> commentCounts = commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        // 프로필 게시물 DTO 변환
        List<ProfilePostResDto> profilePostResDtoList = posts.stream().map(post -> {
            String fileUrl = post.getPostFile().stream()
                    .findFirst()
                    .map(PostFile::getFileUrl)
                    .orElse(null);
            return ProfilePostResDto.fromEntity(
                    post.getId(),
                    fileUrl,
                    postLikeCounts.getOrDefault(post.getId(), 0L),
                    commentCounts.getOrDefault(post.getId(), 0L)
            );
        }).collect(Collectors.toList());

        return UserProfileDto.profileSearch(
                targetUser,
                followerCount,
                followingCount,
                profileImgUrl,
                profilePostResDtoList
        );
    }

    /**
     * 프로필 이미지 업로드
     * @param file 업로드할 이미지 파일
     * @return 업로드된 이미지 URL
     * @throws IOException 파일 업로드 실패 시
     */
    @Transactional
    public String updateProfileImage(MultipartFile file) throws IOException {
        User user = getCurrentUser();

        // 새 이미지 업로드
        String imageUrl = s3Service.uploadFile(file, file.getOriginalFilename());
        user.updateProfileImage(imageUrl);
        userRepository.save(user);

        log.info("[UserProfileService] 프로필 이미지 업데이트 완료 - userId: {}, imageUrl: {}", user.getId(), imageUrl);
        return imageUrl;
    }

    /**
     * 프로필 텍스트 수정
     * @param dto 프로필 텍스트 수정 정보
     * @throws DuplicateNicknameException 닉네임 중복 시
     */
    @Transactional
    public void updateProfileText(ProfileTextUpdateDto dto) {
        User user = getCurrentUser();

        // 닉네임 변경 시 중복 확인
        if (dto.getNickName() != null && !user.getNickName().equals(dto.getNickName())) {
            if (userRepository.existsByNickName(dto.getNickName())) {
                throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
            }
        }

        user.updateProfile(dto.getNickName(), dto.getProfileTxt(), dto.getIdVisibility(), dto.getSex());
        userRepository.save(user);

        log.info("[UserProfileService] 프로필 텍스트 업데이트 완료 - userId: {}, nickname: {}", user.getId(), dto.getNickName());
    }

    /**
     * 계정 공개 범위 변경
     * @param newStatus 새로운 공개 범위 (ALL, FOLLOW, ONLYME)
     * @throws IllegalArgumentException 공개 범위 값이 null인 경우
     * @throws IllegalStateException 이미 동일한 상태인 경우
     */
    @Transactional
    public void changeIdVisibility(Visibility newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("변경할 계정 범위 값이 없습니다.");
        }

        User user = getCurrentUser();

        // User 엔티티에게 상태 변경 위임
        user.changeStatus(newStatus);
        userRepository.save(user);

        log.info("[UserProfileService] 계정 공개 범위 변경 완료 - userId: {}, visibility: {}", user.getId(), newStatus);
    }

    /**
     * 현재 인증된 사용자 조회
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 존재하지 않습니다.");
        }
        Long userId = Long.valueOf(authentication.getName());
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }
}
