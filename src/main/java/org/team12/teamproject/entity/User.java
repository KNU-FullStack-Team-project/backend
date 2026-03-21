package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    // 양방향 매핑: 유저 한 명이 여러 계좌(일반 1개 + 대회 N개)를 가질 수 있음
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Account> accounts = new ArrayList<>();

    @Builder
    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }
}