package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityPostCreateRequestDto {
    private String title;
    private String content;
}