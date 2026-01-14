package com.TwinStar.TwinStar.chat.controller;

import com.TwinStar.TwinStar.chat.domain.ChatRoom;
import com.TwinStar.TwinStar.chat.dto.ChatMessageDto;
import com.TwinStar.TwinStar.chat.dto.ChatRoomCreateReqDto;
import com.TwinStar.TwinStar.chat.dto.ChatRoomResDto;
import com.TwinStar.TwinStar.chat.service.ChatService;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.ChatUserListDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

//    채팅방 개설
    @PostMapping("room/create")
    public ResponseEntity<?> RoomOpen(@RequestBody ChatRoomCreateReqDto chatRoomCreateReqDto){
        log.info("[ChatController] 채팅방 개설 요청");
        Long chatRoomId = chatService.RoomOpen(chatRoomCreateReqDto);
        log.info("[ChatController] 채팅방 개설 완료 - chatRoomId: {}", chatRoomId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "채팅방 개설 완료",chatRoomId),HttpStatus.OK);
    }

//    채팅방 리스트 보여주기
    @GetMapping("room/list")
    public ResponseEntity<?> getChatRoomList() {
        List<ChatRoomResDto> chatRoomList = chatService.chatRoomList();
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "채팅방 리스트 불러오기 완료", chatRoomList), HttpStatus.OK);
    }

//    채팅 메세지 리스트 page로 보여주기
    @GetMapping("room/detail/{roomId}")
    public ResponseEntity<?> getChatMessageList(@PathVariable("roomId") Long roomId,
                                                @RequestParam(name = "page", defaultValue = "0") Integer page,
                                                @RequestParam(name = "size", defaultValue = "20") Integer size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ChatMessageDto> chatMessageList = chatService.getChatMessages(roomId, pageRequest);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "채팅 불러오기 완료", chatMessageList),HttpStatus.OK);
    }

//    채팅 읽음 처리
    @PostMapping("read/{roomId}")
    public ResponseEntity<?> readChat(@PathVariable("roomId") Long roomId){
        chatService.readChat(roomId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "채팅 읽기 완료" ,roomId),HttpStatus.OK);
    }

//    채팅방 나가기
    @PostMapping("/room/leave/{roomId}")
    public ResponseEntity<?> leaveChatRoom(@PathVariable("roomId") Long roomId) {
        chatService.leaveChatRoom(roomId);
        return ResponseEntity.ok(new CommonDto(HttpStatus.OK.value(), "채팅방 나가기 완료", roomId));
    }

//    채팅방 초대하기
    @PostMapping("/room/invite/{roomId}")
    public ResponseEntity<?> inviteUsersToChatRoom(@PathVariable("roomId") Long roomId, @RequestBody List<Long> userIds) {
        chatService.inviteUsersToChatRoom(roomId, userIds);
        return ResponseEntity.ok(new CommonDto(HttpStatus.OK.value(), "유저 "+userIds.toString() + roomId.toString()+"번방 초대 완료", roomId));
    }

//    현재 채팅방 유저 리스트 확인
    @GetMapping("/room/users/{roomId}")
    public ResponseEntity<?> checkParticipatingUsers(@PathVariable("roomId") Long roomId) {
        List<ChatUserListDto> checkParticipatingUserList = chatService.checkParticipatingUsers(roomId);
        return ResponseEntity.ok(new CommonDto(HttpStatus.OK.value(), "유저 리스트 확인", checkParticipatingUserList));
    }

//    방 제목 변경
    @PostMapping("room/name/{roomId}")
    public ResponseEntity<?> changeRoomName(@PathVariable("roomId") Long roomId, @RequestBody Map<String, String> request){
        String name = request.get("name");
        chatService.changeRoomName(roomId, name);
        return ResponseEntity.ok(new CommonDto(HttpStatus.OK.value(), "방 제목 변경완료", roomId));
    }

}
