package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames={"requester_id","receiver_id"}))
public class Friendship {
    public enum Status { PENDING, ACCEPTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private User requester;
    @ManyToOne(optional=false) private User receiver;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status=Status.PENDING;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}

