package com.TwinStar.TwinStar.user.service;


import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.common.domain.Visibility;
import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.common.exception.DuplicateEmailException;
import com.TwinStar.TwinStar.common.exception.DuplicateNicknameException;
import com.TwinStar.TwinStar.common.exception.LoginFailedException;
import com.TwinStar.TwinStar.common.exception.PrivateAccountException;
import com.TwinStar.TwinStar.common.exception.SuspendedAccountException;
import com.TwinStar.TwinStar.common.service.S3Service;
import com.TwinStar.TwinStar.follow.repository.FollowRepository;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.domain.PostFile;
import com.TwinStar.TwinStar.post.dto.ProfilePostResDto;
import com.TwinStar.TwinStar.post.repository.PostLikeRepository;
import com.TwinStar.TwinStar.post.repository.PostRepository;
import com.TwinStar.TwinStar.user.domain.AdminYn;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.domain.UserStatus;
import com.TwinStar.TwinStar.user.dto.*;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {
    private static final String DEFAULT_PROFILE_IMG = "https://i.pinimg.com/474x/3b/73/a1/3b73a13983f88f84e130bb3fb29e17.jpg";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FollowRepository followRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final S3Service s3Service;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, FollowRepository followRepository, PostLikeRepository postLikeRepository, CommentRepository commentRepository, PostRepository postRepository, S3Service s3Service, @Qualifier("rtdb") RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.followRepository = followRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.s3Service = s3Service;
        this.redisTemplate = redisTemplate;
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

//   1. 로그인
    public User login(LoginDto dto){
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new LoginFailedException("email 또는 비밀번호가 일치하지 않습니다."));

        // User 객체에게 검증 위임
        user.validateLogin();
        user.validatePassword(dto.getPassword(), passwordEncoder);

        return user;
    }

    @Transactional
    public void logout() {
        User user = getCurrentUser();
        redisTemplate.delete(user.getEmail());
    }

//   2. 회원가입
    @Transactional
    public Long create(UserSaveReq dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateEmailException("중복 이메일입니다.");
        }
//        닉네임 중복체크 메서드
        if (userRepository.existsByNickName(dto.getNickName())){
            throw new DuplicateNicknameException("중복된 닉네임입니다");
        }
        User user = userRepository.save(dto.toEntity(passwordEncoder.encode(dto.getPassword())));
        return user.getId();
    }

//    회원가입에서 비동기 중복이메일 검증
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

//    회원가입에서 비동기 중복닉네임 검증
    public boolean existsByNickName(String nickname) {
        // 닉네임 중복 여부 확인
        return userRepository.existsByNickName(nickname);
    }

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
        Long followerCount = followRepository.countByUserAndFollowYn(targetUser, YN.Y); // 수정된 부분

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

        Map<Long, Long> postLikeCounts = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

        Map<Long, Long> commentCounts = commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (Long) o[1]));

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

//    프로필 이미지 업로드
    @Transactional
    public String updateProfileImage(MultipartFile file) throws IOException{
        User user = getCurrentUser();

//        새 이미지 업록드
        String imageUrl = s3Service.uploadFile(file, file.getOriginalFilename());
        user.updateProfileImage(imageUrl);
        userRepository.save(user);
        return imageUrl;
    }

//    프로필 텍스트 수정
    @Transactional
    public void updateProfileText(ProfileTextUpdateDto dto){
        User user = getCurrentUser();

        // 2. 닉네임 변경 시 중복 확인
        if (dto.getNickName() != null && !user.getNickName().equals(dto.getNickName())) {
            if (userRepository.existsByNickName(dto.getNickName())) {
                throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
            }
        }

        user.updateProfile(dto.getNickName(),dto.getProfileTxt(),dto.getIdVisibility(),dto.getSex());

        userRepository.save(user);
    }


//   6. 회원탈퇴
    @Transactional
    public void deleteUser() {
        User user = getCurrentUser();
        // User 객체에게 탈퇴 처리 위임 (내부에서 상태 검증 후 변경)
        user.deleteUser();

        // Redis에서 Refresh Token 삭제
        redisTemplate.delete(user.getEmail());

        userRepository.save(user); // 변경사항 저장
    }

//   7. 비밀번호 변경
    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        User currentUser = getCurrentUser();
        
        // User 객체에게 검증 위임
        user.validateSelf(currentUser);
        user.validatePassword(request.getCurrentPassword(), passwordEncoder);

        // 새 비밀번호 정책 검증 (예: 8자 이상, 숫자/특수문자 포함)
        if (!isValidPassword(request.getNewPassword())) {
            throw new IllegalArgumentException("비밀번호가 보안 정책을 충족하지 않습니다.");
        }

        // 비밀번호 변경
        user.changePassword(request.getNewPassword(),passwordEncoder);
    }

