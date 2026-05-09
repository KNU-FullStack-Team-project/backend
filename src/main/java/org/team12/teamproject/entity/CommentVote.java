package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "comment_votes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_comment_votes_comment_user", columnNames = {"comment_id", "user_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SequenceGenerator(
        name = "commentVoteSeqGenerator",
        sequenceName = "COMMENT_VOTES_SEQ",
        allocationSize = 1
)
public class CommentVote {

    public static final String VOTE_TYPE_LIKE = "LIKE";
    public static final String VOTE_TYPE_DISLIKE = "DISLIKE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "commentVoteSeqGenerator")
    @Column(name = "comment_vote_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "vote_type", nullable = false, length = 20)
    private String voteType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public CommentVote(Comment comment, User user, String voteType, LocalDateTime createdAt) {
        this.comment = comment;
        this.user = user;
        this.voteType = voteType;
        this.createdAt = createdAt;
    }
}
