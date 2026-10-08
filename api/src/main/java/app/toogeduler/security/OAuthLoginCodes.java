package app.toogeduler.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 소셜 로그인이 끝나면 JWT 대신 짧게 사는 일회용 코드를 리다이렉트 주소에 싣는다.
 * 코드는 로그인을 시작한 기기가 만든 challenge(= SHA-256(verifier))에 묶여 있어서,
 * 그 기기에 남아 있는 verifier 를 함께 보내야만 토큰으로 바꿀 수 있다(PKCE 와 같은 방식).
 * 덕분에 토큰이 브라우저 기록에 남지 않고, 다른 사람의 코드가 담긴 딥링크로 로그인을 강제할 수도 없다.
 *
 * 코드는 메모리에만 보관한다. API 인스턴스가 하나라는 전제이며, 여러 대로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Service
public class OAuthLoginCodes {
    static final Duration TTL=Duration.ofMinutes(2);
    private static final int MAX_PENDING=10_000;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Pending> pending=new ConcurrentHashMap<>();
    private final Clock clock;

    private record Pending(Long userId,String challenge,Instant expiresAt){}

    public OAuthLoginCodes(){this(Clock.systemUTC());}
    OAuthLoginCodes(Clock clock){this.clock=clock;}

    public String issue(Long userId,String challenge){
        evictExpired();
        if(pending.size()>=MAX_PENDING)throw new IllegalStateException("too many pending OAuth codes");
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(code,new Pending(userId,challenge,clock.instant().plus(TTL)));
        return code;
    }

    /** 코드는 한 번만 쓸 수 있다. verifier 가 틀려도 코드를 버려서 대입 시도를 막는다. */
    public Optional<Long> redeem(String code,String verifier){
        if(code==null||verifier==null)return Optional.empty();
        Pending p=pending.remove(code);
        if(p==null||!p.expiresAt().isAfter(clock.instant()))return Optional.empty();
        boolean matches=MessageDigest.isEqual(p.challenge().getBytes(StandardCharsets.US_ASCII),challengeOf(verifier).getBytes(StandardCharsets.US_ASCII));
        return matches?Optional.of(p.userId()):Optional.empty();
    }

    /** base64url(SHA-256(verifier)), 패딩 없음. 프론트의 계산과 같아야 한다. */
    public static String challengeOf(String verifier){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    /** SHA-256 을 base64url 로 인코딩하면 항상 43자다. */
    public static boolean isValidChallenge(String challenge){return challenge!=null&&challenge.matches("[A-Za-z0-9_-]{43}");}

    private void evictExpired(){Instant now=clock.instant();pending.values().removeIf(p->!p.expiresAt().isAfter(now));}
}
