package com.TwinStar.TwinStar.follow.service;


import com.TwinStar.TwinStar.alarm.repository.AlarmRepository;
import com.TwinStar.TwinStar.alarm.service.AlarmService;
import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.follow.domain.Follow;
import com.TwinStar.TwinStar.follow.dto.FollowDto;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.follow.repository.FollowRepository;
import com.TwinStar.TwinStar.user.dto.UserListResDto;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class FollowService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final AlarmService alarmService;
    private final AlarmRepository alarmRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository, AlarmService alarmService, AlarmRepository alarmRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.alarmService = alarmService;
        this.alarmRepository = alarmRepository;
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

    //    토글 팔로우/언팔로우 요청
    @Transactional
    public boolean toggleFollow(Long receiveUserId) {
        log.info("[FollowService] toggleFollow started - receiveUserId: {}", receiveUserId);

        User followRequest = getCurrentUser();
        log.debug("[FollowService] Current user: {} (ID: {})", followRequest.getNickName(), followRequest.getId());

        User receiveFollowRequest = userRepository.findById(receiveUserId)
                .orElseThrow(() -> {
                    log.error("[FollowService] Target user not found - receiveUserId: {}", receiveUserId);
                    return new IllegalArgumentException("팔로잉 대상이 존재하지 않습니다.");
                });

        // 팔로우 상태 확인
        Optional<Follow> existingFollow = followRepository.findByUserAndReceiveUser(followRequest,receiveFollowRequest);

        String content= followRequest.getNickName()+"님이 회원님을 팔로우합니다.";
//        String url = "https://www.alexandrelax.store/profile/"+followRequest.getId();
        String url = "http://localhost:8080/profile/"+followRequest.getId();
        if(!alarmRepository.existsByUrlAndContent(url,content)){
            alarmService.createAlarm(receiveFollowRequest,content,url);
            log.debug("[FollowService] Alarm created for user: {}", receiveFollowRequest.getNickName());
        }

        boolean result;
        if (existingFollow.isPresent()) {
            Follow follow = existingFollow.get();
            follow.toggleFollow();  // followYn 값 변경 (Y <-> N)
            followRepository.save(follow);
            result = follow.getFollowYn() == YN.Y;
            log.info("[FollowService] Follow status toggled - userId: {}, receiveUserId: {}, isFollowing: {}",
                    followRequest.getId(), receiveUserId, result);
        } else {
            // 팔로우하고 있지 않다면 새로 팔로우
            Follow follow = new Follow(followRequest, receiveFollowRequest);
            followRepository.save(follow);
            result = true;
            log.info("[FollowService] New follow created - userId: {}, receiveUserId: {}",
                    followRequest.getId(), receiveUserId);
        }

        return result;
    }

    // 팔로워 수 조회
    public Long countByReceiveUserIdAndFollowYn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        return Optional.ofNullable(followRepository.countByReceiveUserAndFollowYn(user, YN.Y))
                .orElse(0L); //nullpointexception방지 위한 0값 설정
    }

    // 팔로잉 수 조회
    public Long countByUserIdAndFollowYn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
//        return Optional.ofNullable(followRepository.countByUserIdAndFollowYn(user))
//                .orElse(0L);
        return Optional.ofNullable(followRepository.countByUserAndFollowYn(user, YN.Y))
                .orElse(0L);
    }

    // (팔로워 목록)
    public Page<FollowDto> getFollowerList(Long userId, Pageable pageable) {
        User loginUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        Page<User> followUserList = followRepository.findByReceiveUserAndFollowYn(loginUser,YN.Y,pageable)
                .map(follow -> follow.getUser());

        // 각 유저와 로그인한 유저 간의 팔로우 여부 확인
        return followUserList.map(user -> {
            String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,user, YN.Y)||user.equals(loginUser) ? "Y" : "N";

            return new FollowDto(user,isFollow);
        });
    }

    // (팔로잉 목록)
    public Page<FollowDto> getFollowingList(Long userId,Pageable pageable) {
        User loginUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        // 내가 팔로우한 유저 목록을 가져옴
        Page<User> followingUsers = followRepository.findByUserAndFollowYn(loginUser, YN.Y, pageable)
                .map(Follow::getReceiveUser); // 내가 팔로우한 대상 유저 반환

        // 각 유저와 로그인한 유저 간의 팔로우 여부 확인 후 DTO 변환
        return followingUsers.map(user -> {
            String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser, user, YN.Y) || user.equals(loginUser) ? "Y" : "N";
            return new FollowDto(user, isFollow);
        });
    }

    public Boolean isFollow(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(() -> new EntityNotFoundException("User not found"));
        User targetUser = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        return followRepository.existsByUserAndReceiveUserAndFollowYn(user, targetUser, YN.Y);
    }
}
