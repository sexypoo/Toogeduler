package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="app_notifications",indexes={@Index(columnList="user_id,createdAt")},uniqueConstraints=@UniqueConstraint(columnNames={"user_id","dedupe_key"}))
public class Notification {
    public enum Type { FRIEND_REQUEST, FRIEND_ACCEPTED, GROUP_JOINED, EVENT_REMINDER, GROUP_EVENT }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Type type;
    @Column(nullable=false,length=100) private String title;
    @Column(nullable=false,length=300) private String message;
    @Column(nullable=false,length=255) private String link="/";
    @Column(name="dedupe_key",nullable=false,length=180) private String dedupeKey;
    private Instant readAt;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
