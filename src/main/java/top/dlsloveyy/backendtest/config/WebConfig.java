package top.dlsloveyy.backendtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path uploadRoot;

    public WebConfig(@Value("${upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = resolveUploadRoot(uploadDir);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path legacyRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve("uploads").normalize();
        if (!legacyRoot.equals(uploadRoot) && Files.isDirectory(legacyRoot)) {
            registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRoot.toUri().toString(), legacyRoot.toUri().toString())
                .setCachePeriod(0);
            return;
        }

        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadRoot.toUri().toString())
            .setCachePeriod(0);
    }

    private Path resolveUploadRoot(String uploadDir) {
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

