package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/groups/{groupId}/availability") @RequiredArgsConstructor
public class AvailabilityController {
    private final GroupRepository groups; private final GroupMemberRepository members; private final EventRepository events;
    /** 지금 시각. 테스트에서 고정할 수 있게 필드로 둔다. */
    Clock clock=Clock.systemUTC();
    public record Slot(OffsetDateTime startAt,OffsetDateTime endAt,int availableCount,int totalCount,List<String>availablePeople,boolean everyoneAvailable){}
    @GetMapping List<Slot> find(Authentication auth,@PathVariable Long groupId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="09:00")LocalTime dayStart,@RequestParam(defaultValue="22:00")LocalTime dayEnd,@RequestParam(defaultValue="60")int minMinutes,@RequestParam(defaultValue="Asia/Seoul")String timeZone){
        User current=ApiSupport.user(auth);groups.findById(groupId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"그룹을 찾을 수 없습니다."));if(!members.existsByGroupIdAndUserId(groupId,current.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"그룹 멤버만 찾을 수 있습니다.");
        if(to.isBefore(from)||to.isAfter(from.plusMonths(2)))throw new ApiException(HttpStatus.BAD_REQUEST,"검색 기간은 최대 2개월입니다.");if(minMinutes<30||minMinutes>480)throw new ApiException(HttpStatus.BAD_REQUEST,"최소 시간은 30분에서 8시간 사이여야 합니다.");
        ZoneId zone=ZoneId.of(timeZone);List<User> people=members.findByGroupId(groupId).stream().map(GroupMember::getUser).toList();OffsetDateTime rangeFrom=from.atStartOfDay(zone).toOffsetDateTime();OffsetDateTime rangeTo=to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();List<EventController.EventRange> busy=events.busyEvents(people.stream().map(User::getId).toList(),rangeFrom,rangeTo).stream().flatMap(e->EventController.occurrenceRanges(e,rangeFrom,rangeTo).stream()).toList();List<Slot> slots=new ArrayList<>();
        OffsetDateTime now=OffsetDateTime.now(clock);for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)){OffsetDateTime cursor=date.atTime(dayStart).atZone(zone).toOffsetDateTime();OffsetDateTime limit=date.atTime(dayEnd).atZone(zone).toOffsetDateTime();while(!cursor.plusMinutes(minMinutes).isAfter(limit)){OffsetDateTime slotStart=cursor;OffsetDateTime end=slotStart.plusMinutes(minMinutes);if(slotStart.isBefore(now)){cursor=cursor.plusMinutes(30);continue;}List<User> free=new ArrayList<>();for(User p:people){boolean overlaps=busy.stream().anyMatch(e->e.ownerId().equals(p.getId())&&e.startAt().isBefore(end)&&e.endAt().isAfter(slotStart));if(!overlaps)free.add(p);}if(!free.isEmpty())slots.add(new Slot(slotStart,end,free.size(),people.size(),free.stream().map(User::getName).toList(),free.size()==people.size()));cursor=cursor.plusMinutes(30);}}
        Comparator<Slot> order=Comparator.comparing(Slot::availableCount,Comparator.reverseOrder()).thenComparing(Slot::startAt);
        List<Slot> result=new ArrayList<>(slots.stream().filter(Slot::everyoneAvailable).sorted(order).limit(30).toList());
        result.addAll(slots.stream().filter(s->!s.everyoneAvailable()).sorted(order).limit(10).toList());
        return result;
    }
}
