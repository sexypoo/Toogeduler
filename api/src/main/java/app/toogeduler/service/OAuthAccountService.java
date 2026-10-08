package app.toogeduler.service;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 소셜 로그인 결과를 Toogeduler 계정에 연결한다.
 *
 * 이메일만 같다고 기존 계정에 붙이면, 공격자가 피해자의 이메일로 미리 비밀번호 계정을 만들어 두었다가
 * 피해자가 소셜 로그인하는 순간 그 계정을 함께 쓰게 된다(계정 선점 공격).
 * 그래서 비밀번호로 가입된 계정에는 자동으로 연결하지 않고, 제공자가 인증한 이메일만 받는다.
 */
@Service @RequiredArgsConstructor
public class OAuthAccountService {
    private final UserRepository users;

    /** 로그인 화면이 사용자에게 보여줄 문구를 고르는 데 쓰는 실패 코드. */
    public static class SignInRejected extends RuntimeException{
        private final String code;
        public SignInRejected(String code){super(code);this.code=code;}
        public String code(){return code;}
    }

    /**
     * @param emailVerified 제공자가 이메일 소유를 확인했는지. 카카오 대체 이메일처럼 우리가 만든 주소는 true 로 넘긴다.
     */
    public User signIn(String provider,String email,boolean emailVerified,String name,String avatarUrl){
        if(email==null||email.isBlank())throw new SignInRejected("email");
        if(!emailVerified)throw new SignInRejected("email_unverified");
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        User user=users.findByEmailIgnoreCase(normalized).orElse(null);
        if(user==null){
            User created=new User(normalized,name,null);created.setProvider(provider);created.setAvatarUrl(avatarUrl);
            return users.save(created);
        }
        if(user.getPasswordHash()!=null)throw new SignInRejected("password_account");
        boolean changed=false;
        if((user.getAvatarUrl()==null||user.getAvatarUrl().isBlank())&&avatarUrl!=null){user.setAvatarUrl(avatarUrl);changed=true;}
        if(user.getFriendCode()==null||user.getFriendCode().isBlank()){user.setFriendCode(User.createFriendCode());changed=true;}
        return changed?users.save(user):user;
    }
}
