package com.TwinStar.TwinStar.follow.controller;

import com.TwinStar.TwinStar.common.auth.JwtUtil;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.follow.dto.FollowDto;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.follow.service.FollowService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import java.util.List;

@RequestMapping("/follow")
@RestController
public class FollowController {
    public final FollowService followService;
    public final JwtUtil jwtUtil;

    public FollowController(FollowService followService, JwtUtil jwtUtil) {
        this.followService = followService;
        this.jwtUtil = jwtUtil;
    }

//    토글 팔로우/언팔로우 요청
    @PostMapping("/toggle/{receiveUserId}")
    public ResponseEntity<?> toggleFollow(@PathVariable Long receiveUserId) {

        boolean isFollowing = followService.toggleFollow(receiveUserId);

        if (isFollowing) {
            return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "팔로우 되었습니다.",isFollowing),HttpStatus.OK);
        } else {
            return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "언팔로우 되었습니다.",isFollowing),HttpStatus.OK);
        }
    }

    // 특정 유저의 팔로워 수 조회 (공개 API)
    @GetMapping("/count/follower/{userId}")
    public ResponseEntity<?> countFollowersByUserId(@PathVariable Long userId) {
        Long count = followService.countByReceiveUserIdAndFollowYn(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), count+ "명",count),HttpStatus.OK);
    }

    // 특정 유저의 팔로잉 수 조회 (공개 API)
    @GetMapping("/count/following/{userId}")
    public ResponseEntity<?> countFollowingByUserId(@PathVariable Long userId) {
        Long count = followService.countByUserIdAndFollowYn(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), count+ "명",count),HttpStatus.OK);
    }

//    팔로잉 목록 조회
    @GetMapping("/following/{userId}")
    public ResponseEntity<?> getFollowerList(@PathVariable Long userId, Pageable pageable) {
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "팔로워 목록 조회입니다",followService.getFollowerList(userId,pageable)),HttpStatus.OK);
    }

//        팔로워 목록 조회
    @GetMapping("/follower/{userId}")
    public ResponseEntity<?> getFollowingList(@PathVariable Long userId, Pageable pageable) {
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "팔로잉 목록 조회입니다",followService.getFollowingList(userId,pageable)),HttpStatus.OK);
    }

    @GetMapping("check/{userId}")
    public ResponseEntity<?> getIsFollow(@PathVariable Long userId){
        Boolean isFollow = followService.isFollow(userId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "팔로우 확인",isFollow),HttpStatus.OK);
    }
}
