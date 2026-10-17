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
        List<Slot> slots=slots(auth,groupId,from,to,dayStart,dayEnd,minMinutes,minMinutes,timeZone);
        Comparator<Slot> order=Comparator.comparing(Slot::availableCount,Comparator.reverseOrder()).thenComparing(Slot::startAt);
        List<Slot> result=new ArrayList<>(slots.stream().filter(Slot::everyoneAvailable).sorted(order).limit(30).toList());
        result.addAll(slots.stream().filter(s->!s.everyoneAvailable()).sorted(order).limit(10).toList());
        return result;
    }

    /**
     * 기간 전체의 빈 시간을 "같은 사람들이 계속 되는 구간"으로 합쳐 시간순으로 돌려준다.
     * 30분 간격으로 겹쳐 잘린 후보 대신 하루에 몇 개의 덩어리만 오므로, 화면이 기간 전체를 한 번에 그릴 수 있다.
     */
    @GetMapping("/blocks") List<Slot> blocks(Authentication auth,@PathVariable Long groupId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="09:00")LocalTime dayStart,@RequestParam(defaultValue="22:00")LocalTime dayEnd,@RequestParam(defaultValue="60")int minMinutes,@RequestParam(defaultValue="Asia/Seoul")String timeZone){
        List<Slot> blocks=new ArrayList<>();
        for(Slot step:slots(auth,groupId,from,to,dayStart,dayEnd,30,minMinutes,timeZone)){
            Slot last=blocks.isEmpty()?null:blocks.getLast();
            if(last!=null&&last.endAt().isEqual(step.startAt())&&last.availablePeople().equals(step.availablePeople()))blocks.set(blocks.size()-1,new Slot(last.startAt(),step.endAt(),last.availableCount(),last.totalCount(),last.availablePeople(),last.everyoneAvailable()));
            else blocks.add(step);
        }
        return blocks.stream().filter(b->Duration.between(b.startAt(),b.endAt()).toMinutes()>=minMinutes).toList();
    }

    /** 각 날짜의 [dayStart, dayEnd) 안에서 stepMinutes 길이의 구간마다 비어 있는 멤버를 계산한다. 지난 시각과 아무도 안 되는 구간은 뺀다. */
    private List<Slot> slots(Authentication auth,Long groupId,LocalDate from,LocalDate to,LocalTime dayStart,LocalTime dayEnd,int stepMinutes,int minMinutes,String timeZone){
        User current=ApiSupport.user(auth);groups.findById(groupId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"그룹을 찾을 수 없습니다."));if(!members.existsByGroupIdAndUserId(groupId,current.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"그룹 멤버만 찾을 수 있습니다.");
        if(to.isBefore(from)||to.isAfter(from.plusMonths(2)))throw new ApiException(HttpStatus.BAD_REQUEST,"검색 기간은 최대 2개월입니다.");if(minMinutes<30||minMinutes>480)throw new ApiException(HttpStatus.BAD_REQUEST,"최소 시간은 30분에서 8시간 사이여야 합니다.");
        ZoneId zone=ZoneId.of(timeZone);List<User> people=members.findByGroupId(groupId).stream().map(GroupMember::getUser).toList();OffsetDateTime rangeFrom=from.atStartOfDay(zone).toOffsetDateTime();OffsetDateTime rangeTo=to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();List<EventController.EventRange> busy=events.busyEvents(people.stream().map(User::getId).toList(),rangeFrom,rangeTo).stream().flatMap(e->EventController.occurrenceRanges(e,rangeFrom,rangeTo).stream()).toList();List<Slot> slots=new ArrayList<>();
        OffsetDateTime now=OffsetDateTime.now(clock);for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)){OffsetDateTime cursor=date.atTime(dayStart).atZone(zone).toOffsetDateTime();OffsetDateTime limit=date.atTime(dayEnd).atZone(zone).toOffsetDateTime();while(!cursor.plusMinutes(stepMinutes).isAfter(limit)){OffsetDateTime slotStart=cursor;OffsetDateTime end=slotStart.plusMinutes(stepMinutes);if(slotStart.isBefore(now)){cursor=cursor.plusMinutes(30);continue;}List<User> free=new ArrayList<>();for(User p:people){boolean overlaps=busy.stream().anyMatch(e->e.ownerId().equals(p.getId())&&e.startAt().isBefore(end)&&e.endAt().isAfter(slotStart));if(!overlaps)free.add(p);}if(!free.isEmpty())slots.add(new Slot(slotStart,end,free.size(),people.size(),free.stream().map(User::getName).toList(),free.size()==people.size()));cursor=cursor.plusMinutes(30);}}
        return slots;
    }
}
