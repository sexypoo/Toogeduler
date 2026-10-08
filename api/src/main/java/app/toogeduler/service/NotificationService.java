package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.web.EventController;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final EventRepository events;
    private final GroupMemberRepository members;
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm",Locale.KOREAN);

    @Transactional
    public void notify(User user,Notification.Type type,String title,String message,String link,String dedupeKey){
        if(notifications.existsByUserIdAndDedupeKey(user.getId(),dedupeKey))return;
        Notification n=new Notification();n.setUser(user);n.setType(type);n.setTitle(title);n.setMessage(message);n.setLink(link);n.setDedupeKey(dedupeKey);notifications.save(n);
    }

    @Scheduled(fixedDelay=60000,initialDelay=10000)
    @Transactional
    public void createDueEventReminders(){
        OffsetDateTime now=OffsetDateTime.now();
        OffsetDateTime until=now.plusDays(1);
        for(Event event:events.reminderCandidates(now,until)){
            Integer minutes=event.getReminderMinutes();if(minutes==null||minutes<=0)continue;
            for(EventController.EventRange occurrence:EventController.occurrenceRanges(event,now,until)){
                OffsetDateTime remindAt=occurrence.startAt().minusMinutes(minutes);
                if(remindAt.isAfter(now)||!occurrence.startAt().isAfter(now))continue;
                String key="event-reminder:"+event.getId()+":"+occurrence.startAt();
                notify(event.getOwner(),Notification.Type.EVENT_REMINDER,event.getTitle()+" 일정이 곧 시작해요",reminderText(minutes),"/?event="+event.getId()+"&at="+occurrence.startAt().toInstant(),key);
            }
        }
    }

    /**
     * 그룹에 공유한 일정을 만들며 "그룹 멤버에게 알리기"를 고른 경우, 그 그룹의 다른 멤버에게 알린다.
     * 여러 그룹에 함께 공유해도 한 사람에게는 한 번만 보낸다. 알림을 누르면 그룹 캘린더의 그 날짜로 간다.
     */
    @Transactional
    public void announceGroupEvent(Event event){
        User owner=event.getOwner();String when=event.getStartAt().atZoneSameInstant(SEOUL).format(WHEN);
        Set<Long> notified=new HashSet<>();notified.add(owner.getId());
        for(Group group:event.getGroups())for(GroupMember member:members.findByGroupId(group.getId())){
            User user=member.getUser();if(!notified.add(user.getId()))continue;
            notify(user,Notification.Type.GROUP_EVENT,"새 그룹 일정이 잡혔어요",owner.getName()+"님이 "+group.getName()+"에 "+when+" '"+event.getTitle()+"' 일정을 잡았어요.","/?group="+group.getId()+"&at="+event.getStartAt().toInstant(),"group-event:"+event.getId()+":"+user.getId());
        }
    }

    private String reminderText(int minutes){if(minutes==1440)return "내일 시작하는 일정입니다.";if(minutes>=60)return (minutes/60)+"시간 뒤에 시작합니다.";return minutes+"분 뒤에 시작합니다.";}
}
