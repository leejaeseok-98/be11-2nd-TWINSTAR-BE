package com.TwinStar.TwinStar.follow.domain;

import com.TwinStar.TwinStar.common.domain.YN;
import com.TwinStar.TwinStar.user.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 팔로우를 한사람 (나)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receive_user_id", nullable = false)
    private User receiveUser; // 팔로우를 당한사람 (카리나)

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private YN followYn;

    @PrePersist
    public void prePersist() {
        if (followYn == null) {
            this.followYn = YN.Y; // 기본값을 Y로 설정
        }
    }

    public Follow(User user, User receiveUser) {
        this.user = user;
        this.receiveUser = receiveUser;
        this.followYn = YN.Y; // 새로 생성 시 기본값 Y
    }

    // 팔로우 상태 변경 메서드
    public void toggleFollow() {
        this.followYn = (this.followYn == YN.Y) ? YN.N : YN.Y;
    }
}
