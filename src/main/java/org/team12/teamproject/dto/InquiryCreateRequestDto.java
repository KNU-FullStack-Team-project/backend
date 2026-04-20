package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InquiryCreateRequestDto {
    private String category;
    private String title;
    private String content;
}
