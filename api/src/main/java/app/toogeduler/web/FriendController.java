package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@RestController @RequestMapping("/api/friends") @RequiredArgsConstructor
public class FriendController {
    private final FriendshipRepository friendships; private final UserRepository users; private final EventRepository events; private final NotificationService notificationService;
    @GetMapping List<Map<String,Object>> list(Authentication auth){User me=ApiSupport.user(auth);return friendships.findByRequesterIdOrReceiverId(me.getId(),me.getId()).stream().map(f->{User other=f.getRequester().getId().equals(me.getId())?f.getReceiver():f.getRequester();Map<String,Object>m=new LinkedHashMap<>(AuthController.user(other));m.put("requestId",f.getId());m.put("status",f.getStatus());m.put("incoming",f.getReceiver().getId().equals(me.getId()));return m;}).toList();}
    @PostMapping("/requests") Map<String,Object> request(Authentication auth,@RequestBody Map<String,String> body){User me=ApiSupport.user(auth);String identifier=body.getOrDefault("identifier",body.getOrDefault("email","")).trim();User target=users.findByFriendCodeIgnoreCase(identifier).or(()->users.findByEmailIgnoreCase(identifier)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"이메일 또는 친구 코드에 해당하는 사용자를 찾을 수 없습니다."));if(target.getId().equals(me.getId()))throw new ApiException(HttpStatus.BAD_REQUEST,"나에게 친구 요청을 보낼 수 없습니다.");boolean exists=friendships.findByRequesterIdOrReceiverId(me.getId(),me.getId()).stream().anyMatch(f->f.getRequester().getId().equals(target.getId())||f.getReceiver().getId().equals(target.getId()));if(exists)throw new ApiException(HttpStatus.CONFLICT,"이미 친구이거나 요청을 보낸 사용자입니다.");Friendship f=new Friendship();f.setRequester(me);f.setReceiver(target);f=friendships.save(f);notificationService.notify(target,Notification.Type.FRIEND_REQUEST,"새 친구 요청",me.getName()+"님이 친구 요청을 보냈어요.","/?tab=friends","friend-request:"+f.getId());return Map.of("id",f.getId(),"status",f.getStatus());}
    @PostMapping("/requests/{id}/accept") Map<String,Object> accept(Authentication auth,@PathVariable Long id){User me=ApiSupport.user(auth);Friendship f=friendships.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"친구 요청을 찾을 수 없습니다."));if(!f.getReceiver().getId().equals(me.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"받은 요청만 수락할 수 있습니다.");f.setStatus(Friendship.Status.ACCEPTED);friendships.save(f);notificationService.notify(f.getRequester(),Notification.Type.FRIEND_ACCEPTED,"친구 요청 수락",me.getName()+"님과 친구가 되었어요.","/?tab=friends","friend-accepted:"+f.getId());return Map.of("status",f.getStatus());}
    @GetMapping("/{userId}") Map<String,Object> profile(Authentication auth,@PathVariable Long userId){Friendship friendship=acceptedFriendship(ApiSupport.user(auth),userId);User friend=friendship.getRequester().getId().equals(userId)?friendship.getRequester():friendship.getReceiver();Map<String,Object> out=new LinkedHashMap<>(AuthController.user(friend));out.put("friendSince",friendship.getCreatedAt());return out;}
    @GetMapping("/{userId}/events") List<Map<String,Object>> calendar(Authentication auth,@PathVariable Long userId,@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to){acceptedFriendship(ApiSupport.user(auth),userId);return events.friendCalendar(userId,from,to).stream().flatMap(event->EventController.occurrenceViews(event,from,to).stream()).toList();}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void remove(Authentication auth,@PathVariable Long id){User me=ApiSupport.user(auth);Friendship f=friendships.findById(id).orElseThrow();if(!f.getRequester().getId().equals(me.getId())&&!f.getReceiver().getId().equals(me.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"친구 관계를 삭제할 수 없습니다.");friendships.delete(f);}
    private Friendship acceptedFriendship(User me,Long userId){return friendships.findByRequesterIdOrReceiverId(me.getId(),me.getId()).stream().filter(f->f.getStatus()==Friendship.Status.ACCEPTED).filter(f->f.getRequester().getId().equals(userId)||f.getReceiver().getId().equals(userId)).findFirst().orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"친구가 된 사용자만 캘린더를 볼 수 있습니다."));}
}
