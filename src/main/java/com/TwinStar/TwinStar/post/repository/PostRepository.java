package com.TwinStar.TwinStar.post.repository;

import com.TwinStar.TwinStar.common.domain.Visibility;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.domain.PostLike;
import com.TwinStar.TwinStar.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post,Long> {
    List<Post> findByUserId(Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.postFile WHERE p.user.id = :userId")
    List<Post> findByUserIdWithFiles(@Param("userId") Long userId);

//    // 특정 유저가 볼 수 있는 게시물 조회 (전체 공개 또는 맞팔 유저의 팔로우 공개 게시물)
//    @Query("""
//        SELECT p FROM Post p
//        WHERE p.postDel = 'N'
//        AND (p.visibility = :allVisibility OR p.user.id IN (:userIds))
//        ORDER BY p.createdTime DESC
//        """)
//    Page<Post> findVisiblePostsForUser(@Param("allVisibility") Visibility allVisibility,
//                                       @Param("userIds") List<Long> userIds,
//                                       Pageable pageable);

    @Query("""
    SELECT p FROM Post p
    WHERE
        p.user.id = :currentUserId

        OR p.user.idVisibility = 'ALL'

        OR (p.user.idVisibility = 'FOLLOW' AND p.user.id IN (
                        SELECT f.receiveUser.id FROM Follow f
                        WHERE f.user.id = :currentUserId AND f.followYn = 'Y'
                        AND f.receiveUser.id IN (
                            SELECT f2.user.id FROM Follow f2 WHERE f2.receiveUser.id = :currentUserId AND f2.followYn = 'Y'
                        )
                    ))
""")
    Page<Post> findFeedPostsForUser(@Param("currentUserId") Long currentUserId, Pageable pageable);


    @Query("SELECT COUNT(pl) FROM PostLike pl WHERE pl.post.id = :postId")
    Long countPostLikes(@Param("postId") Long postId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId")
    Long countPostComments(@Param("postId") Long postId);
}
