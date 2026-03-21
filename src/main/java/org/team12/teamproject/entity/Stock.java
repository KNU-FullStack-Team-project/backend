package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity 
@Table(name = "stocks") // 오라클에 생성될 실제 테이블 이름
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id 
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol; // 종목코드 (예: 005930)

    @Column(name = "name", length = 100, nullable = false)
    private String name; // 종목명 (예: 삼성전자)

    // 필요하다면 카테고리(업종), 상장일 등의 컬럼을 아래에 계속 추가하면 됩니다!

    @Builder
    public Stock(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }
}