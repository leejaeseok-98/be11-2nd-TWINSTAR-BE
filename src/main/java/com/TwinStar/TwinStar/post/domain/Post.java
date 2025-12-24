package com.TwinStar.TwinStar.post.domain;

import com.TwinStar.TwinStar.chat.domain.ReadStatus;
import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.common.domain.BaseTimeEntity;
import com.TwinStar.TwinStar.common.domain.Visibility;
import com.TwinStar.TwinStar.hashTag.domain.PostHashTag;
import com.TwinStar.TwinStar.post.dto.PostUpdateResDto;
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
import java.util.stream.Collectors;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class Post extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String content;

    @Builder.Default
    private String postDel = "N";

    private Visibility visibility;

    private Long score;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostLike> postLikes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<PostFile> postFile = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<PostHashTag> hashTag = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> comment = new ArrayList<>();

    public void updateContent(String content){
        this.content = content;
    }

    public PostUpdateResDto formEntity(List<String> hashTag, List<String> imageFile){
        return PostUpdateResDto.builder()
                .content(this.content)
                .visibility(this.visibility)
                .hashTag(hashTag)
                .imageFile(imageFile)
                .build();
    }

    public List<String> getFileUrls() {
        return postFile.stream()
                .map(PostFile::getFileUrl)
                .collect(Collectors.toList());
    }

    // 작성자 권한 검증
    public void validateOwner(User user) {
        if (!this.user.getId().equals(user.getId())) {
            throw new AccessDeniedException("해당 게시물에 대한 권한이 없습니다.");
        }
    }

    // 좋아요 토글 (추가/삭제) 및 결과 반환 (true: 추가됨, false: 삭제됨)
    public boolean toggleLike(User user) {
        Optional<PostLike> existingLike = this.postLikes.stream()
                .filter(like -> like.getUser().getId().equals(user.getId()))
                .findFirst();

        if (existingLike.isPresent()) {
            this.postLikes.remove(existingLike.get());
            existingLike.get().setPost(null); // 양방향 관계 해제
            return false; // 좋아요 취소됨
        } else {
            PostLike newLike = PostLike.builder()
                    .post(this)
                    .user(user)
                    .build();
            this.postLikes.add(newLike);
            return true; // 좋아요 추가됨
        }
    }
}
