package org.team12.teamproject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:5174", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String profilePath = getProfileDirectory().toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/api/profile/**")
                .addResourceLocations(profilePath);

        Path uploadPath = getUploadDirectory().toAbsolutePath().normalize();

        System.out.println("=== WebConfig uploadDir = " + uploadDir);
        System.out.println("=== WebConfig user.dir = " + System.getProperty("user.dir"));
        System.out.println("=== WebConfig uploadPath = " + uploadPath);

        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");
    }

    private Path getProfileDirectory() {
        Path backendProfileDirectory = Paths.get("backend", "profile");
        if (Files.exists(Paths.get("backend")) || Files.exists(backendProfileDirectory)) {
            return backendProfileDirectory;
        }
        return Paths.get("profile");
    }

    private Path getUploadDirectory() {
        Path configuredPath = Paths.get(uploadDir);

        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        return Paths.get(System.getProperty("user.dir"), uploadDir);
    }
}