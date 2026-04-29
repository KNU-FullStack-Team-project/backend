package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_likes_post_user", columnNames = {"post_id", "user_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SequenceGenerator(
        name = "postLikeSeqGenerator",
        sequenceName = "seq_post_likes",
        allocationSize = 1
)
public class PostLike {

    public static final String VOTE_TYPE_LIKE = "LIKE";
    public static final String VOTE_TYPE_DISLIKE = "DISLIKE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "postLikeSeqGenerator")
    @Column(name = "post_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "vote_type", nullable = false, length = 20)
    private String voteType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PostLike(Post post, User user, String voteType, LocalDateTime createdAt) {
        this.post = post;
        this.user = user;
        this.voteType = voteType;
        this.createdAt = createdAt;
    }
}