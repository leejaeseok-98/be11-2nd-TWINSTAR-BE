package com.TwinStar.TwinStar.alarm.service;

import com.TwinStar.TwinStar.alarm.domain.Alarm;
import com.TwinStar.TwinStar.alarm.dto.AlarmCommonResDto;
import com.TwinStar.TwinStar.alarm.dto.AlarmResDto;
import com.TwinStar.TwinStar.alarm.repository.AlarmRepository;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional
public class AlarmService {
    private final AlarmRepository alarmRepository;
    private final UserRepository userRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public AlarmService(AlarmRepository alarmRepository, UserRepository userRepository) {
        this.alarmRepository = alarmRepository;
        this.userRepository = userRepository;
    }

    public void createAlarm(User receiver, String content, String url){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User sender = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()-> new EntityNotFoundException("user is not found."));
        if (sender.equals(receiver)){return ;}

        log.info("[AlarmService] 알림 생성 - receiverId: {}, senderId: {}, content: {}", receiver.getId(), sender.getId(), content);
        Alarm alarm = Alarm.builder()
                .user(receiver)
                .sender(sender)
                .url(url)
                .content(content)
                .build();
        alarmRepository.save(alarm);

        AlarmCommonResDto alarmCommonResDto = AlarmCommonResDto.builder()
                .url(url)
                .content(content)
                .build();
        sendNotification(receiver.getId(), alarmCommonResDto);
    }

    public void sendNotification(Long userId, AlarmCommonResDto dto) {
        SseEmitter emitter = emitters.get(String.valueOf(userId));
        if (emitter != null) {
            try {
                log.debug("[AlarmService] SSE 알림 전송 - userId: {}", userId);
                emitter.send(SseEmitter.event().name("alarm").data(dto));
            } catch (IOException e) {
                log.error("[AlarmService] SSE 알림 전송 실패 - userId: {}", userId, e);
                emitters.remove(String.valueOf(userId)); // 전송 중 오류 발생하면 제거
            }
        }
    }

    public SseEmitter subscribe(Long userId) {
        log.info("[AlarmService] SSE 구독 시작 - userId: {}", userId);
        SseEmitter emitter = new SseEmitter(60 * 1000L); // 30분
        emitters.put(String.valueOf(userId), emitter);

        try {
            emitter.send(SseEmitter.event().name("connect").data("연결 성공"));
            log.info("[AlarmService] SSE 구독 성공 - userId: {}", userId);
        } catch (IOException e) {
            log.error("[AlarmService] SSE 구독 실패 - userId: {}", userId, e);
            emitters.remove(String.valueOf(userId));
        }
        return emitter;
    }

    public void unsubscribe(Long userId) {
        emitters.remove(String.valueOf(userId));
    }

//    알림 리스트 불러오기
    public Page<AlarmResDto> getAlarms(PageRequest pageRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()-> new EntityNotFoundException("user is not found."));

        Page<Alarm> alarmPage = alarmRepository.findByUserOrderByCreatedTimeDesc(user, pageRequest);

        return alarmPage.map(alarm -> AlarmResDto.builder()
                .id(alarm.getId())
                .senderId(alarm.getSender().getId())
                .profileImage(alarm.getSender().getProfileImg())
                .content(alarm.getContent())
                .url(alarm.getUrl())
                .createdTime(alarm.getCreatedTime())
                .build());
    }

}
