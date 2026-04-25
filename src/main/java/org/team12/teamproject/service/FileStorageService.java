package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.team12.teamproject.dto.CommunityAttachmentResponseDto;
import org.team12.teamproject.entity.PostAttachment;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.PostAttachmentRepository;
import org.team12.teamproject.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final UserRepository userRepository;
    private final PostAttachmentRepository postAttachmentRepository;

    private static final DateTimeFormatter FILE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional
    public CommunityAttachmentResponseDto storeImage(MultipartFile file, String email) {
        validateImage(file);
        return storeImageOnly(file, email, "community/images");
    }

    @Transactional
    public CommunityAttachmentResponseDto storeFile(MultipartFile file, String email) {
        validateGeneralFile(file);
        return storeFileOnly(file, email, "community/files");
    }

    private CommunityAttachmentResponseDto storeImageOnly(MultipartFile file, String email, String subDir) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

            String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "image");
            String extension = extractExtension(originalName);
            String storedName = createImageStoredFileName(extension);

            Path rootPath = getUploadRootPath();
            Path targetDir = rootPath.resolve(subDir).normalize();
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedName);

            System.out.println("=== FileStorageService IMAGE rootPath = " + rootPath);
            System.out.println("=== FileStorageService IMAGE targetDir = " + targetDir);
            System.out.println("=== FileStorageService IMAGE targetPath = " + targetPath);

            file.transferTo(targetPath.toFile());

            String fileUrl = "/api/uploads/" + subDir.replace("\\", "/") + "/" + storedName;

            return CommunityAttachmentResponseDto.builder()
                    .attachmentId(null)
                    .originalName(originalName)
                    .fileUrl(fileUrl)
                    .fileType("IMAGE")
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();

        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("이미지 저장 중 오류가 발생했습니다.");
        }
    }

    private CommunityAttachmentResponseDto storeFileOnly(MultipartFile file, String email, String subDir) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

            String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
            String extension = extractExtension(originalName);
            String storedName = createTempFileStoredFileName(user.getId(), extension);

            Path rootPath = getUploadRootPath();
            Path targetDir = rootPath.resolve(subDir).normalize();
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedName);

            System.out.println("=== FileStorageService FILE rootPath = " + rootPath);
            System.out.println("=== FileStorageService FILE targetDir = " + targetDir);
            System.out.println("=== FileStorageService FILE targetPath = " + targetPath);

            file.transferTo(targetPath.toFile());

            String fileUrl = "/api/uploads/" + subDir.replace("\\", "/") + "/" + storedName;

            PostAttachment saved = postAttachmentRepository.save(
                    PostAttachment.builder()
                            .post(null)
                            .user(user)
                            .originalName(originalName)
                            .storedName(storedName)
                            .fileUrl(fileUrl)
                            .fileType("FILE")
                            .contentType(file.getContentType())
                            .fileSize(file.getSize())
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            return CommunityAttachmentResponseDto.builder()
                    .attachmentId(saved.getId())
                    .originalName(saved.getOriginalName())
                    .fileUrl(saved.getFileUrl())
                    .fileType(saved.getFileType())
                    .contentType(saved.getContentType())
                    .fileSize(saved.getFileSize())
                    .build();

        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
        }
    }

    private String createImageStoredFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private String createTempFileStoredFileName(Long userId, String extension) {
        String uploadDate = LocalDateTime.now().format(FILE_DATE_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uploadDate + "_" + userId + "_TEMP_FILE_" + uuid + extension;
    }

    private Path getUploadRootPath() {
        Path configuredPath = Paths.get(uploadDir);

        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        return Paths.get(System.getProperty("user.dir"), uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    private void validateImage(MultipartFile file) {
        validateEmpty(file);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private void validateGeneralFile(MultipartFile file) {
        validateEmpty(file);

        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("파일은 최대 20MB까지 업로드할 수 있습니다.");
        }
    }

    private void validateEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
    }

    private String extractExtension(String originalName) {
        int index = originalName.lastIndexOf(".");
        if (index < 0) {
            return "";
        }
        return originalName.substring(index);
    }
}