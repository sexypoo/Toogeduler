package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String name;
    private String avatarUrl;
    private String passwordHash;
    @Column(nullable = false) private String provider = "LOCAL";
    @Column(nullable = false) private Instant createdAt = Instant.now();
    public User(String email, String name, String passwordHash) { this.email=email; this.name=name; this.passwordHash=passwordHash; }
}

