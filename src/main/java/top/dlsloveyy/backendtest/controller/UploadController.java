package top.dlsloveyy.backendtest.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class UploadController {

    private final Path uploadRoot;

    public UploadController(@Value("${upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = resolveUploadRoot(uploadDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
            }
            validateImageFile(file);

            String newFileName = UUID.randomUUID() + getSafeExtension(file.getOriginalFilename());
            Path destination = uploadRoot.resolve(newFileName).normalize();
            file.transferTo(destination);

            return ResponseEntity.ok(Map.of("url", "/uploads/" + newFileName));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "上传失败：" + e.getMessage()));
        }
    }

    @PostMapping("/upload/avatar")
    public ResponseResult<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseResult.error("文件为空");
        }
        try {
            validateImageFile(file);
            Path avatarDir = uploadRoot.resolve("avatars");
            Files.createDirectories(avatarDir);
            String fileName = "avatar_" + UUID.randomUUID() + getSafeExtension(file.getOriginalFilename());
            Path destination = avatarDir.resolve(fileName).normalize();
            file.transferTo(destination);
            return ResponseResult.success("上传成功", "/uploads/avatars/" + fileName);
        } catch (IOException e) {
            return ResponseResult.error("上传失败：" + e.getMessage());
        }
    }

    private static String getSafeExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex);
    }

    private static void validateImageFile(MultipartFile file) {
        long maxSize = 10L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("图片大小不能超过 10MB");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String extension = getSafeExtension(filename).toLowerCase();
        Set<String> allowed = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
        if (!allowed.contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、gif、webp 图片格式");
        }
    }

    private static Path resolveUploadRoot(String uploadDir) {
        Path configuredPath = Paths.get(uploadDir).normalize();
        if (!configuredPath.isAbsolute()) {
            Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            if (!"backend-main".equalsIgnoreCase(workingDir.getFileName().toString())) {
                Path backendMainDir = workingDir.resolve("backend-main");
                if (Files.isDirectory(backendMainDir)) {
                    workingDir = backendMainDir;
                }
            }
            configuredPath = workingDir.resolve(configuredPath).normalize();
        }
        try {
            Files.createDirectories(configuredPath);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + configuredPath, e);
        }
        return configuredPath;
    }

}
