package com.TwinStar.TwinStar.follow.repository;


import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.follow.domain.Follow;
import com.TwinStar.TwinStar.user.domain.User;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    //    특정 Follower와 Following 간의 관계를 조회하는 메서드 *toggleFollow에서 사용
    Optional<Follow> findByUserAndReceiveUser(User user, User receiveUser);
    //
    boolean existsByUserAndReceiveUser(User user, User receiveUser);
    //     팔로워 수
    Long countByReceiveUserAndFollowYn(User receiveUser, YN followYn);

    // 팔로잉 수 조회 (내가 팔로우한 사람)
    Long countByUserAndFollowYn(User user, YN followYn);

    // 나를 팔로우한 유저 목록 (페이징 적용)
    Page<Follow> findByReceiveUserAndFollowYn(User receiveUser, YN followYn, Pageable pageable);

    // 내가 팔로우한 유저 목록 (페이징 적용)
    Page<Follow> findByUserAndFollowYn(User receiveUser, YN followYn, Pageable pageable);
//    //     나를 팔로우한 목록
//    List<Follow> findByUserIdAndFollowYn(User userId, YN followYn);
//
//    //         내가 팔로우한 목록
//    List<Follow> findByReceiveUserIdAndFollowYn(User receiveUserId, YN followYn);

    // 내가 팔로우한 유저 ID 조회
    @Query("SELECT f.receiveUser.id FROM Follow f WHERE f.user.id = :userId AND f.followYn = 'Y'")
    List<Long> findFollowingUserIds(@Param("userId") Long userId);

    // 특정 유저 목록 중 내가 팔로우한 유저 ID 조회 (N+1 해결용)
    @Query("SELECT f.receiveUser.id FROM Follow f WHERE f.user.id = :userId AND f.receiveUser.id IN :targetUserIds AND f.followYn = 'Y'")
    Set<Long> findFollowingUserIdsIn(@Param("userId") Long userId, @Param("targetUserIds") List<Long> targetUserIds);

//    // 나와 맞팔로우 관계인 유저 ID 조회
//    @Query("""
//        SELECT f.receiveUser.id FROM Follow f
//        WHERE f.user.id = :user AND f.followYn = 'Y'
//        AND f.receiveUser.id IN (
//            SELECT f2.user.id FROM Follow f2
//            WHERE f2.receiveUser.id = :userId AND f2.followYn = 'Y'
//        )
//    """)
//    List<Long> findMutualFollowUserIds(@Param("userId") Long userId);

    Boolean existsByUserAndReceiveUserAndFollowYn(User user, User receiveUser, YN followYn);

}
