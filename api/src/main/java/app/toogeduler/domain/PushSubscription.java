package app.toogeduler.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * 브라우저 하나의 웹 푸시 구독. endpoint 는 브라우저마다 고유하므로 같은 브라우저에서 다시 등록하면 새로 만들지 않고 이 행을 갱신한다. 로그아웃·탈퇴, 또는 푸시
 * 서비스가 410/404 로 만료를 알려 오면 지운다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "push_subscriptions",
        indexes = {@Index(name = "idx_push_subscriptions_user", columnList = "user_id")})
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    /** 브라우저의 P-256 공개키(base64url). 알림 내용을 이 키로 암호화한다. */
    @Column(nullable = false)
    private String p256dh;

    /** 브라우저가 만든 인증 비밀값(base64url, 16바이트). */
    @Column(nullable = false)
    private String auth;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
