package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "votes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_votes_user_target",
                        columnNames = {"target_type", "target_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Vote {

    public static final String TARGET_TYPE_POST = "POST";
    public static final String TARGET_TYPE_COMMENT = "COMMENT";

    public static final String VOTE_TYPE_LIKE = "LIKE";
    public static final String VOTE_TYPE_DISLIKE = "DISLIKE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "votes_seq_generator")
    @SequenceGenerator(
            name = "votes_seq_generator",
            sequenceName = "VOTES_SEQ",
            allocationSize = 1
    )
    @Column(name = "vote_id")
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "vote_type", nullable = false, length = 20)
    private String voteType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}