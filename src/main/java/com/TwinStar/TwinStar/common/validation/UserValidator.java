package com.TwinStar.TwinStar.common.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 사용자 관련 검증 로직 통합 클래스
 * - 이메일 형식 검증
 * - 비밀번호 정책 검증
 */
@Slf4j
@Component
public class UserValidator {

    // 이메일 정규표현식 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    // 비밀번호 정규표현식 패턴 (최소 8자, 숫자와 문자 포함)
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+]{8,}$"
    );

    /**
     * 이메일 형식 검증
     * @param email 검증할 이메일
     * @return 유효성 여부
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("[UserValidator] 이메일이 비어있습니다.");
            return false;
        }
        boolean valid = EMAIL_PATTERN.matcher(email).matches();
        if (!valid) {
            log.warn("[UserValidator] 잘못된 이메일 형식: {}", email);
        }
        return valid;
    }

    /**
     * 비밀번호 정책 검증
     * - 최소 8자 이상
     * - 숫자 포함
     * - 문자 포함
     * @param password 검증할 비밀번호
     * @return 유효성 여부
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.isBlank()) {
            log.warn("[UserValidator] 비밀번호가 비어있습니다.");
            return false;
        }

        if (password.length() < 8) {
            log.warn("[UserValidator] 비밀번호가 8자 미만입니다.");
            return false;
        }

        boolean valid = PASSWORD_PATTERN.matcher(password).matches();
        if (!valid) {
            log.warn("[UserValidator] 비밀번호가 정책을 충족하지 않습니다. (숫자와 문자를 포함해야 합니다)");
        }
        return valid;
    }

    /**
     * 비밀번호 정책 검증 (예외 발생 버전)
     * @param password 검증할 비밀번호
     * @throws IllegalArgumentException 비밀번호가 정책을 충족하지 않을 경우
     */
    public void validatePassword(String password) {
        if (!isValidPassword(password)) {
            throw new IllegalArgumentException("비밀번호는 최소 8자 이상이며, 숫자와 문자를 포함해야 합니다.");
        }
    }

    /**
     * 이메일 형식 검증 (예외 발생 버전)
     * @param email 검증할 이메일
     * @throws IllegalArgumentException 이메일 형식이 잘못된 경우
     */
    public void validateEmail(String email) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("올바른 이메일 형식이어야 합니다.");
        }
    }
}
