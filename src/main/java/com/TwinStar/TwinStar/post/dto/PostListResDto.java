package com.TwinStar.TwinStar.post.dto;

import com.TwinStar.TwinStar.post.domain.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PostListResDto {
    private Long userId;
    private String nickName;
    private String profileImage;
    private Long postId;
    private List<String> imageList;
    private String content;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime createdTime;
    private String isUpdate;
    private List<String> hashTag;
    private String isLike;
    private String isFollow;

    public static PostListResDto fromEntity(Post post, Long likeCount, Long commentCount, List<String> hashTags, String isLike, String isFollow) {
        return fromEntity(post, likeCount, commentCount, hashTags, isLike, isFollow, post.getFileUrls());
    }

    public static PostListResDto fromEntity(Post post, Long likeCount, Long commentCount, List<String> hashTags, String isLike, String isFollow, List<String> imageList) {
        return PostListResDto.builder()
                .userId(post.getUser().getId())
                .nickName(post.getUser().getNickName())
                .profileImage(post.getUser().getProfileImg())
                .postId(post.getId())
                .imageList(imageList)
                .content(post.getContent())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .createdTime(post.getCreatedTime())
                .isUpdate(determineUpdateStatus(post))
                .hashTag(hashTags)
                .isLike(isLike)
                .isFollow(isFollow)
                .build();
    }

    // 수정 여부 판단 (createdTime과 updatedTime 비교)
    private static String determineUpdateStatus(Post post) {
        return (post.getUpdatedTime() != null && !post.getUpdatedTime().equals(post.getCreatedTime())) ? "Y" : "N";
    }
}
