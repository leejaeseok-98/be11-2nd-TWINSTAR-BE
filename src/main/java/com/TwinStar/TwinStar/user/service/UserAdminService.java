package com.TwinStar.TwinStar.user.service;

import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.user.domain.AdminYn;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 전용 서비스
 * - 관리자 권한 부여/회수
 * - 계정 정지/해제
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;

    /**
     * 관리자 권한 부여
     * @param userid 대상 사용자 ID
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     * @throws IllegalStateException 삭제된 계정인 경우
     */
    @Transactional
    public void grantAdminRole(Long userid) {
        log.info("[UserAdminService] 관리자 권한 부여 시작 - targetUserId: {}", userid);

        User receiveUser = checkAdminPrivilegeAndGetTargetUser(userid);
        receiveUser.changeAdmin(AdminYn.ADMIN);

        log.info("[UserAdminService] 관리자 권한 부여 완료 - targetUserId: {}", userid);
    }

    /**
     * 관리자 권한 회수
     * @param userid 대상 사용자 ID
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     * @throws IllegalStateException 삭제된 계정인 경우
     */
    @Transactional
    public void revokeAdminRole(Long userid) {
        log.info("[UserAdminService] 관리자 권한 회수 시작 - targetUserId: {}", userid);

        User receiveUser = checkAdminPrivilegeAndGetTargetUser(userid);
        receiveUser.changeAdmin(AdminYn.USER);

        log.info("[UserAdminService] 관리자 권한 회수 완료 - targetUserId: {}", userid);
    }

    /**
     * 계정 정지
     * @param userId 대상 사용자 ID
     * @param days 정지 기간 (일 단위, null이면 무기한)
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public void banUser(Long userId, Integer days) {
        log.info("[UserAdminService] 계정 정지 시작 - targetUserId: {}, days: {}", userId, days);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("해당 사용자를 찾을 수 없습니다."));

        user.ban(days);
        userRepository.save(user);

        log.info("[UserAdminService] 계정 정지 완료 - targetUserId: {}, days: {}", userId, days);
    }

    /**
     * 계정 정지 해제
     * @param userId 대상 사용자 ID
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public void unbanUser(Long userId) {
        log.info("[UserAdminService] 계정 정지 해제 시작 - targetUserId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("해당 사용자를 찾을 수 없습니다."));

        user.unban();
        userRepository.save(user);

        log.info("[UserAdminService] 계정 정지 해제 완료 - targetUserId: {}", userId);
    }

    /**
     * 관리자 권한 검증 및 대상 사용자 조회
     * - 현재 사용자가 관리자인지 확인
     * - 자기 자신에 대한 작업인지 확인
     * - 대상 사용자가 삭제되지 않았는지 확인
     * @param targetUserId 대상 사용자 ID
     * @return 대상 사용자
     */
    private User checkAdminPrivilegeAndGetTargetUser(Long targetUserId) {
        User adminUser = getCurrentUser();

        // 관리자 권한 검증
        adminUser.validateAdminPrivilege();

        // 자기 자신에 대한 작업 방지
        adminUser.validateNotSelf(User.builder().id(targetUserId).build());

        // 대상 사용자 조회
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("대상 사용자를 찾을 수 없습니다."));

        // 삭제된 계정 확인
        if (targetUser.getDelYn() == YN.Y) {
            throw new IllegalStateException("삭제된 계정의 권한을 변경할 수 없습니다.");
        }

        return targetUser;
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
