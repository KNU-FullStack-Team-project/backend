package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "post_attachments_seq_generator")
    @SequenceGenerator(
            name = "post_attachments_seq_generator",
            sequenceName = "POST_ATTACHMENTS_SEQ",
            allocationSize = 1
    )
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void assignToPost(Post post) {
        this.post = post;
    }

    public void detachFromPost() {
        this.post = null;
    }

    public boolean isOwner(Long userId) {
        return this.user != null && this.user.getId().equals(userId);
    }
    public void updateFileInfo(String storedName, String fileUrl, Post post) {
        this.storedName = storedName;
        this.fileUrl = fileUrl;
        this.post = post;
    }
}