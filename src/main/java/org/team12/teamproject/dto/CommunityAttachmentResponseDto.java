package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityAttachmentResponseDto {
    private Long attachmentId;
    private String originalName;
    private String fileUrl;
    private String fileType;
    private String contentType;
    private Long fileSize;
}