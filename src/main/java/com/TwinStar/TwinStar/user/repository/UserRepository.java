package com.TwinStar.TwinStar.user.repository;


import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.domain.UserStatus;
import com.TwinStar.TwinStar.user.dto.ChatUserListDto;
import com.TwinStar.TwinStar.user.dto.UserListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByEmail(String email);

//    회원id로 게시물 찾는 쿼리
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.posts WHERE u.id = :userId")
    Optional<User> findByIdWithPosts(@Param("userId") Long userId);

    //닉네임 중복 체크 메서드 jpa네이밍 규칙 포함
    boolean existsByNickName(String nickName);

//    회원id로 del상태 조회
    Optional<User> findByIdAndDelYn(Long id, YN delYn);

//    정지된 사용자 조회 메서드
    @Query("SELECT u FROM User u WHERE u.userStatus = :status AND (u.banCloseTime IS NULL OR u.banCloseTime > CURRENT_TIMESTAMP)")
    List<User> findBannedUsers(@Param("status") UserStatus status);

    Optional<User> findByNickName(String nickName);

//    닉네임으로 검색 조회
    @Query("""
    SELECT new com.TwinStar.TwinStar.user.dto.ChatUserListDto(u.id, u.nickName, u.profileImg) 
    FROM User u 
    WHERE LOWER(u.nickName) LIKE LOWER(CONCAT('%', :nickname, '%'))
""")
    Page<ChatUserListDto> searchUsersByNickname(@Param("nickname") String nickname, Pageable pageable);

    Page<User> findAll(Specification<User> spec, Pageable pageable);

}
