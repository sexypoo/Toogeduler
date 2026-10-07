package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.web.EventController;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final EventRepository events;

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

    private String reminderText(int minutes){if(minutes==1440)return "내일 시작하는 일정입니다.";if(minutes>=60)return (minutes/60)+"시간 뒤에 시작합니다.";return minutes+"분 뒤에 시작합니다.";}
}
