package com.TwinStar.TwinStar.user.service;

import com.TwinStar.TwinStar.common.exception.LoginFailedException;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.LoginDto;
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
 * 사용자 인증 서비스
 * - 로그인
 * - 로그아웃
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 로그인
     * @param dto 로그인 정보 (이메일, 비밀번호)
     * @return 인증된 사용자
     * @throws LoginFailedException 로그인 실패 시
     */
    public User login(LoginDto dto) {
        log.info("[UserAuthService] 로그인 시도 - email: {}", dto.getEmail());

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    log.warn("[UserAuthService] 로그인 실패 - 존재하지 않는 이메일: {}", dto.getEmail());
                    return new LoginFailedException("email 또는 비밀번호가 일치하지 않습니다.");
                });

        try {
            // User 엔티티에게 검증 위임
            user.validateLogin();
            user.validatePassword(dto.getPassword(), passwordEncoder);

            log.info("[UserAuthService] 로그인 성공 - userId: {}, email: {}", user.getId(), user.getEmail());
            return user;
        } catch (Exception e) {
            log.warn("[UserAuthService] 로그인 실패 - email: {}, 사유: {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void logout() {
        User user = getCurrentUser();
        log.info("[UserAuthService] 로그아웃 - userId: {}, email: {}", user.getId(), user.getEmail());
        redisTemplate.delete(user.getEmail());
    }

    /**
     * 현재 인증된 사용자 조회
     * @return 현재 사용자
     * @throws AuthenticationCredentialsNotFoundException 인증 정보가 없는 경우
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
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
