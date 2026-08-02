package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController @RequestMapping("/api/groups") @RequiredArgsConstructor
public class GroupController {
    private final GroupRepository groups; private final GroupMemberRepository members; private final EventRepository events;
    @Value("${app.web-url}") String webUrl;
    public record GroupInput(@NotBlank @Size(max=40)String name,String color){}
    @GetMapping List<Map<String,Object>> mine(Authentication auth){User u=ApiSupport.user(auth);return members.findByUserId(u.getId()).stream().map(m->view(m.getGroup())).toList();}
    @PostMapping @Transactional Map<String,Object> create(Authentication auth,@Valid @RequestBody GroupInput body){User u=ApiSupport.user(auth);Group g=new Group();g.setName(body.name());g.setColor(body.color()==null?"#19B7B1":body.color());g.setOwner(u);g.setInviteToken(randomToken());g=groups.save(g);GroupMember m=new GroupMember();m.setGroup(g);m.setUser(u);m.setRole(GroupMember.Role.OWNER);members.save(m);return view(g);}
    @PostMapping("/join/{token}") @Transactional Map<String,Object> join(Authentication auth,@PathVariable String token){User u=ApiSupport.user(auth);Group g=groups.findByInviteToken(token).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"유효하지 않은 초대 링크입니다."));if(!members.existsByGroupIdAndUserId(g.getId(),u.getId())){GroupMember m=new GroupMember();m.setGroup(g);m.setUser(u);members.save(m);}return view(g);}
    @PostMapping("/{id}/invite/refresh") Map<String,Object> refresh(Authentication auth,@PathVariable Long id){User u=ApiSupport.user(auth);Group g=memberGroup(id,u);g.setInviteToken(randomToken());groups.save(g);return Map.of("inviteUrl",webUrl+"/invite/"+g.getInviteToken());}
    @GetMapping("/{id}/events") List<Map<String,Object>> calendar(Authentication auth,@PathVariable Long id,@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to){User u=ApiSupport.user(auth);memberGroup(id,u);Map<Long,Event> visible=new LinkedHashMap<>();events.groupCalendar(id,from,to).forEach(e->visible.put(e.getId(),e));events.busyEvents(List.of(u.getId()),from,to).stream().filter(e->e.getOwner().getId().equals(u.getId())).forEach(e->visible.put(e.getId(),e));return visible.values().stream().flatMap(e->EventController.occurrenceViews(e,from,to).stream()).toList();}
    @DeleteMapping("/{id}/members/me") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT) void leave(Authentication auth,@PathVariable Long id){User u=ApiSupport.user(auth);Group g=memberGroup(id,u);if(g.getOwner().getId().equals(u.getId()))throw new ApiException(HttpStatus.CONFLICT,"그룹장을 다른 멤버에게 양도한 후 탈퇴할 수 있습니다.");members.delete(members.findByGroupIdAndUserId(id,u.getId()).orElseThrow());}
    @DeleteMapping("/{id}/members/{userId}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT) void remove(Authentication auth,@PathVariable Long id,@PathVariable Long userId){User u=ApiSupport.user(auth);Group g=ownerGroup(id,u);if(g.getOwner().getId().equals(userId))throw new ApiException(HttpStatus.CONFLICT,"그룹장은 내보낼 수 없습니다.");members.findByGroupIdAndUserId(id,userId).ifPresent(members::delete);}
    @PostMapping("/{id}/transfer/{userId}") @Transactional Map<String,Object> transfer(Authentication auth,@PathVariable Long id,@PathVariable Long userId){User u=ApiSupport.user(auth);Group g=ownerGroup(id,u);GroupMember next=members.findByGroupIdAndUserId(id,userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"그룹 멤버가 아닙니다."));GroupMember old=members.findByGroupIdAndUserId(id,u.getId()).orElseThrow();old.setRole(GroupMember.Role.MEMBER);next.setRole(GroupMember.Role.OWNER);g.setOwner(next.getUser());members.saveAll(List.of(old,next));return view(groups.save(g));}
    @DeleteMapping("/{id}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication auth,@PathVariable Long id){Group g=ownerGroup(id,ApiSupport.user(auth));members.deleteAll(members.findByGroupId(id));groups.delete(g);}
    private Map<String,Object> view(Group g){List<Map<String,Object>> people=members.findByGroupId(g.getId()).stream().map(m->{Map<String,Object>x=new LinkedHashMap<>(AuthController.user(m.getUser()));x.put("role",m.getRole());return x;}).toList();Map<String,Object> out=new LinkedHashMap<>();out.put("id",g.getId());out.put("name",g.getName());out.put("color",g.getColor());out.put("ownerId",g.getOwner().getId());out.put("members",people);out.put("inviteUrl",webUrl+"/invite/"+g.getInviteToken());return out;}
    private Group memberGroup(Long id,User u){Group g=groups.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"그룹을 찾을 수 없습니다."));if(!members.existsByGroupIdAndUserId(id,u.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"그룹 멤버만 볼 수 있습니다.");return g;}
    private Group ownerGroup(Long id,User u){Group g=memberGroup(id,u);if(!g.getOwner().getId().equals(u.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"그룹장만 할 수 있습니다.");return g;}
    private String randomToken(){return UUID.randomUUID().toString().replace("-","");}
}
