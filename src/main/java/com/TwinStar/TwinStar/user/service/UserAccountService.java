package com.TwinStar.TwinStar.user.service;

import com.TwinStar.TwinStar.common.exception.DuplicateEmailException;
import com.TwinStar.TwinStar.common.exception.DuplicateNicknameException;
import com.TwinStar.TwinStar.common.validation.UserValidator;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.PasswordChangeRequest;
import com.TwinStar.TwinStar.user.dto.UserSaveReq;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
// * 사용자 계정 관리 서비스
 * - 회원가입
 * - 회원탈퇴
 * - 비밀번호 변경
 * - 이메일/닉네임 중복 체크
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserValidator userValidator;

    /**
     * 회원가입
     * @param dto 회원가입 정보
     * @return 생성된 사용자 ID
     * @throws DuplicateEmailException 이메일 중복 시
     * @throws DuplicateNicknameException 닉네임 중복 시
     */
    @Transactional
    public Long create(UserSaveReq dto) {
        log.info("[UserAccountService] 회원가입 시도 - email: {}, nickname: {}", dto.getEmail(), dto.getNickName());

        // 이메일 중복 체크
        if (userRepository.existsByEmail(dto.getEmail())) {
            log.warn("[UserAccountService] 회원가입 실패 - 중복 이메일: {}", dto.getEmail());
            throw new DuplicateEmailException("중복 이메일입니다.");
        }

        // 닉네임 중복 체크
        if (userRepository.existsByNickName(dto.getNickName())) {
            log.warn("[UserAccountService] 회원가입 실패 - 중복 닉네임: {}", dto.getNickName());
            throw new DuplicateNicknameException("중복된 닉네임입니다");
        }

        User user = userRepository.save(dto.toEntity(passwordEncoder.encode(dto.getPassword())));
        log.info("[UserAccountService] 회원가입 완료 - userId: {}, email: {}", user.getId(), user.getEmail());
        return user.getId();
    }

    /**
     * 이메일 중복 체크 (비동기 검증용)
     * @param email 검증할 이메일
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 닉네임 중복 체크 (비동기 검증용)
     * @param nickname 검증할 닉네임
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    public boolean existsByNickName(String nickname) {
        return userRepository.existsByNickName(nickname);
    }

    /**
     * 회원탈퇴 (소프트 삭제)
     * - delYn을 'Y'로 변경
     * - Redis에서 Refresh Token 삭제
     */
    @Transactional
    public void deleteUser() {
        User user = getCurrentUser();

        // User 엔티티에게 탈퇴 처리 위임
        user.deleteUser();

        // Redis에서 Refresh Token 삭제
        redisTemplate.delete(user.getEmail());

        userRepository.save(user);
        log.info("[UserAccountService] 회원탈퇴 완료 - userId: {}, email: {}", user.getId(), user.getEmail());
    }

    /**
     * 비밀번호 변경
     * @param id 사용자 ID
     * @param request 비밀번호 변경 요청 (현재 비밀번호, 새 비밀번호)
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 비밀번호 정책 위반 시
     * @throws SecurityException 본인이 아닌 경우
     */
    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        User currentUser = getCurrentUser();

        // User 엔티티에게 검증 위임
        user.validateSelf(currentUser);
        user.validatePassword(request.getCurrentPassword(), passwordEncoder);

        // 새 비밀번호 정책 검증 (통합 Validator 사용)
        userValidator.validatePassword(request.getNewPassword());

        // 비밀번호 변경
        user.changePassword(request.getNewPassword(), passwordEncoder);
        log.info("[UserAccountService] 비밀번호 변경 완료 - userId: {}", user.getId());
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
