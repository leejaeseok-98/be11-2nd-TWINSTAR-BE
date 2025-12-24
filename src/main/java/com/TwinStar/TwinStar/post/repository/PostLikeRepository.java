package com.TwinStar.TwinStar.post.repository;

import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.domain.PostLike;
import com.TwinStar.TwinStar.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository

public interface PostLikeRepository extends JpaRepository<PostLike,Long> {
    Long countByPost(Post post);

    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    Optional<PostLike> findByPostAndUser(Post post, User user);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    // 특정 유저가 좋아요한 게시물 ID 목록 조회 (N+1 해결용)
    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.id = :userId AND pl.post.id IN :postIds")
    Set<Long> findLikedPostIdsByUserId(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post.id = :postId")
    Page<User> findUsersWhoLikedPost(@Param("postId") Long postId, Pageable pageable);
}
