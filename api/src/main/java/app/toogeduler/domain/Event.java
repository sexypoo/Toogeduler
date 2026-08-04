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
    /** 기존 단일 공개 범위 데이터와의 호환을 위해 유지한다. 신규 코드는 visibilities를 사용한다. */
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Visibility visibility=Visibility.PRIVATE;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="event_visibilities",joinColumns=@JoinColumn(name="event_id"))
    @Enumerated(EnumType.STRING) @Column(name="visibility",nullable=false)
    private Set<Visibility> visibilities=new HashSet<>();
    private String recurrenceRule;
    private Integer reminderMinutes;
    @Column(nullable=false) private String color="#168CF2";
    @Column(unique=true) private String publicToken;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="event_groups", joinColumns=@JoinColumn(name="event_id"), inverseJoinColumns=@JoinColumn(name="group_id"))
    private Set<Group> groups=new HashSet<>();
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    @PostLoad void migrateVisibility(){if(visibilities.isEmpty()&&visibility!=null)visibilities.add(visibility);}
    @PreUpdate void updated(){ updatedAt=Instant.now(); }
}
