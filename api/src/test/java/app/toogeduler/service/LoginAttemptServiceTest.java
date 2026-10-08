package app.toogeduler.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {
    @Test void fiveFailuresLockTheKey(){
        LoginAttemptService attempts=new LoginAttemptService();
        for(int i=0;i<5;i++)attempts.recordFailure("email:victim@example.com");
        assertTrue(attempts.lockedSecondsRemaining("email:victim@example.com")>0);
        assertEquals(0,attempts.lockedSecondsRemaining("device:other"));
    }

    @Test void floodingRandomKeysDoesNotEvictAnActiveLock(){
        // 아무 이메일이나 대량으로 넣어 상한을 넘겨도 피해자 계정의 잠금은 남아 있어야 한다.
        LoginAttemptService attempts=new LoginAttemptService();
        for(int i=0;i<5;i++)attempts.recordFailure("email:victim@example.com");
        for(int i=0;i<110_000;i++)attempts.recordFailure("email:"+UUID.randomUUID()+"@flood.test");
        assertTrue(attempts.lockedSecondsRemaining("email:victim@example.com")>0);
    }
}
