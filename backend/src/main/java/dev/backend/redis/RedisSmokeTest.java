package dev.backend.redis;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisSmokeTest {

    @Bean
    CommandLineRunner redisTest(StringRedisTemplate redis) {
        return args -> {
            redis.opsForValue().set("hello", "world");
            System.out.println("[Redis]\tGET hello = " + redis.opsForValue().get("hello"));
        };
    }
}
