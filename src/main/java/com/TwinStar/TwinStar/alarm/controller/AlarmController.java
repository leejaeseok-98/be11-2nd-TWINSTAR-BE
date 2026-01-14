package com.TwinStar.TwinStar.alarm.controller;


import com.TwinStar.TwinStar.alarm.dto.AlarmCommonResDto;
import com.TwinStar.TwinStar.alarm.dto.AlarmResDto;
import com.TwinStar.TwinStar.alarm.service.AlarmService;
import com.TwinStar.TwinStar.chat.dto.ChatMessageDto;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/alarm")
public class AlarmController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> alarmList(@RequestParam(name = "page", defaultValue = "0") Integer page,
                                       @RequestParam(name = "size", defaultValue = "20") Integer size){
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AlarmResDto> AlarmResDtoPage = alarmService.getAlarms(pageRequest);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "알림 리스트 불러오기 완료", AlarmResDtoPage),HttpStatus.OK);
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.valueOf(authentication.getName());
        log.info("[AlarmController] SSE 구독 요청 - userId: {}", userId);
        return alarmService.subscribe(userId);
    }

    @GetMapping("/unsubscribe")
    public void unSubscribe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.valueOf(authentication.getName());
        alarmService.unsubscribe(userId);
    }

}
