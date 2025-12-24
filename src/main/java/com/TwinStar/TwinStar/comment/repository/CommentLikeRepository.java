package com.TwinStar.TwinStar.comment.repository;

import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.comment.domain.CommentLike;
import com.TwinStar.TwinStar.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);

    Long countByComment(Comment comment);

    @Query("SELECT cl.comment.id, COUNT(cl) FROM CommentLike cl WHERE cl.comment.id IN :commentIds GROUP BY cl.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    // 특정 유저가 좋아요한 댓글 ID 목록 조회 (N+1 해결용)
    @Query("SELECT cl.comment.id FROM CommentLike cl WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    Set<Long> findLikedCommentIdsByUserId(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);

    @Query("SELECT cl.user FROM CommentLike cl WHERE cl.comment.id = :commentId")
    Page<User> findUsersWhoLikedComment(@Param("commentId") Long commentId, Pageable pageable);
}
