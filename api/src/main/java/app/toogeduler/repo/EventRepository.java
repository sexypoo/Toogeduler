package app.toogeduler.repo;
import app.toogeduler.domain.Event;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;
public interface EventRepository extends JpaRepository<Event,Long>{
    Optional<Event> findByPublicToken(String token);
    long countByOwnerId(Long ownerId);
    List<Event> findByOwnerId(Long ownerId);
    @Query("select distinct e from Event e join e.groups g where g.id=:groupId")
    List<Event> findByGroupId(@Param("groupId")Long groupId);
    /** "내 캘린더": 내가 만든 일정만. 친구·그룹 일정은 각자의 화면(친구 프로필, 그룹 캘린더)에서 본다. */
    @Query("select e from Event e where e.owner.id=:userId and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.endAt > :from and e.startAt < :to))")
    List<Event> ownedCalendar(@Param("userId") Long userId,@Param("from") OffsetDateTime from,@Param("to") OffsetDateTime to);
    @Query("select distinct e from Event e join e.groups g left join e.visibilities v where g.id=:groupId and exists (select 1 from GroupMember m where m.group.id=:groupId and m.user.id=e.owner.id) and (e.visibility='GROUP' or v='GROUP') and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.endAt > :from and e.startAt < :to))")
    List<Event> groupCalendar(@Param("groupId")Long groupId,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select e from Event e where e.owner.id in :userIds and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.endAt > :from and e.startAt < :to))")
    List<Event> busyEvents(@Param("userIds")Collection<Long> userIds,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select distinct e from Event e left join e.visibilities v where (e.visibility='PUBLIC' or v='PUBLIC') and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.endAt > :from and e.startAt < :to)) order by e.startAt")
    List<Event> publicCalendar(@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select distinct e from Event e left join e.visibilities v where e.owner.id=:ownerId and (e.visibility in ('FRIENDS','PUBLIC') or v in ('FRIENDS','PUBLIC')) and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.endAt > :from and e.startAt < :to)) order by e.startAt")
    List<Event> friendCalendar(@Param("ownerId")Long ownerId,@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
    @Query("select e from Event e where e.reminderMinutes is not null and ((e.recurrenceRule is not null and e.recurrenceRule <> '') or (e.startAt > :from and e.startAt <= :to))")
    List<Event> reminderCandidates(@Param("from")OffsetDateTime from,@Param("to")OffsetDateTime to);
}
