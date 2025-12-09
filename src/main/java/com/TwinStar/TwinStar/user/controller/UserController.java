package com.TwinStar.TwinStar.user.controller;


import com.TwinStar.TwinStar.common.auth.JwtTokenProvider;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.common.exception.MissingRequestParameterException;
import com.TwinStar.TwinStar.post.dto.ProfilePostResDto;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.*;
import com.TwinStar.TwinStar.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    @Qualifier("rtdb")
    private final RedisTemplate<String,Object> redisTemplate;
    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;
    private final ObjectMapper objectMapper;


    public UserController(UserService userService, JwtTokenProvider jwtTokenProvider, @Qualifier("rtdb") RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
//  1.로그인
    @PostMapping("/doLogin")
    public ResponseEntity<LoginResponseDto> doLogin(@RequestBody LoginDto dto) {
//        id,email, password 검증
        User user = userService.login(dto);
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
        userService.logout();
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Logged out successfully", null), HttpStatus.OK);
    }

//  2.회원가입
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody UserSaveReq dto) {
        Long memberId = userService.create(dto);
        return new ResponseEntity<>(memberId, HttpStatus.CREATED);
    }

    // 이메일 중복 체크
    @GetMapping("/check-email/{email}")
    public ResponseEntity<?> checkEmailDuplicate(@PathVariable String email) {
        boolean isDuplicate = userService.existsByEmail(email);
        Map<String, Boolean> response = new HashMap<>(); //프론트에서 json으로 값을 주기 위해 Map사용 {"duplicate": true} 또는 {"duplicate": false} 형식
        response.put("duplicate", isDuplicate);
        return ResponseEntity.ok(response);
    }

    // 닉네임 중복 체크
    @GetMapping("/check-nickname/{nickname}")
    public ResponseEntity<?> checkNicknameDuplicate(@PathVariable String nickname) {
        boolean isDuplicate = userService.existsByNickName(nickname);
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

        userService.changePassword(id, request);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"Password changed successfully",null),HttpStatus.OK);

    }

//  5. 사용자 상태 변경
    @PatchMapping("/status")
    public ResponseEntity<?> changeStatus(@RequestBody ChangeIdVisibility newStatus){
//        요청 본문이 null이거나 idVisibility가 null이면 예외 발생 방지
        if (newStatus == null || newStatus.getIdVisibility() == null){
            throw new MissingRequestParameterException("idVisibility 값이 필요합니다.");
        }


        userService.changeIdVisibility(newStatus.getIdVisibility());
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "user Visibility updeated to" + newStatus,newStatus),HttpStatus.OK);
    }

//  6. 프로필 들어가면 정보를 얻는다.
    @GetMapping("/detail/{receiveUserId}")
    public ResponseEntity<?> userDetail(@PathVariable Long receiveUserId){
        UserProfileDto dto = userService.searchProfile(receiveUserId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "memberDetailLest is found",dto),HttpStatus.OK);

    }

//  8.사용자 프로필 이미지 수정
    @PostMapping("/profile/img")
    public ResponseEntity<?> updateImgProfile(@RequestParam("file") MultipartFile file) throws IOException {
        String imageUrl = userService.updateProfileImage(file);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully.",imageUrl),HttpStatus.OK);
    }

//  8.2 사용자 프로필 텍스트 수정
    @PostMapping("/profile/text")
    public ResponseEntity<?> updateTextProfile(@RequestBody ProfileTextUpdateDto dto){
        userService.updateProfileText(dto);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully",null),HttpStatus.OK);
    }

//    일반유저용 유저목록 조회
    @GetMapping("/list")
    public ResponseEntity<?> ChatUserList(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        Page<ChatUserListDto> chatUserListDtos = userService.chatUserList(pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"user is found",chatUserListDtos),HttpStatus.OK);
    }

//    채팅 유저 검색
    @GetMapping("/chat/search")
    public ResponseEntity<?> chatSearch(@PageableDefault(size = 10) Pageable pageable,@RequestParam(required = false) String nickName){
        Page<ChatUserListDto> users = userService.searchChatUsers(nickName,pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"is good",users),HttpStatus.OK);
    }

//  9. 관리자용 유저목록 조회
    @GetMapping("/admin/user/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> list(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable, UserSearchDto dto){
        Page<UserListDto> userListDto = userService.userList(pageable, dto);
        return new ResponseEntity<>(userListDto,HttpStatus.OK);
    }

//    관리자용 유저목록 검색
    @GetMapping("/admin/list/search")
    public ResponseEntity<?> adminListSearch(@PageableDefault(size = 10) Pageable pageable,@RequestParam(required = false) String nickName){
        Page<UserListDto> users = userService.searchListUsers(nickName,pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"is good",users),HttpStatus.OK);
    }

//    관리자용 유저상세목록 조회
    @GetMapping("admin/detail/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> detailList(@PathVariable Long userId){
        UserDetailDto userDetailDto = userService.userDetailList(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "userDetailList is found",userDetailDto),HttpStatus.OK);
    }

//   10. 관리자 권한 부여
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/grant")
    public ResponseEntity<?> grantAdmin(@RequestBody GrantAdminId grant) { //보안 및 json으로 받기 위해 @RequestBody 씀
        userService.grantAdminRole(grant.getId()); //유저 id로 권한 부여 서비스 메서드 호출
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "관리자 권한이 부여되었습니다.",grant),HttpStatus.OK);
    }

//  11.관리자 권한 회수
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/revoke")
    public ResponseEntity<?> revokeAdmin(@RequestBody GrantAdminId revoke) {
        userService.revokeAdminRole(revoke.getId());
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "관리자 권한이 해제되었습니다.",revoke),HttpStatus.OK);
    }

//  12. JWT 기반 회원 탈퇴 API
    @DeleteMapping("/del")
    public ResponseEntity<?> deleteUser() {
        userService.deleteUser();
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "Profile updated successfully.","null"),HttpStatus.OK);
    }

//    13.  계정 정지 (관리자 전용)
    @PostMapping("/admin/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')") // 관리자만 접근 가능
    public ResponseEntity<?> suspendUser(@PathVariable Long userId, @RequestParam(required = false) Integer days) {
        userService.banUser(userId, days);
        String message = (days == null) ? "사용자 계정이 무기한 정지되었습니다." : "사용자 계정이 " + days + "일 동안 정지되었습니다.";
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),message,null),HttpStatus.OK);
    }

//    14. 계정 정지 해제 (관리자 전용)
    @PostMapping("/admin/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> unsuspendUser(@PathVariable Long userId) {
        userService.unbanUser(userId);
        return ResponseEntity.ok("사용자 계정 정지가 해제되었습니다.");
    }


}
