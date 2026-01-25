package com.TwinStar.TwinStar.chat.repository;

import com.TwinStar.TwinStar.chat.domain.ChatParticipant;
import com.TwinStar.TwinStar.chat.domain.ChatRoom;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.ChatUserListDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

//    roomId로 참여 user 찾기 리스트로
    List<ChatParticipant> findAllByChatRoomId(Long chatRoomId);

//    우ㅠ저가 그 채팅방에 있느지 확인
    @Query("SELECT cp FROM ChatParticipant cp WHERE cp.chatRoom.id = :chatRoomId AND cp.user.id = :userId")
    Optional<ChatParticipant> findByChatRoomIdAndUserId(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

//    채팅방에 있는 유저 리스트 확인
    @Query("SELECT new com.TwinStar.TwinStar.user.dto.ChatUserListDto( " +
            "u.id, u.nickName, u.profileImg) " +
            "FROM ChatParticipant cp " +
            "JOIN cp.user u " +
            "WHERE cp.chatRoom = :chatRoom AND cp.isActive = true")
    List<ChatUserListDto> findActiveUsersByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

//    여러 채팅방의 참여자를 User 정보와 함께 일괄 조회 (N+1 문제 해결)
    @Query("SELECT cp FROM ChatParticipant cp " +
            "JOIN FETCH cp.user " +
            "JOIN FETCH cp.chatRoom " +
            "WHERE cp.chatRoom.id IN :chatRoomIds")
    List<ChatParticipant> findAllByChatRoomIdsWithUser(@Param("chatRoomIds") List<Long> chatRoomIds);
}
