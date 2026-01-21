package com.TwinStar.TwinStar.comment.repository;

import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Long countByPost(Post post);

    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    // parentId를 이용해 해당 댓글과 연관된 Post 찾기
    @Query("SELECT c.post FROM Comment c WHERE c.id = :parentId")
    Post findPostByParentId(@Param("parentId") Long parentId);

    List<Comment> findByPost(Post post);

    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post = :post")
    List<Comment> findByPostWithUser(@Param("post") Post post);

    @Query("SELECT COUNT(cl) FROM CommentLike cl WHERE cl.comment.id = :commentId")
    Long countCommentLikes(@Param("commentId") Long commentId);
}
