package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints=@UniqueConstraint(columnNames={"group_id","user_id"}))
public class GroupMember {
    public enum Role { OWNER, MEMBER }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private Group group;
    @ManyToOne(optional=false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role=Role.MEMBER;
    @Column(nullable=false) private Instant joinedAt=Instant.now();
}

