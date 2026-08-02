package app.toogeduler.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="calendar_events", indexes={@Index(columnList="startAt"),@Index(columnList="publicToken")})
public class Event {
    public enum Visibility { PRIVATE, GROUP, FRIENDS, PUBLIC }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private User owner;
    @Column(nullable=false) private String title;
    private String description;
    private String location;
    @Column(nullable=false) private OffsetDateTime startAt;
    @Column(nullable=false) private OffsetDateTime endAt;
    @Column(nullable=false) private boolean allDay;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Visibility visibility=Visibility.PRIVATE;
    private String recurrenceRule;
    @Column(nullable=false) private String color="#168CF2";
    @Column(unique=true) private String publicToken;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="event_groups", joinColumns=@JoinColumn(name="event_id"), inverseJoinColumns=@JoinColumn(name="group_id"))
    private Set<Group> groups=new HashSet<>();
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    @PreUpdate void updated(){ updatedAt=Instant.now(); }
}
