package top.dlsloveyy.backendtest.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UploadController {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
            }

            String uploadPath = System.getProperty("user.dir") + File.separator + "uploads";
            File folder = new File(uploadPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            String newFileName = UUID.randomUUID() + "." + extension;

            File dest = new File(folder, newFileName);
            file.transferTo(dest);

            return ResponseEntity.ok(Map.of("url", "/uploads/" + newFileName));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "上传失败：" + e.getMessage()));
        }
    }

    @PostMapping("/upload/avatar")
    public String uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "文件为空";
        }
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";

        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        File dest = new File(uploadDir + fileName);
        try {
            file.transferTo(dest);
            return "/uploads/avatars/" + fileName;
        } catch (IOException e) {
            e.printStackTrace();
            return "上传失败";
        }
    }


}
