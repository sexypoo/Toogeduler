package app.toogeduler.web;

import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
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
    private final UserRepository users; private final EventRepository events; private final GroupMemberRepository groupMembers; private final FriendshipRepository friendships; private final PasswordEncoder passwords; private final JwtService jwt; private final AccountService accounts; private final LoginAttemptService loginAttempts;
    public record Register(@Email String email,@Size(min=2,max=30)String name,@Size(min=8,max=72)String password){}
    public record Login(@Email String email,@NotBlank String password){}
    public record ProfileUpdate(@NotBlank @Size(min=2,max=30)String name,@Size(max=255)String avatarUrl){}
    public record AccountDelete(String password){}
    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody Register body){
        if(users.findByEmailIgnoreCase(body.email()).isPresent())throw new ApiException(HttpStatus.CONFLICT,"이미 가입된 이메일입니다.");
        User u=users.save(new User(body.email().toLowerCase(),body.name(),passwords.encode(body.password()))); return response(u);
    }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login body){
        // 계정 단위로만 잠근다. 이 API 는 Vercel 프록시를 거쳐 들어오므로 클라이언트 IP 를
        // 신뢰할 수 없다(X-Forwarded-For 는 위조 가능하고, 우측 값은 모든 웹 사용자가 공유한다).
        // IP 단위 제한이 필요하면 Vercel·Railway 엣지에서 설정한다.
        String emailKey="email:"+body.email();
        long locked=loginAttempts.lockedSecondsRemaining(emailKey);
        if(locked>0)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"로그인 시도가 너무 많습니다. "+((locked/60)+1)+"분 후에 다시 시도해주세요.");
        User u=users.findByEmailIgnoreCase(body.email()).orElse(null);
        if(u==null||u.getPasswordHash()==null||!passwords.matches(body.password(),u.getPasswordHash())){
            loginAttempts.recordFailure(emailKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요.");
        }
        loginAttempts.recordSuccess(emailKey);
        return response(u);
    }
    @GetMapping("/mobile-oauth/{provider}") void mobileOauth(@PathVariable String provider,HttpSession session,HttpServletResponse response)throws IOException{
        if(!provider.equals("google")&&!provider.equals("kakao"))throw new ApiException(HttpStatus.NOT_FOUND,"지원하지 않는 소셜 로그인입니다.");
        session.setAttribute("mobile_oauth",true);response.sendRedirect("/oauth2/authorization/"+provider);
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
    private User ensureFriendCode(User u){if(u.getFriendCode()==null||u.getFriendCode().isBlank()){u.setFriendCode(User.createFriendCode());return users.save(u);}return u;}
    static Map<String,Object> user(User u){Map<String,Object> out=new java.util.LinkedHashMap<>();out.put("id",u.getId());out.put("email",u.getEmail().endsWith("@oauth.toogeduler.local")?"카카오 계정":u.getEmail());out.put("name",u.getName());out.put("avatarUrl",u.getAvatarUrl()==null?"":u.getAvatarUrl());out.put("friendCode",u.getFriendCode()==null?"":u.getFriendCode());out.put("provider",u.getProvider());out.put("createdAt",u.getCreatedAt());return out;}
}
