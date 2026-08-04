package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notifications;
    @GetMapping List<Map<String,Object>> list(Authentication auth){return notifications.findTop50ByUserIdOrderByCreatedAtDesc(ApiSupport.user(auth).getId()).stream().map(this::view).toList();}
    @GetMapping("/unread-count") Map<String,Long> unreadCount(Authentication auth){return Map.of("count",notifications.countByUserIdAndReadAtIsNull(ApiSupport.user(auth).getId()));}
    @PatchMapping("/{id}/read") @Transactional Map<String,Object> read(Authentication auth,@PathVariable Long id){Notification n=owned(id,ApiSupport.user(auth));if(n.getReadAt()==null)n.setReadAt(Instant.now());return view(notifications.save(n));}
    @PatchMapping("/read-all") @Transactional Map<String,Long> readAll(Authentication auth){Long userId=ApiSupport.user(auth).getId();List<Notification> list=notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId);Instant now=Instant.now();list.stream().filter(n->n.getReadAt()==null).forEach(n->n.setReadAt(now));notifications.saveAll(list);return Map.of("count",0L);}
    private Notification owned(Long id,User user){Notification n=notifications.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"알림을 찾을 수 없습니다."));if(!n.getUser().getId().equals(user.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"내 알림만 확인할 수 있습니다.");return n;}
    private Map<String,Object> view(Notification n){Map<String,Object> m=new LinkedHashMap<>();m.put("id",n.getId());m.put("type",n.getType());m.put("title",n.getTitle());m.put("message",n.getMessage());m.put("link",n.getLink());m.put("read",n.getReadAt()!=null);m.put("createdAt",n.getCreatedAt());return m;}
}
