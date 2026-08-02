package app.toogeduler.config;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;

@Configuration
public class DemoData {
    @Bean CommandLineRunner seed(@Value("${app.seed-demo}")boolean enabled,UserRepository users,GroupRepository groups,GroupMemberRepository members,EventRepository events,PasswordEncoder encoder){return args->{if(!enabled||users.count()>0)return;User minji=users.save(new User("minji@toogeduler.app","김민지",encoder.encode("password123!")));User jun=users.save(new User("jun@toogeduler.app","이준",encoder.encode("password123!")));User sora=users.save(new User("sora@toogeduler.app","박소라",encoder.encode("password123!")));Group g=new Group();g.setName("주말 탐험대");g.setColor("#19B7B1");g.setOwner(minji);g.setInviteToken(UUID.randomUUID().toString().replace("-",""));g=groups.save(g);for(User u:List.of(minji,jun,sora)){GroupMember m=new GroupMember();m.setGroup(g);m.setUser(u);m.setRole(u==minji?GroupMember.Role.OWNER:GroupMember.Role.MEMBER);members.save(m);}ZoneId zone=ZoneId.of("Asia/Seoul");LocalDate monday=LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY);seedEvent(events,minji,g,"디자인 리뷰",monday.plusDays(1),10,0,11,30,"#168CF2");seedEvent(events,jun,g,"클라이밍",monday.plusDays(2),19,0,21,0,"#8B6FF7");seedEvent(events,sora,g,"병원 예약",monday.plusDays(3),14,0,15,0,"#F0648C");seedEvent(events,minji,g,"성수 브런치",monday.plusDays(5),11,0,12,30,"#19B7B1");};}
    private void seedEvent(EventRepository repo,User owner,Group group,String title,LocalDate date,int sh,int sm,int eh,int em,String color){ZoneId z=ZoneId.of("Asia/Seoul");Event e=new Event();e.setOwner(owner);e.setTitle(title);e.setStartAt(date.atTime(sh,sm).atZone(z).toOffsetDateTime());e.setEndAt(date.atTime(eh,em).atZone(z).toOffsetDateTime());e.setVisibility(Event.Visibility.GROUP);e.setColor(color);e.setPublicToken(UUID.randomUUID().toString());e.getGroups().add(group);repo.save(e);}
}
