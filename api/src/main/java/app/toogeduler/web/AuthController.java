package app.toogeduler.web;

import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import app.toogeduler.security.DeviceTokens;
import app.toogeduler.security.OAuthLoginCodes;
import app.toogeduler.security.OAuthStart;
import app.toogeduler.service.AccountService;
import app.toogeduler.service.LoginAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users; private final EventRepository events; private final GroupMemberRepository groupMembers; private final FriendshipRepository friendships; private final PasswordEncoder passwords; private final JwtService jwt; private final AccountService accounts; private final LoginAttemptService loginAttempts; private final OAuthLoginCodes loginCodes; private final DeviceTokens deviceTokens;
    /** "timing-only" 의 BCrypt 해시. 없는 계정에 대한 비밀번호 비교에만 쓴다. */
    private static final String TIMING_HASH=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("timing-only");
    public record Register(@NotBlank(message="이메일을 입력해주세요.") @Email(message="이메일 형식이 올바르지 않습니다.") @Size(max=255)String email,@NotBlank(message="이름을 입력해주세요.") @Size(min=2,max=30,message="이름은 2~30자로 입력해주세요.")String name,@NotNull(message="비밀번호를 입력해주세요.") @Size(min=8,max=72,message="비밀번호는 8~72자로 입력해주세요.")String password){}
    public record Login(@NotBlank(message="이메일을 입력해주세요.") @Email(message="이메일 형식이 올바르지 않습니다.")String email,@NotBlank(message="비밀번호를 입력해주세요.") @Size(max=72)String password,@Size(max=200)String deviceToken){}
    public record ProfileUpdate(@NotBlank @Size(min=2,max=30)String name,@Size(max=255)String avatarUrl){}
    public record AccountDelete(String password){}
    public record OAuthExchange(@NotBlank String code,@NotBlank String verifier){}
    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody Register body){
        if(users.findByEmailIgnoreCase(body.email()).isPresent())throw new ApiException(HttpStatus.CONFLICT,"이미 가입된 이메일입니다.");
        User u=users.save(new User(body.email().toLowerCase(),body.name(),passwords.encode(body.password()))); return response(u,deviceTokens.issue(u.getId()));
    }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login body){
        // IP 는 믿을 수 없다(Vercel 프록시를 거치고 X-Forwarded-For 는 위조 가능). 그래서 계정 단위로 잠그되,
        // 이 계정으로 로그인했던 기기(기기 토큰)의 실패는 따로 센다. 공격자가 일부러 계정을 잠가도
        // 주인은 자기 기기에서 계속 로그인할 수 있다. 여러 계정을 돌며 시도하는 공격은 엣지에서 IP 로 제한한다.
        User u=users.findByEmailIgnoreCase(body.email()).orElse(null);
        Long deviceOwner=deviceTokens.verify(body.deviceToken());
        boolean trustedDevice=u!=null&&u.getId().equals(deviceOwner);
        String attemptKey=trustedDevice?"device:"+body.deviceToken():"email:"+body.email();
        long locked=loginAttempts.lockedSecondsRemaining(attemptKey);
        if(locked>0)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"로그인 시도가 너무 많습니다. "+((locked/60)+1)+"분 후에 다시 시도해주세요.");
        // 없는 계정도 같은 시간이 걸리도록 비밀번호 비교를 한 번 한다(응답 시간으로 가입 여부를 알 수 없게).
        String hash=u==null||u.getPasswordHash()==null?TIMING_HASH:u.getPasswordHash();
        boolean matches=passwords.matches(body.password(),hash);
        if(u==null||u.getPasswordHash()==null||!matches){
            loginAttempts.recordFailure(attemptKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요.");
        }
        loginAttempts.recordSuccess(attemptKey);
        return response(u,trustedDevice?body.deviceToken():deviceTokens.issue(u.getId()));
    }
    /**
     * 소셜 로그인 시작점. 웹과 앱 모두 이 주소로 들어와야 한다.
     * challenge 는 클라이언트가 만든 verifier 의 SHA-256 값이며, 로그인이 끝나면 일회용 코드가 여기에 묶인다.
     */
    @GetMapping("/oauth/{provider}") void startOauth(@PathVariable String provider,@RequestParam String challenge,@RequestParam(defaultValue="web") String client,HttpSession session,HttpServletResponse response)throws IOException{
        if(!provider.equals("google")&&!provider.equals("kakao"))throw new ApiException(HttpStatus.NOT_FOUND,"지원하지 않는 소셜 로그인입니다.");
        if(!OAuthLoginCodes.isValidChallenge(challenge))throw new ApiException(HttpStatus.BAD_REQUEST,"로그인 요청이 올바르지 않습니다.");
        OAuthStart.save(session,challenge,client.equals("mobile"));response.sendRedirect("/oauth2/authorization/"+provider);
    }
    @PostMapping("/oauth/exchange") Map<String,Object> exchangeOauth(@Valid @RequestBody OAuthExchange body){
        Long userId=loginCodes.redeem(body.code(),body.verifier()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"로그인 링크가 만료되었거나 올바르지 않아요. 다시 로그인해주세요."));
        return response(users.findById(userId).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"계정을 찾을 수 없습니다.")));
    }
    @GetMapping("/me") Map<String,Object> me(Authentication auth){return user(ensureFriendCode(ApiSupport.user(auth)));}
    @PatchMapping("/me") Map<String,Object> updateMe(Authentication auth,@Valid @RequestBody ProfileUpdate body){
        User u=ApiSupport.user(auth);String avatar=body.avatarUrl()==null?null:body.avatarUrl().trim();
        if(avatar!=null&&!avatar.isEmpty()&&!avatar.matches("https?://.+"))throw new ApiException(HttpStatus.BAD_REQUEST,"프로필 이미지 주소는 http 또는 https로 시작해야 합니다.");
        u.setName(body.name().trim());u.setAvatarUrl(avatar==null||avatar.isEmpty()?null:avatar);if(u.getFriendCode()==null)u.setFriendCode(User.createFriendCode());return user(users.save(u));
    }
    /**
     * 회원 탈퇴. App Store 가이드라인 5.1.1(v)에 따라 앱 안에서 계정을 완전히 삭제할 수 있어야 한다.
     * 이메일 계정은 오조작을 막기 위해 비밀번호를 다시 확인한다.
     */
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteMe(Authentication auth,@RequestBody(required=false) AccountDelete body){
        User u=ApiSupport.user(auth);
        if(u.getPasswordHash()!=null){
            String password=body==null?null:body.password();
            if(password==null||password.isBlank()||!passwords.matches(password,u.getPasswordHash()))throw new ApiException(HttpStatus.UNAUTHORIZED,"비밀번호를 확인해주세요.");
        }
        accounts.deleteAccount(u.getId());
    }
    @GetMapping("/me/stats") Map<String,Object> stats(Authentication auth){
        User u=ApiSupport.user(auth);long friendCount=friendships.findByRequesterIdOrReceiverId(u.getId(),u.getId()).stream().filter(f->f.getStatus()==app.toogeduler.domain.Friendship.Status.ACCEPTED).count();
        return Map.of("eventCount",events.countByOwnerId(u.getId()),"groupCount",groupMembers.findByUserId(u.getId()).size(),"friendCount",friendCount);
    }
    private Map<String,Object> response(User u){u=ensureFriendCode(u);return Map.of("token",jwt.create(u.getId()),"user",user(u));}
    private Map<String,Object> response(User u,String deviceToken){Map<String,Object> out=new java.util.LinkedHashMap<>(response(u));out.put("deviceToken",deviceToken);return out;}
    private User ensureFriendCode(User u){if(u.getFriendCode()==null||u.getFriendCode().isBlank()){u.setFriendCode(User.createFriendCode());return users.save(u);}return u;}
    /** 다른 사람에게 보이는 최소 정보. 이메일·친구 코드는 담지 않는다(공개 일정 API 는 로그인 없이 열려 있다). */
    static Map<String,Object> publicUser(User u){Map<String,Object> out=new java.util.LinkedHashMap<>();out.put("id",u.getId());out.put("name",u.getName());out.put("avatarUrl",u.getAvatarUrl()==null?"":u.getAvatarUrl());return out;}
    static Map<String,Object> user(User u){Map<String,Object> out=new java.util.LinkedHashMap<>();out.put("id",u.getId());out.put("email",u.getEmail().endsWith("@oauth.toogeduler.local")?"카카오 계정":u.getEmail());out.put("name",u.getName());out.put("avatarUrl",u.getAvatarUrl()==null?"":u.getAvatarUrl());out.put("friendCode",u.getFriendCode()==null?"":u.getFriendCode());out.put("provider",u.getProvider());out.put("createdAt",u.getCreatedAt());return out;}
}
