package app.toogeduler.service;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OAuthAccountServiceTest {
    private UserRepository users;
    private OAuthAccountService service;

    @BeforeEach void setUp(){
        users=mock(UserRepository.class);
        when(users.save(any(User.class))).thenAnswer(i->i.getArgument(0));
        service=new OAuthAccountService(users);
    }

    @Test void passwordAccountWithSameEmailIsNotTakenOver(){
        // 공격자가 피해자 이메일로 먼저 가입해 둔 비밀번호 계정
        User preRegistered=new User("victim@gmail.com","공격자","bcrypt-hash");
        when(users.findByEmailIgnoreCase("victim@gmail.com")).thenReturn(Optional.of(preRegistered));
        OAuthAccountService.SignInRejected rejected=assertThrows(OAuthAccountService.SignInRejected.class,
            ()->service.signIn("GOOGLE","Victim@Gmail.com",true,"피해자",null));
        assertEquals("password_account",rejected.code());
    }

    @Test void unverifiedEmailIsRejected(){
        OAuthAccountService.SignInRejected rejected=assertThrows(OAuthAccountService.SignInRejected.class,
            ()->service.signIn("GOOGLE","someone@example.com",false,"누군가",null));
        assertEquals("email_unverified",rejected.code());
        verify(users,never()).save(any());
    }

    @Test void missingEmailIsRejected(){
        assertEquals("email",assertThrows(OAuthAccountService.SignInRejected.class,()->service.signIn("GOOGLE"," ",true,"이름",null)).code());
    }

    @Test void newUserIsCreatedWithNormalizedEmail(){
        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        User created=service.signIn("GOOGLE"," New@Example.com ",true,"새 사람","https://img");
        assertEquals("new@example.com",created.getEmail());
        assertEquals("GOOGLE",created.getProvider());
        assertNull(created.getPasswordHash());
    }

    @Test void existingSocialAccountSignsInAgain(){
        User social=new User("kakao-1@oauth.toogeduler.local","카카오",null);social.setProvider("KAKAO");social.setId(3L);
        when(users.findByEmailIgnoreCase("kakao-1@oauth.toogeduler.local")).thenReturn(Optional.of(social));
        assertEquals(3L,service.signIn("KAKAO","kakao-1@oauth.toogeduler.local",true,"카카오",null).getId());
    }
}
