package app.toogeduler.security;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import app.toogeduler.service.OAuthAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 소셜 로그인 성공 처리 전체: 시작 정보 → 계정 연결 → 일회용 코드 → 교환까지. */
class OAuthLoginHandlerTest {
    private static final String VERIFIER="device-verifier-0123456789abcdefghijklmnopq";
    private UserRepository users;
    private OAuthLoginCodes codes;
    private OAuthLoginHandler handler;

    @BeforeEach void setUp(){
        users=mock(UserRepository.class);
        when(users.save(any(User.class))).thenAnswer(i->{User u=i.getArgument(0);if(u.getId()==null)u.setId(42L);return u;});
        codes=new OAuthLoginCodes();
        handler=new OAuthLoginHandler(new OAuthAccountService(users),codes,"https://app.example.com","toogeduler://auth/callback");
    }

    @Test void webLoginRedirectsWithACodeOnlyThisDeviceCanRedeem()throws Exception{
        when(users.findByEmailIgnoreCase("new@gmail.com")).thenReturn(Optional.empty());
        MockHttpServletResponse res=succeed(started(false),google("new@gmail.com",true));
        String location=res.getRedirectedUrl();
        assertTrue(location.startsWith("https://app.example.com/auth/callback?code="),location);
        assertFalse(location.contains("token"),"JWT 가 주소에 실리면 안 된다");
        String code=location.substring(location.indexOf("code=")+5);
        assertEquals(Optional.of(42L),codes.redeem(code,VERIFIER));
    }

    @Test void mobileLoginReturnsToTheAppScheme()throws Exception{
        when(users.findByEmailIgnoreCase("new@gmail.com")).thenReturn(Optional.empty());
        assertTrue(succeed(started(true),google("new@gmail.com",true)).getRedirectedUrl().startsWith("toogeduler://auth/callback?code="));
    }

    @Test void loginThatSkippedTheStartEndpointIsRejected()throws Exception{
        // 공격자가 /oauth2/authorization/google 로 바로 들어오게 만든 경우: 묶을 challenge 가 없다.
        MockHttpServletResponse res=succeed(new MockHttpServletRequest(),google("new@gmail.com",true));
        assertEquals("https://app.example.com/auth/callback?oauthError=state",res.getRedirectedUrl());
        verify(users,never()).save(any());
    }

    @Test void googleAccountDoesNotTakeOverAPasswordAccount()throws Exception{
        when(users.findByEmailIgnoreCase("victim@gmail.com")).thenReturn(Optional.of(new User("victim@gmail.com","선점","bcrypt-hash")));
        assertEquals("https://app.example.com/auth/callback?oauthError=password_account",succeed(started(false),google("victim@gmail.com",true)).getRedirectedUrl());
    }

    @Test void unverifiedGoogleEmailIsRejected()throws Exception{
        assertEquals("https://app.example.com/auth/callback?oauthError=email_unverified",succeed(started(false),google("someone@gmail.com",false)).getRedirectedUrl());
    }

    @Test void kakaoWithoutEmailUsesItsMemberId()throws Exception{
        when(users.findByEmailIgnoreCase("kakao-777@oauth.toogeduler.local")).thenReturn(Optional.empty());
        DefaultOAuth2User kakao=new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES,Map.of("id",777L,"kakao_account",Map.of("profile",Map.of("nickname","카카오친구"))),"id");
        OAuth2AuthenticationToken auth=new OAuth2AuthenticationToken(kakao,AuthorityUtils.NO_AUTHORITIES,"kakao");
        MockHttpServletResponse res=new MockHttpServletResponse();handler.onAuthenticationSuccess(started(false),res,auth);
        assertTrue(res.getRedirectedUrl().contains("?code="));
        verify(users).save(argThat(u->u.getEmail().equals("kakao-777@oauth.toogeduler.local")&&u.getName().equals("카카오친구")));
    }

    @Test void startInfoIsConsumedSoItCannotBeReplayed()throws Exception{
        when(users.findByEmailIgnoreCase("new@gmail.com")).thenReturn(Optional.empty());
        MockHttpServletRequest req=started(false);
        succeed(req,google("new@gmail.com",true));
        assertNull(new MockHttpServletRequest(){{setSession(req.getSession());}}.getSession().getAttribute("oauth_challenge"));
    }

    private MockHttpServletRequest started(boolean mobile){
        MockHttpServletRequest req=new MockHttpServletRequest();MockHttpSession session=new MockHttpSession();
        OAuthStart.save(session,OAuthLoginCodes.challengeOf(VERIFIER),mobile);req.setSession(session);return req;
    }
    private OAuth2AuthenticationToken google(String email,boolean verified){
        DefaultOAuth2User user=new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES,Map.of("sub","g-1","email",email,"email_verified",verified,"name","구글 사용자"),"sub");
        return new OAuth2AuthenticationToken(user,AuthorityUtils.NO_AUTHORITIES,"google");
    }
    private MockHttpServletResponse succeed(MockHttpServletRequest req,OAuth2AuthenticationToken auth)throws Exception{
        MockHttpServletResponse res=new MockHttpServletResponse();handler.onAuthenticationSuccess(req,res,auth);return res;
    }
}
