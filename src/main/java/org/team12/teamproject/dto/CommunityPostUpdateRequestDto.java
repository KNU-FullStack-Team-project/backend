package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CommunityPostUpdateRequestDto {
    private String title;
    private String content;
    private Boolean isNotice;
    private List<Long> attachmentIds;
}