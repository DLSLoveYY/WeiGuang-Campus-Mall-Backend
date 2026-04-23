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
