package app.toogeduler.repo;
import app.toogeduler.domain.Event;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;
public interface EventRepository extends JpaRepository<Event,Long>{
    Optional<Event> findByPublicToken(String token);
    @Query("select distinct e from Event e left join e.groups g where (e.recurrenceRule is not null or (e.endAt > :from and e.startAt < :to)) and (e.owner.id=:userId or g.id in :groupIds)")
    List<Event> calendar(@Param("userId") Long userId,@Param("groupIds") Collection<Long> groupIds,@Param("from") OffsetDateTime from,@Param("to") OffsetDateTime to);
    @Query("select distinct e from Event e left join e.groups g where (e.recurrenceRule is not null or (e.endAt > :from and e.startAt < :to)) and (e.owner.id=:userId or e.visibility='PUBLIC' or (e.visibility='FRIENDS' and e.owner.id in :friendIds) or (e.visibility='GROUP' and g.id in :groupIds))")
    List<Event> visibleCalendar(@Param("userId")Long userId,@Param("friendIds")Collection<Long>friendIds,@Param("groupIds")Collection<Long>groupIds,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select distinct e from Event e join e.groups g where g.id=:groupId and e.visibility='GROUP' and (e.recurrenceRule is not null or (e.endAt > :from and e.startAt < :to))")
    List<Event> groupCalendar(@Param("groupId")Long groupId,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select e from Event e where e.owner.id in :userIds and (e.recurrenceRule is not null or (e.endAt > :from and e.startAt < :to))")
    List<Event> busyEvents(@Param("userIds")Collection<Long> userIds,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
}
