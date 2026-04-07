package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "boards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Board implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "boards_seq_generator")
    @SequenceGenerator(name = "boards_seq_generator", sequenceName = "BOARDS_SEQ", allocationSize = 1)
    @Column(name = "board_id")
    private Long id;

    @Column(name = "board_name", nullable = false, length = 100)
    private String boardName;

    @Column(name = "board_code", nullable = false, length = 50)
    private String boardCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}