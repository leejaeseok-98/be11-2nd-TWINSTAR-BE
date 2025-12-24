package com.TwinStar.TwinStar.comment.service;

import com.TwinStar.TwinStar.alarm.repository.AlarmRepository;
import com.TwinStar.TwinStar.alarm.service.AlarmService;
import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.comment.dto.CommentLikeResDto;
import com.TwinStar.TwinStar.comment.repository.CommentLikeRepository;
import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.follow.repository.FollowRepository;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.UserListResDto;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.concurrent.TimeUnit;

import static com.TwinStar.TwinStar.common.config.RabbitMQConfig.BACKUP_QUEUE_COMMENT_AL;
import static com.TwinStar.TwinStar.common.config.RabbitMQConfig.BACKUP_QUEUE_COMMENT_ML;

@Service
public class CommentLikeService {

    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AlarmService alarmService;
    private final AlarmRepository alarmRepository;
    private final FollowRepository followRepository;

    @Qualifier("commentLikeRedisTemple")
    private final RedisTemplate<String, Object> commentLikeRedisTemplate;

    public CommentLikeService(CommentLikeRepository commentLikeRepository, CommentRepository commentRepository, UserRepository userRepository, RabbitTemplate rabbitTemplate, AlarmService alarmService, AlarmRepository alarmRepository, FollowRepository followRepository, @Qualifier("commentLikeRedisTemple")RedisTemplate<String, Object> commentLikeRedisTemplate) {
        this.commentLikeRepository = commentLikeRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.alarmService = alarmService;
        this.alarmRepository = alarmRepository;
        this.followRepository = followRepository;
        this.commentLikeRedisTemplate = commentLikeRedisTemplate;
    }


    @Transactional
    public CommentLikeResDto commentLikeToggle(Long commentId) {
        String redisKey = "comment:like:" + commentId;

        Object cachedValue = commentLikeRedisTemplate.opsForValue().get(redisKey);
        Long likeCount = cachedValue != null ? ((Number) cachedValue).longValue() : null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName()))
                .orElseThrow(() -> new EntityNotFoundException("user is not found."));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 댓글이 존재하지 않습니다."));

        if (likeCount == null) {
            likeCount = commentLikeRepository.countByComment(comment);
            commentLikeRedisTemplate.opsForValue().set(redisKey, likeCount, 10, TimeUnit.MINUTES);
        }

        // Comment 엔티티에게 좋아요 토글 위임
        boolean isLiked = comment.toggleLike(user);

        if (isLiked) {
            // 좋아요 추가됨
            rabbitTemplate.convertAndSend(BACKUP_QUEUE_COMMENT_AL, commentId);
            likeCount++;

            User receiver = comment.getUser();
            String content = receiver.getNickName() + "님이 회원님의 댓글을 좋아합니다.";
            String url = "https://www.alexandrelax.store/post/detail/" + comment.getPost().getId();
            if(!alarmRepository.existsByUrlAndContent(url,content)){
                alarmService.createAlarm(receiver, content, url);
            }
        } else {
            // 좋아요 취소됨
            rabbitTemplate.convertAndSend(BACKUP_QUEUE_COMMENT_ML, commentId);
            likeCount--;
        }

        commentLikeRedisTemplate.opsForValue().set(redisKey, likeCount, 10, TimeUnit.MINUTES);

        return new CommentLikeResDto(likeCount, isLiked);
    }

    @Transactional(readOnly = true)
    public Page<UserListResDto> getLikeList(Long commentId, Pageable pageable) {
        // 현재 로그인한 사용자 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName()))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // 해당 댓글을 좋아요 한 유저 목록 조회 (페이징)
        Page<User> likedUsers = commentLikeRepository.findUsersWhoLikedComment(commentId, pageable);

        // 각 유저와 로그인한 유저 간의 팔로우 여부 확인
        return likedUsers.map(user -> {
            String isFollow = followRepository.existsByUserAndReceiveUserAndFollowYn(loginUser,user, YN.Y)||user.equals(loginUser) ? "Y" : "N";

            return new UserListResDto().toUserListResDto(user, isFollow);
        });
    }

}
