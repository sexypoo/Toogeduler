package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class EventController {
    private final EventRepository events; private final GroupRepository groups; private final GroupMemberRepository members; private final FriendshipRepository friendships;
    public record EventInput(@NotBlank @Size(max=100)String title,String description,String location,@NotNull OffsetDateTime startAt,@NotNull OffsetDateTime endAt,boolean allDay,@NotNull Event.Visibility visibility,String recurrenceRule,String color,Set<Long>groupIds){}
    @GetMapping("/api/events") List<Map<String,Object>> calendar(Authentication auth,@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to){
        User user=ApiSupport.user(auth); List<Long> ids=members.findByUserId(user.getId()).stream().map(m->m.getGroup().getId()).toList();
        Collection<Long> safeIds=ids.isEmpty()?List.of(-1L):ids;List<Long> friendIds=friendships.findByRequesterIdOrReceiverId(user.getId(),user.getId()).stream().filter(f->f.getStatus()==Friendship.Status.ACCEPTED).map(f->f.getRequester().getId().equals(user.getId())?f.getReceiver().getId():f.getRequester().getId()).toList();
        return events.visibleCalendar(user.getId(),friendIds.isEmpty()?List.of(-1L):friendIds,safeIds,from,to).stream().flatMap(e->occurrenceViews(e,from,to).stream()).toList();
    }
    @PostMapping("/api/events") Map<String,Object> create(Authentication auth,@Valid @RequestBody EventInput body){
        User user=ApiSupport.user(auth); validate(body); Event e=new Event(); e.setOwner(user); apply(e,body,user);e.setPublicToken(UUID.randomUUID().toString());return view(events.save(e),true);
    }
    @PutMapping("/api/events/{id}") Map<String,Object> update(Authentication auth,@PathVariable Long id,@Valid @RequestBody EventInput body){
        User user=ApiSupport.user(auth);Event e=owned(id,user);validate(body);apply(e,body,user);return view(events.save(e),true);
    }
    @PatchMapping("/api/events/{id}/move") Map<String,Object> move(Authentication auth,@PathVariable Long id,@RequestBody Map<String,String> body){
        User user=ApiSupport.user(auth);Event e=owned(id,user);e.setStartAt(OffsetDateTime.parse(body.get("startAt")));e.setEndAt(OffsetDateTime.parse(body.get("endAt")));return view(events.save(e),true);
    }
    @DeleteMapping("/api/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication auth,@PathVariable Long id){events.delete(owned(id,ApiSupport.user(auth)));}
    @GetMapping("/api/public/events/{token}") Map<String,Object> publicEvent(@PathVariable String token){Event e=events.findByPublicToken(token).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"일정을 찾을 수 없습니다."));if(e.getVisibility()!=Event.Visibility.PUBLIC)throw new ApiException(HttpStatus.NOT_FOUND,"공개되지 않은 일정입니다.");return view(e,true);}
    private Event owned(Long id,User u){Event e=events.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"일정을 찾을 수 없습니다."));if(!e.getOwner().getId().equals(u.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"내 일정만 수정할 수 있습니다.");return e;}
    private void validate(EventInput b){if(!b.endAt().isAfter(b.startAt()))throw new ApiException(HttpStatus.BAD_REQUEST,"종료 시간은 시작 시간보다 뒤여야 합니다.");if(b.visibility()==Event.Visibility.GROUP&&(b.groupIds()==null||b.groupIds().isEmpty()))throw new ApiException(HttpStatus.BAD_REQUEST,"공개할 그룹을 선택해주세요.");}
    private void apply(Event e,EventInput b,User u){
        e.setTitle(b.title());e.setDescription(b.description());e.setLocation(b.location());e.setStartAt(b.startAt());e.setEndAt(b.endAt());e.setAllDay(b.allDay());e.setVisibility(b.visibility());e.setRecurrenceRule(b.recurrenceRule());e.setColor(b.color()==null?"#168CF2":b.color());e.getGroups().clear();
        if(b.groupIds()!=null)for(Long id:b.groupIds()){if(!members.existsByGroupIdAndUserId(id,u.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"소속된 그룹에만 일정을 공개할 수 있습니다.");e.getGroups().add(groups.findById(id).orElseThrow());}
    }
    private boolean canView(Event e,User u,List<Long> groupIds){if(e.getOwner().getId().equals(u.getId()))return true;if(e.getVisibility()==Event.Visibility.PUBLIC)return true;if(e.getVisibility()==Event.Visibility.GROUP)return e.getGroups().stream().anyMatch(g->groupIds.contains(g.getId()));return false;}
    public static Map<String,Object> view(Event e,boolean details){
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",e.getId());m.put("title",details?e.getTitle():"바쁨");m.put("startAt",e.getStartAt());m.put("endAt",e.getEndAt());m.put("allDay",e.isAllDay());m.put("visibility",e.getVisibility());m.put("color",e.getColor());m.put("owner",AuthController.user(e.getOwner()));m.put("description",details&&e.getDescription()!=null?e.getDescription():"");m.put("location",details&&e.getLocation()!=null?e.getLocation():"");m.put("recurrenceRule",e.getRecurrenceRule()==null?"":e.getRecurrenceRule());m.put("publicToken",e.getPublicToken()==null?"":e.getPublicToken());m.put("groupIds",e.getGroups().stream().map(Group::getId).toList());return m;
    }
    public record EventRange(Long ownerId,OffsetDateTime startAt,OffsetDateTime endAt){}
    public static List<Map<String,Object>> occurrenceViews(Event e,OffsetDateTime from,OffsetDateTime to){return occurrenceRanges(e,from,to).stream().map(r->{Map<String,Object>m=new LinkedHashMap<>(view(e,true));m.put("startAt",r.startAt());m.put("endAt",r.endAt());m.put("instanceStart",r.startAt());return m;}).toList();}
    public static List<EventRange> occurrenceRanges(Event e,OffsetDateTime from,OffsetDateTime to){
        List<EventRange> out=new ArrayList<>();OffsetDateTime start=e.getStartAt(),end=e.getEndAt();String rule=e.getRecurrenceRule();if(rule==null||rule.isBlank()){if(start.isBefore(to)&&end.isAfter(from))out.add(new EventRange(e.getOwner().getId(),start,end));return out;}int guard=0;while(!end.isAfter(from)&&guard++<5000){start=next(start,rule);end=next(end,rule);}while(start.isBefore(to)&&guard++<5500){if(end.isAfter(from))out.add(new EventRange(e.getOwner().getId(),start,end));start=next(start,rule);end=next(end,rule);}return out;
    }
    private static OffsetDateTime next(OffsetDateTime value,String rule){if(rule.contains("FREQ=DAILY"))return value.plusDays(1);if(rule.contains("FREQ=MONTHLY"))return value.plusMonths(1);return value.plusWeeks(1);}
}
