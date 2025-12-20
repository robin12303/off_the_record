package dev.backend.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtProvider jwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-seconds}") long accessTokenSeconds
    ) {
        return new JwtProvider(secret, accessTokenSeconds);
    }
}