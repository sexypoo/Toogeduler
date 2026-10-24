package app.toogeduler.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각을 주입받아 쓰는 서비스가 테스트에서 고정 시각으로 바꿔 끼울 수 있게 한다. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
