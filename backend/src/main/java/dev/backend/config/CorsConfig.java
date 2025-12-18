package dev.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location") // 필요하면 추가
                .allowCredentials(true)     // 쿠키/세션/인증 필요하면 true
                .maxAge(3600);

        registry.addMapping("/stream/**")
                .allowedOriginPatterns(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET")
                .allowedHeaders("*")
                .maxAge(3600);

    }
}
