package dev.backend.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "ix_refresh_user_id", columnList = "user_id"),
                @Index(name = "ix_refresh_expires_at", columnList = "expires_at")
        }
)
@Setter
@Getter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @JsonIgnore // API로 엔티티 그대로 내보낼 때 순환참조 방지용(선택)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_user")
    )
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // DB가 DEFAULT CURRENT_TIMESTAMP로 넣어주니까 JPA는 읽기 전용이 편함
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

}
