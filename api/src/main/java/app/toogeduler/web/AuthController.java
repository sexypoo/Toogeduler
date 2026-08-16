package app.toogeduler.web;

import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
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
    private final UserRepository users; private final EventRepository events; private final GroupMemberRepository groupMembers; private final FriendshipRepository friendships; private final PasswordEncoder passwords; private final JwtService jwt;
    public record Register(@Email String email,@Size(min=2,max=30)String name,@Size(min=8,max=72)String password){}
    public record Login(@Email String email,@NotBlank String password){}
    public record ProfileUpdate(@NotBlank @Size(min=2,max=30)String name,@Size(max=255)String avatarUrl){}
    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody Register body){
        if(users.findByEmailIgnoreCase(body.email()).isPresent())throw new ApiException(HttpStatus.CONFLICT,"이미 가입된 이메일입니다.");
        User u=users.save(new User(body.email().toLowerCase(),body.name(),passwords.encode(body.password()))); return response(u);
    }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login body){
        User u=users.findByEmailIgnoreCase(body.email()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요."));
        if(u.getPasswordHash()==null||!passwords.matches(body.password(),u.getPasswordHash()))throw new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요.");return response(u);
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
    @GetMapping("/me/stats") Map<String,Object> stats(Authentication auth){
        User u=ApiSupport.user(auth);long friendCount=friendships.findByRequesterIdOrReceiverId(u.getId(),u.getId()).stream().filter(f->f.getStatus()==app.toogeduler.domain.Friendship.Status.ACCEPTED).count();
        return Map.of("eventCount",events.countByOwnerId(u.getId()),"groupCount",groupMembers.findByUserId(u.getId()).size(),"friendCount",friendCount);
    }
    private Map<String,Object> response(User u){u=ensureFriendCode(u);return Map.of("token",jwt.create(u.getId()),"user",user(u));}
    private User ensureFriendCode(User u){if(u.getFriendCode()==null||u.getFriendCode().isBlank()){u.setFriendCode(User.createFriendCode());return users.save(u);}return u;}
    static Map<String,Object> user(User u){Map<String,Object> out=new java.util.LinkedHashMap<>();out.put("id",u.getId());out.put("email",u.getEmail().endsWith("@oauth.toogeduler.local")?"카카오 계정":u.getEmail());out.put("name",u.getName());out.put("avatarUrl",u.getAvatarUrl()==null?"":u.getAvatarUrl());out.put("friendCode",u.getFriendCode()==null?"":u.getFriendCode());out.put("provider",u.getProvider());out.put("createdAt",u.getCreatedAt());return out;}
}
