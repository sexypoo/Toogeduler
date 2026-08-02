package app.toogeduler.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${app.jwt-secret}") String secret){ key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
    public String create(Long userId){
        Instant now=Instant.now();
        return Jwts.builder().subject(userId.toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(30, java.time.temporal.ChronoUnit.DAYS))).signWith(key).compact();
    }
    public Long parse(String token){ return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}

