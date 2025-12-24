package com.TwinStar.TwinStar.comment.domain;

import com.TwinStar.TwinStar.common.domain.BaseTimeEntity;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.user.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class Comment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent" , cascade = CascadeType.ALL)
    @Builder.Default
    private List<Comment> child = new ArrayList<>();

    @OneToMany(mappedBy = "comment" , cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommentLike> commentLike = new ArrayList<>();

    @Builder.Default
    private String pinnedComment = "N";;

    @Builder.Default
    private String commentDel = "N";

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.content = "삭제된 댓글입니다.";
        this.commentDel="Y";
    }

    public void addChild(Comment comment) {
        this.child.add(comment);
    }

    public void pinned(){
        this.pinnedComment = "Y";
    }

    // 작성자 권한 검증
    public void validateOwner(User user) {
        if (!this.user.getId().equals(user.getId())) {
            throw new AccessDeniedException("해당 댓글에 대한 권한이 없습니다.");
        }
    }

    // 좋아요 토글 (추가/삭제) 및 결과 반환 (true: 추가됨, false: 삭제됨)
    public boolean toggleLike(User user) {
        Optional<CommentLike> existingLike = this.commentLike.stream()
                .filter(like -> like.getUser().getId().equals(user.getId()))
                .findFirst();

        if (existingLike.isPresent()) {
            this.commentLike.remove(existingLike.get());
            existingLike.get().setComment(null); // 양방향 관계 해제
            return false; // 좋아요 취소됨
        } else {
            CommentLike newLike = CommentLike.builder()
                    .comment(this)
                    .user(user)
                    .build();
            this.commentLike.add(newLike);
            return true; // 좋아요 추가됨
        }
    }
}
