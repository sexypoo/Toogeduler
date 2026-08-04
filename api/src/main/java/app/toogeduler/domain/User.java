package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String name;
    private String avatarUrl;
    @Column(unique=true,length=12) private String friendCode=createFriendCode();
    private String passwordHash;
    @Column(nullable = false) private String provider = "LOCAL";
    @Column(nullable = false) private Instant createdAt = Instant.now();
    public User(String email, String name, String passwordHash) { this.email=email; this.name=name; this.passwordHash=passwordHash; }
    public static String createFriendCode(){return "TGD-"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
}
