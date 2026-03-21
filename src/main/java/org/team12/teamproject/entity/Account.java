package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    // N:1 관계 (계좌 여러 개가 유저 한 명에게 속함)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 계좌 종류 (GENERAL: 일반계좌, COMPETITION: 대회계좌)
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType type;

    // 일반 계좌일 때는 null, 대회 계좌일 때는 해당 대회 ID가 들어감
    @Column(name = "competition_id")
    private Long competitionId;

    // 계좌 잔고 (기본값 10,000,000원)
    @Column(name = "balance", nullable = false)
    private Long balance = 10000000L;

    @Builder
    public Account(User user, AccountType type, Long competitionId, Long balance) {
        this.user = user;
        this.type = type;
        this.competitionId = competitionId;
        this.balance = (balance != null) ? balance : 0L;
    }

    // 계좌 타입을 정의하는 Enum
    public enum AccountType {
        GENERAL,       // 일반 계좌 (유저당 1개만 허용하는 것은 Service 로직에서 방어)
        COMPETITION    // 대회용 임시 계좌
    }
}