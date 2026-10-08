package app.toogeduler.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 로그인에 성공한 기기에 주는 "기기 토큰"(OWASP 의 device cookie 방식).
 *
 * 계정 단위 잠금만 있으면 누구나 남의 이메일로 비밀번호를 5번 틀려 그 계정을 잠글 수 있다.
 * 이전에 로그인했던 기기는 이 토큰을 함께 보내고, 그 기기의 실패는 따로 센다.
 * 그래서 공격자가 계정을 잠가도 원래 주인은 자기 기기에서 계속 로그인할 수 있다.
 *
 * 형식: {userId}.{random}.{HMAC}. 액세스 토큰(JWT)과 다른 키로 서명하므로 API 인증에는 쓸 수 없다.
 */
@Service
public class DeviceTokens {
    private final byte[] key;
    private final SecureRandom random=new SecureRandom();

    public DeviceTokens(@Value("${app.jwt-secret}") String secret){
        try{key=MessageDigest.getInstance("SHA-256").digest(("device-token:"+secret).getBytes(StandardCharsets.UTF_8));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }

    public String issue(Long userId){
        byte[] nonce=new byte[18];random.nextBytes(nonce);
        String payload=userId+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload+"."+sign(payload);
    }

    /** 서명이 맞으면 토큰 주인의 userId, 아니면 null. */
    public Long verify(String token){
        if(token==null)return null;
        int last=token.lastIndexOf('.');
        if(last<0)return null;
        String payload=token.substring(0,last);
        if(!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),token.substring(last+1).getBytes(StandardCharsets.US_ASCII)))return null;
        try{return Long.valueOf(payload.substring(0,payload.indexOf('.')));}catch(RuntimeException e){return null;}
    }

    private String sign(String payload){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
}
