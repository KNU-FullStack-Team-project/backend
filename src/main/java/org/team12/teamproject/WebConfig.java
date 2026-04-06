package org.team12.teamproject;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:5174", "http://localhost:3000") // React 
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String profilePath = getProfileDirectory().toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/profile/**")
                .addResourceLocations(profilePath);
    }

    private Path getProfileDirectory() {
        Path backendProfileDirectory = Paths.get("backend", "profile");
        if (Files.exists(Paths.get("backend")) || Files.exists(backendProfileDirectory)) {
            return backendProfileDirectory;
        }

        return Paths.get("profile");
    }
}
