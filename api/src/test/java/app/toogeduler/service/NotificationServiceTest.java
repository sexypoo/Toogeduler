package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test void duplicateNotificationIsNotCreated(){
        NotificationRepository notifications=mock(NotificationRepository.class);EventRepository events=mock(EventRepository.class);NotificationService service=new NotificationService(notifications,events);
        User user=new User("user@toogeduler.app","사용자","hash");user.setId(1L);when(notifications.existsByUserIdAndDedupeKey(1L,"same-key")).thenReturn(true);
        service.notify(user,Notification.Type.FRIEND_REQUEST,"친구 요청","새 요청","/","same-key");
        verify(notifications,never()).save(any());
    }
}