//   8. 비밀번호 검증
    private boolean isValidPassword(String password) {
        return password.length() >= 8 && password.matches(".*[0-9].*") && password.matches(".*[!@#$%^&*()].*");
    }


//    9. 계정범위 변경
    @Transactional
    public void changeIdVisibility(Visibility newStatus){
        // newStatus가 null이면 예외 발생
        if (newStatus == null) {
            throw new IllegalArgumentException("변경할 계정 범위 값이 없습니다.");
        }

        User user = getCurrentUser();

        // User 객체에게 상태 변경 위임 (내부에서 검증 후 변경)
        user.changeStatus(newStatus);
        
        userRepository.save(user);
    }

//  10. 채팅용 유저리스트
    public Page<ChatUserListDto> chatUserList(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(user -> new ChatUserListDto(user)); // User → ChatUserListDto 변환
    }

    private Specification<User> byNickNameContains(String nickName) {
        return (root, query, criteriaBuilder) -> {
            if (StringUtils.hasText(nickName)) {
                // 참고: LOWER 함수 사용은 DB에 따라 인덱스를 타지 못해 성능 저하를 유발할 수 있음.
                // 대용량 데이터 처리 시 DB에 함수 기반 인덱스(Function-based Index) 생성을 고려해야 함.
                return criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("nickName")),
                        "%" + nickName.toLowerCase() + "%"
                );
            }
            return null;
        };
    }

//  11.  채팅유저 검색
    public Page<ChatUserListDto> searchChatUsers(String nickName, Pageable pageable) {
        Specification<User> spec = byNickNameContains(nickName);
        Page<User> result = userRepository.findAll(spec, pageable);
        return result.map(ChatUserListDto::new);
    }

    //  11-1.  관리자용 유저목록 검색
    public Page<UserListDto> searchListUsers(String nickName, Pageable pageable) {
        Specification<User> spec = byNickNameContains(nickName);
        return userRepository.findAll(spec, pageable).map(User::listFromEntity);
    }

//  12. 관리자용 유저 리스트
    public Page<UserListDto> userList(Pageable pageable, UserSearchDto dto){
        Specification<User> spec = (root, query, criteriaBuilder) -> {
//                root : 엔티티의 속성을 접근하기 위한 객체, criteriabuilder : 쿼리를 생성하기 위한 객체
            List<Predicate> predicates = new ArrayList<>();
            if (dto.getNickName() != null){
                predicates.add(criteriaBuilder.equal(root.get("nickName"),dto.getNickName())); // 필드명 'nickname' -> 'nickName'으로 수정
            }
            Predicate[] predicateArr = new Predicate[predicates.size()];
            for (int i =0; i<predicates.size();i++){
                predicateArr[i] = predicates.get(i);
            }
            Predicate predicate = criteriaBuilder.and(predicateArr);
            return predicate;
        };
        return userRepository.findAll(spec,pageable).map(user-> user.listFromEntity());
    }

//  13.  관리자 유저 상세조회
    public UserDetailDto userDetailList(Long userId){
        User user = userRepository.findById(userId).orElseThrow(()-> new EntityNotFoundException("user is not found"));
        return UserDetailDto.detailList(user);
    }

    private User checkAdminPrivilegeAndGetTargetUser(Long targetUserId) {
        User adminUser = getCurrentUser();
        
        // User 객체에게 검증 위임
        adminUser.validateAdminPrivilege();
        adminUser.validateNotSelf(User.builder().id(targetUserId).build()); // ID만 가진 임시 객체로 비교

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("대상 사용자를 찾을 수 없습니다."));
        if (targetUser.getDelYn() == YN.Y) {
            throw new IllegalStateException("삭제된 계정의 권한을 변경할 수 없습니다.");
        }
        return targetUser;
    }

//  14. 관리자 권한 부여 메소드
    @Transactional
    public void grantAdminRole(Long userid) {
        User receiveUser = checkAdminPrivilegeAndGetTargetUser(userid);
        // User 객체에게 권한 변경 위임
        receiveUser.changeAdmin(AdminYn.ADMIN);
    }
//   15. 관리자 권한 회수 메소드
    @Transactional
    public void revokeAdminRole(Long userid) {
        User receiveUser = checkAdminPrivilegeAndGetTargetUser(userid);
        // User 객체에게 권한 변경 위임
        receiveUser.changeAdmin(AdminYn.USER);
    }

//  16. 계정 정지
    @Transactional
    public void banUser(Long userId, Integer days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("해당 사용자를 찾을 수 없습니다."));

        user.ban(days); // User 엔티티 내 메서드 호출
        userRepository.save(user);
    }

//  17.계정 정지 해제
    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("해당 사용자를 찾을 수 없습니다."));

        user.unban();
        userRepository.save(user);
    }

//    파일 경로에서 파일명만 추출하는 메서드
    private String extractFileName(String fileUrl){
        return fileUrl.substring(fileUrl.lastIndexOf("/")+1);
    }

}
