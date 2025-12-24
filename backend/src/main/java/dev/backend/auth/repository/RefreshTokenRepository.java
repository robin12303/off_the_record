package dev.backend.auth.repository;

import dev.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update RefreshToken rt
           set rt.revokedAt = :now
           where rt.userId = :userId
             and rt.revokedAt is null
           """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}