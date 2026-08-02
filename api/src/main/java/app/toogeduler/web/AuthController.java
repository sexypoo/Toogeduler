package app.toogeduler.web;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import app.toogeduler.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt;
    public record Register(@Email String email,@Size(min=2,max=30)String name,@Size(min=8,max=72)String password){}
    public record Login(@Email String email,@NotBlank String password){}
    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody Register body){
        if(users.findByEmailIgnoreCase(body.email()).isPresent())throw new ApiException(HttpStatus.CONFLICT,"이미 가입된 이메일입니다.");
        User u=users.save(new User(body.email().toLowerCase(),body.name(),passwords.encode(body.password()))); return response(u);
    }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login body){
        User u=users.findByEmailIgnoreCase(body.email()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요."));
        if(u.getPasswordHash()==null||!passwords.matches(body.password(),u.getPasswordHash()))throw new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해주세요.");return response(u);
    }
    @GetMapping("/me") Map<String,Object> me(Authentication auth){return user(ApiSupport.user(auth));}
    private Map<String,Object> response(User u){return Map.of("token",jwt.create(u.getId()),"user",user(u));}
    static Map<String,Object> user(User u){return Map.of("id",u.getId(),"email",u.getEmail(),"name",u.getName(),"avatarUrl",u.getAvatarUrl()==null?"":u.getAvatarUrl());}
}
