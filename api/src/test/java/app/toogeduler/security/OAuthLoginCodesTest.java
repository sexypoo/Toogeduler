package app.toogeduler.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OAuthLoginCodesTest {
    private static final String VERIFIER="this-device-only-verifier-0123456789abcdefghij";

    @Test void codeIsExchangedOnceWithTheMatchingVerifier(){
        OAuthLoginCodes codes=new OAuthLoginCodes();
        String code=codes.issue(7L,OAuthLoginCodes.challengeOf(VERIFIER));
        assertEquals(Optional.of(7L),codes.redeem(code,VERIFIER));
        assertEquals(Optional.empty(),codes.redeem(code,VERIFIER),"같은 코드는 두 번 쓸 수 없다");
    }

    @Test void codeFromAnotherDeviceCannotBeRedeemed(){
        // 공격자가 자기 계정 코드를 딥링크로 보내도, 피해자 기기에는 공격자의 verifier 가 없다.
        OAuthLoginCodes codes=new OAuthLoginCodes();
        String attackerCode=codes.issue(99L,OAuthLoginCodes.challengeOf("attacker-verifier-0123456789abcdefghijklmn"));
        assertEquals(Optional.empty(),codes.redeem(attackerCode,VERIFIER));
    }

    @Test void wrongVerifierBurnsTheCode(){
        OAuthLoginCodes codes=new OAuthLoginCodes();
        String code=codes.issue(7L,OAuthLoginCodes.challengeOf(VERIFIER));
        assertEquals(Optional.empty(),codes.redeem(code,"guess"));
        assertEquals(Optional.empty(),codes.redeem(code,VERIFIER),"틀린 시도 뒤에는 맞는 verifier 로도 쓸 수 없다");
    }

    @Test void expiredCodeIsRejected(){
        MutableClock clock=new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        OAuthLoginCodes codes=new OAuthLoginCodes(clock);
        String code=codes.issue(7L,OAuthLoginCodes.challengeOf(VERIFIER));
        clock.now=clock.now.plus(OAuthLoginCodes.TTL);
        assertEquals(Optional.empty(),codes.redeem(code,VERIFIER));
    }

    @Test void challengeFormatIsValidated(){
        assertTrue(OAuthLoginCodes.isValidChallenge(OAuthLoginCodes.challengeOf(VERIFIER)));
        assertFalse(OAuthLoginCodes.isValidChallenge("short"));
        assertFalse(OAuthLoginCodes.isValidChallenge(null));
    }

    private static class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now){this.now=now;}
        @Override public ZoneOffset getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(java.time.ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
