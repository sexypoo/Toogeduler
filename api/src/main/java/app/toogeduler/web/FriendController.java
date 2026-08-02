package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/friends") @RequiredArgsConstructor
public class FriendController {
    private final FriendshipRepository friendships; private final UserRepository users;
    @GetMapping List<Map<String,Object>> list(Authentication auth){User me=ApiSupport.user(auth);return friendships.findByRequesterIdOrReceiverId(me.getId(),me.getId()).stream().map(f->{User other=f.getRequester().getId().equals(me.getId())?f.getReceiver():f.getRequester();Map<String,Object>m=new LinkedHashMap<>(AuthController.user(other));m.put("requestId",f.getId());m.put("status",f.getStatus());m.put("incoming",f.getReceiver().getId().equals(me.getId()));return m;}).toList();}
    @PostMapping("/requests") Map<String,Object> request(Authentication auth,@RequestBody Map<String,String> body){User me=ApiSupport.user(auth);User target=users.findByEmailIgnoreCase(body.getOrDefault("email","")).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"해당 이메일의 사용자를 찾을 수 없습니다."));if(target.getId().equals(me.getId()))throw new ApiException(HttpStatus.BAD_REQUEST,"나에게 친구 요청을 보낼 수 없습니다.");Friendship f=new Friendship();f.setRequester(me);f.setReceiver(target);f=friendships.save(f);return Map.of("id",f.getId(),"status",f.getStatus());}
    @PostMapping("/requests/{id}/accept") Map<String,Object> accept(Authentication auth,@PathVariable Long id){User me=ApiSupport.user(auth);Friendship f=friendships.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"친구 요청을 찾을 수 없습니다."));if(!f.getReceiver().getId().equals(me.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"받은 요청만 수락할 수 있습니다.");f.setStatus(Friendship.Status.ACCEPTED);friendships.save(f);return Map.of("status",f.getStatus());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void remove(Authentication auth,@PathVariable Long id){User me=ApiSupport.user(auth);Friendship f=friendships.findById(id).orElseThrow();if(!f.getRequester().getId().equals(me.getId())&&!f.getReceiver().getId().equals(me.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"친구 관계를 삭제할 수 없습니다.");friendships.delete(f);}
}

