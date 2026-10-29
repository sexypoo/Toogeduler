package app.toogeduler.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "calendar_groups")
public class Group {
    /** 초대 링크는 만들거나 재발급한 때부터 이 기간 동안만 쓸 수 있다. */
    public static final Duration INVITE_TTL = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String color = "#19B7B1";

    @ManyToOne(optional = false)
    private User owner;

    @Column(nullable = false, unique = true)
    private String inviteToken;

    /** 초대 링크 만료 시각. null 이면 만료 없음(V2 마이그레이션이 기존 행을 채우므로 실제로는 없다). */
    private Instant inviteExpiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * 초대 토큰을 새로 정하고 만료를 지금부터 INVITE_TTL 뒤로 둔다. 옛 토큰은 더 이상 어떤 그룹과도 맞지 않는다. 만료 시각은 DB
     * 컬럼(timestamp(6)) 정밀도인 마이크로초로 잘라, 응답에 실은 값과 다시 읽은 값이 같게 한다.
     */
    public void renewInvite(String token, Instant now) {
        inviteToken = token;
        inviteExpiresAt = now.plus(INVITE_TTL).truncatedTo(ChronoUnit.MICROS);
    }

    public boolean inviteExpired(Instant now) {
        return inviteExpiresAt != null && !now.isBefore(inviteExpiresAt);
    }
}
