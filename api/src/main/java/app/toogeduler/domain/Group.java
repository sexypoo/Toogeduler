package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="calendar_groups")
public class Group {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String color="#19B7B1";
    @ManyToOne(optional=false) private User owner;
    @Column(nullable=false,unique=true) private String inviteToken;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}

