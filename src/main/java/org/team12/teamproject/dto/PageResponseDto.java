package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class PageResponseDto<T> {
    private List<T> content;      // 주식 리스트
    private int currentPage;      // 현재 페이지 번호
    private int totalPages;       // 전체 페이지 수
    private int totalElements;    // 전체 데이터 개수
}