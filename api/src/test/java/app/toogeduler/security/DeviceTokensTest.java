package app.toogeduler.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTokensTest {
    private static final String SECRET="test-secret-key-that-is-long-enough-for-hmac-sha256-0123456789";
    private final DeviceTokens tokens=new DeviceTokens(SECRET);

    @Test void issuedTokenVerifiesToItsOwner(){assertEquals(5L,tokens.verify(tokens.issue(5L)));}

    @Test void tamperedOrForeignTokensAreRejected(){
        String token=tokens.issue(5L);
        assertNull(tokens.verify("6"+token.substring(1)),"userId 를 바꾸면 서명이 맞지 않는다");
        assertNull(new DeviceTokens(SECRET+"-other").verify(token),"다른 서버 비밀값으로 만든 토큰은 받지 않는다");
        assertNull(tokens.verify("garbage"));
        assertNull(tokens.verify(null));
    }

    @Test void deviceTokenCannotBeUsedAsAnAccessToken(){
        // 기기 토큰은 다른 키로 서명하므로 API 인증(JWT)에 쓸 수 없다.
        assertThrows(RuntimeException.class,()->new JwtService(SECRET).parse(tokens.issue(5L)));
    }
}
