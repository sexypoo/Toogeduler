package app.toogeduler.web;

import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import app.toogeduler.security.DeviceTokens;
import app.toogeduler.security.JwtService;
import app.toogeduler.security.OAuthLoginCodes;
import app.toogeduler.service.AccountService;
import app.toogeduler.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 공격자가 비밀번호를 일부러 틀려 계정을 잠가도, 주인은 전에 로그인했던 기기에서 계속 로그인할 수 있어야 한다. */
class AuthControllerLoginTest {
    private static final String SECRET="test-secret-key-that-is-long-enough-for-hmac-sha256-0123456789";
    private AuthController controller;
    private DeviceTokens deviceTokens;
    private User owner;

    @BeforeEach void setUp(){
        BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
        UserRepository users=mock(UserRepository.class);
        owner=new User("owner@example.com","주인",passwords.encode("correct-password"));owner.setId(1L);
        User other=new User("other@example.com","다른 사람",passwords.encode("whatever-pass"));other.setId(2L);
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        deviceTokens=new DeviceTokens(SECRET);
        controller=new AuthController(users,mock(EventRepository.class),mock(GroupMemberRepository.class),mock(FriendshipRepository.class),passwords,new JwtService(SECRET),mock(AccountService.class),new LoginAttemptService(),new OAuthLoginCodes(),deviceTokens);
    }

    @Test void ownerOnATrustedDeviceSignsInWhileTheAccountIsLocked(){
        String ownersDevice=(String)login("correct-password",null).get("deviceToken");
        lockOutByAttacker();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ApiException.class,()->login("correct-password",null)).status,"모르는 기기에서는 잠겨 있다");
        Map<String,Object> result=login("correct-password",ownersDevice);
        assertNotNull(result.get("token"));
        assertEquals(ownersDevice,result.get("deviceToken"));
    }

    @Test void anotherUsersDeviceTokenDoesNotBypassTheLock(){
        lockOutByAttacker();
        String attackersDevice=deviceTokens.issue(2L);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ApiException.class,()->login("correct-password",attackersDevice)).status);
    }

    @Test void trustedDeviceIsStillLimitedOnItsOwn(){
        String device=deviceTokens.issue(1L);
        for(int i=0;i<5;i++)assertThrows(ApiException.class,()->login("wrong-password",device));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ApiException.class,()->login("correct-password",device)).status);
    }

    @Test void unknownEmailIsRejectedWithTheSameMessage(){
        ApiException e=assertThrows(ApiException.class,()->controller.login(new AuthController.Login("nobody@example.com","whatever-pass",null)));
        assertEquals(HttpStatus.UNAUTHORIZED,e.status);
    }

    private void lockOutByAttacker(){for(int i=0;i<5;i++)assertThrows(ApiException.class,()->login("attacker-guess",null));}
    private Map<String,Object> login(String password,String device){return controller.login(new AuthController.Login("owner@example.com",password,device));}
}
