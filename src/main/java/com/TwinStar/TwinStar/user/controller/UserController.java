package com.TwinStar.TwinStar.user.controller;


import com.TwinStar.TwinStar.common.auth.JwtTokenProvider;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.common.exception.MissingRequestParameterException;
import com.TwinStar.TwinStar.post.dto.ProfilePostResDto;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.*;
import com.TwinStar.TwinStar.user.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserAuthService userAuthService;
    private final UserAccountService userAccountService;
    private final UserProfileService userProfileService;
    private final UserSearchService userSearchService;
    private final UserAdminService userAdminService;
    private final JwtTokenProvider jwtTokenProvider;
    @Qualifier("rtdb")
    private final RedisTemplate<String,Object> redisTemplate;
    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;
    private final ObjectMapper objectMapper;


    public UserController(UserAuthService userAuthService, UserAccountService userAccountService, UserProfileService userProfileService, UserSearchService userSearchService, UserAdminService userAdminService, JwtTokenProvider jwtTokenProvider, @Qualifier("rtdb") RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.userAuthService = userAuthService;
        this.userAccountService = userAccountService;
        this.userProfileService = userProfileService;
        this.userSearchService = userSearchService;
        this.userAdminService = userAdminService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
//  1.로그인
    @PostMapping("/doLogin")
    public ResponseEntity<LoginResponseDto> doLogin(@RequestBody LoginDto dto) {
        log.info("[UserController] 로그인 요청 - email: {}", dto.getEmail());
//        id,email, password 검증
        User user = userAuthService.login(dto);
        log.info("[UserController] 로그인 성공 - userId: {}", user.getId());
//        토큰 생성 및 return
        String token = jwtTokenProvider.createToken(user.getId(),user.getEmail(), user.getNickName(),user.getAdminYn().toString());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(),user.getEmail(),user.getAdminYn().toString());
//        redis에 rt저장
        redisTemplate.opsForValue().set(user.getEmail(),refreshToken,200, TimeUnit.DAYS);//200일 ttl
//        사용자에게 at,rt지급

        LoginResponseDto loginResponseDto = new LoginResponseDto(user.getId(), token, refreshToken);

        return new ResponseEntity<>(loginResponseDto, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        log.info("[UserController] 로그아웃 요청");
        userAuthService.logout();
        log.info("[UserController] 로그아웃 완료");
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Logged out successfully", null), HttpStatus.OK);
    }

//  2.회원가입
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody UserSaveReq dto) {
        log.info("[UserController] 회원가입 요청 - email: {}, nickname: {}", dto.getEmail(), dto.getNickName());
        Long memberId = userAccountService.create(dto);
        log.info("[UserController] 회원가입 완료 - userId: {}", memberId);
        return new ResponseEntity<>(memberId, HttpStatus.CREATED);
    }

    // 이메일 중복 체크
    @GetMapping("/check-email/{email}")
    public ResponseEntity<?> checkEmailDuplicate(@PathVariable String email) {
        boolean isDuplicate = userAccountService.existsByEmail(email);
        Map<String, Boolean> response = new HashMap<>(); //프론트에서 json으로 값을 주기 위해 Map사용 {"duplicate": true} 또는 {"duplicate": false} 형식
        response.put("duplicate", isDuplicate);
        return ResponseEntity.ok(response);
    }

    // 닉네임 중복 체크
    @GetMapping("/check-nickname/{nickname}")
    public ResponseEntity<?> checkNicknameDuplicate(@PathVariable String nickname) {
        boolean isDuplicate = userAccountService.existsByNickName(nickname);
        Map<String, Boolean> response = new HashMap<>();
        response.put("duplicate", isDuplicate);
        return ResponseEntity.ok(response);
    }




    //  리프레시 토큰을 이용한 액세스 토큰 재발급
    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshTokenResponseDto> refreshAccessToken(@RequestBody RefreshTokenRequestDto requestDto) {
        String newAccessToken = jwtTokenProvider.refreshAccessToken(requestDto.getRefreshToken());
        return ResponseEntity.ok(new RefreshTokenResponseDto(newAccessToken));
    }

//    4. 비밀번호 변경
    @PatchMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@PathVariable Long id, @RequestBody PasswordChangeRequest request){

        userAccountService.changePassword(id, request);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"Password changed successfully",null),HttpStatus.OK);

    }

//  5. 사용자 상태 변경
    @PatchMapping("/status")
    public ResponseEntity<?> changeStatus(@RequestBody ChangeIdVisibility newStatus){
//        요청 본문이 null이거나 idVisibility가 null이면 예외 발생 방지
        if (newStatus == null || newStatus.getIdVisibility() == null){
            throw new MissingRequestParameterException("idVisibility 값이 필요합니다.");
        }


        userProfileService.changeIdVisibility(newStatus.getIdVisibility());
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "user Visibility updeated to" + newStatus,newStatus),HttpStatus.OK);
    }

