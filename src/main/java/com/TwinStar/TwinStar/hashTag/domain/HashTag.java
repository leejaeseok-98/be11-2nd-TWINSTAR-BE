package com.TwinStar.TwinStar.hashTag.domain;


import com.TwinStar.TwinStar.common.domain.BaseTimeEntity;
import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Entity
@Builder
@BatchSize(size = 100)
public class HashTag extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false,unique = true)
    private String hashTagName;

}