//  6. 프로필 들어가면 정보를 얻는다.
    @GetMapping("/{userId}")
    public ResponseEntity<?> userDetail(@PathVariable Long userId){
        UserProfileDto dto = userProfileService.searchProfile(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "memberDetailLest is found",dto),HttpStatus.OK);

    }

//  8.사용자 프로필 이미지 수정
    @PostMapping("/profile/img")
    public ResponseEntity<?> updateImgProfile(@RequestParam("file") MultipartFile file) throws IOException {
        String imageUrl = userProfileService.updateProfileImage(file);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully.",imageUrl),HttpStatus.OK);
    }

//  8.2 사용자 프로필 텍스트 수정
    @PostMapping("/profile/text")
    public ResponseEntity<?> updateTextProfile(@RequestBody ProfileTextUpdateDto dto){
        userProfileService.updateProfileText(dto);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully",null),HttpStatus.OK);
    }

//    일반유저용 유저목록 조회
    @GetMapping("/list")
    public ResponseEntity<?> ChatUserList(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        Page<ChatUserListDto> chatUserListDtos = userSearchService.chatUserList(pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"user is found",chatUserListDtos),HttpStatus.OK);
    }

//    채팅 유저 검색
    @GetMapping("/chat/search")
    public ResponseEntity<?> chatSearch(@PageableDefault(size = 10) Pageable pageable,@RequestParam(required = false) String nickName){
        Page<ChatUserListDto> users = userSearchService.searchChatUsers(nickName,pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"is good",users),HttpStatus.OK);
    }

//  9. 관리자용 유저목록 조회
    @GetMapping("/admin/user/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> list(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable, UserSearchDto dto){
        Page<UserListDto> userListDto = userSearchService.userList(pageable, dto);
        return new ResponseEntity<>(userListDto,HttpStatus.OK);
    }

//    관리자용 유저목록 검색
    @GetMapping("/admin/list/search")
    public ResponseEntity<?> adminListSearch(@PageableDefault(size = 10) Pageable pageable,@RequestParam(required = false) String nickName){
        Page<UserListDto> users = userSearchService.searchListUsers(nickName,pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"is good",users),HttpStatus.OK);
    }

//    관리자용 유저상세목록 조회
    @GetMapping("admin/detail/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> detailList(@PathVariable Long userId){
        UserDetailDto userDetailDto = userSearchService.userDetailList(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "userDetailList is found",userDetailDto),HttpStatus.OK);
    }

//   10. 관리자 권한 부여
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/grant")
    public ResponseEntity<?> grantAdmin(@RequestBody GrantAdminId grant) { //보안 및 json으로 받기 위해 @RequestBody 씀
        log.info("[UserController] 관리자 권한 부여 요청 - targetUserId: {}", grant.getId());
        userAdminService.grantAdminRole(grant.getId()); //유저 id로 권한 부여 서비스 메서드 호출
        log.info("[UserController] 관리자 권한 부여 완료 - targetUserId: {}", grant.getId());
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "관리자 권한이 부여되었습니다.",grant),HttpStatus.OK);
    }

//  11.관리자 권한 회수
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/revoke")
    public ResponseEntity<?> revokeAdmin(@RequestBody GrantAdminId revoke) {
        log.info("[UserController] 관리자 권한 회수 요청 - targetUserId: {}", revoke.getId());
        userAdminService.revokeAdminRole(revoke.getId());
        log.info("[UserController] 관리자 권한 회수 완료 - targetUserId: {}", revoke.getId());
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "관리자 권한이 해제되었습니다.",revoke),HttpStatus.OK);
    }

//  12. JWT 기반 회원 탈퇴 API
    @DeleteMapping("/del")
    public ResponseEntity<?> deleteUser() {
        userAccountService.deleteUser();
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully.","null"),HttpStatus.OK);
    }

//    13.  계정 정지 (관리자 전용)
    @PostMapping("/admin/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')") // 관리자만 접근 가능
    public ResponseEntity<?> suspendUser(@PathVariable Long userId, @RequestParam(required = false) Integer days) {
        log.info("[UserController] 계정 정지 요청 - targetUserId: {}, days: {}", userId, days);
        userAdminService.banUser(userId, days);
        log.info("[UserController] 계정 정지 완료 - targetUserId: {}, days: {}", userId, days);
        String message = (days == null) ? "사용자 계정이 무기한 정지되었습니다." : "사용자 계정이 " + days + "일 동안 정지되었습니다.";
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),message,null),HttpStatus.OK);
    }

//    14. 계정 정지 해제 (관리자 전용)
    @PostMapping("/admin/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> unsuspendUser(@PathVariable Long userId) {
        log.info("[UserController] 계정 정지 해제 요청 - targetUserId: {}", userId);
        userAdminService.unbanUser(userId);
        log.info("[UserController] 계정 정지 해제 완료 - targetUserId: {}", userId);
        return ResponseEntity.ok("사용자 계정 정지가 해제되었습니다.");
    }


}
