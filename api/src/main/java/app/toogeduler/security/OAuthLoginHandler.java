package app.toogeduler.security;

import app.toogeduler.domain.User;
import app.toogeduler.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 소셜 로그인이 끝났을 때(성공·실패 모두) 로그인을 시작한 곳(웹 또는 앱)의 /auth/callback 으로 돌려보낸다.
 * 성공하면 토큰 대신 일회용 코드를 싣고, 콜백 페이지가 verifier 와 함께 POST /api/auth/oauth/exchange 로 토큰과 바꾼다.
 */
@Component @Slf4j
public class OAuthLoginHandler implements AuthenticationSuccessHandler,AuthenticationFailureHandler {
    private final OAuthAccountService accounts; private final OAuthLoginCodes codes; private final String webUrl; private final String mobileUrl;

    public OAuthLoginHandler(OAuthAccountService accounts,OAuthLoginCodes codes,@Value("${app.web-url}")String webUrl,@Value("${app.mobile-url}")String mobileUrl){
        this.accounts=accounts;this.codes=codes;this.webUrl=webUrl;this.mobileUrl=mobileUrl;
    }

    @Override public void onAuthenticationSuccess(HttpServletRequest req,HttpServletResponse res,Authentication auth)throws IOException{
        OAuthStart start=OAuthStart.take(req);String target=callback(start);
        // /api/auth/oauth/{provider} 를 거치지 않고 들어온 로그인은 묶을 challenge 가 없으므로 받지 않는다.
        if(start.challenge()==null){res.sendRedirect(target+"?oauthError=state");return;}
        OAuth2User oauth=(OAuth2User)auth.getPrincipal();
        String provider=auth instanceof OAuth2AuthenticationToken token?token.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT):"OAUTH";
        String email=email(oauth);boolean verified=emailVerified(oauth,provider);
        // 카카오가 이메일을 주지 않으면 카카오 회원번호로 만든 주소를 쓴다. 우리만 만들 수 있는 주소라 인증된 것으로 본다.
        if((email==null||email.isBlank())&&provider.equals("KAKAO")){email=kakaoFallbackEmail(oauth);verified=true;}
        try{
            User user=accounts.signIn(provider,email,verified,name(oauth),avatar(oauth));
            res.sendRedirect(target+"?code="+codes.issue(user.getId(),start.challenge()));
        }catch(OAuthAccountService.SignInRejected rejected){
            log.info("OAuth sign-in rejected: {}",rejected.code());
            res.sendRedirect(target+"?oauthError="+rejected.code());
        }
    }

    @Override public void onAuthenticationFailure(HttpServletRequest req,HttpServletResponse res,AuthenticationException error)throws IOException{
        String code=error instanceof OAuth2AuthenticationException oauth?oauth.getError().getErrorCode():"oauth_failed";log.warn("OAuth login failed: {}",code);
        res.sendRedirect(callback(OAuthStart.take(req))+"?oauthError="+URLEncoder.encode(code,StandardCharsets.UTF_8));
    }

    private String callback(OAuthStart start){return start.mobile()?mobileUrl:webUrl+"/auth/callback";}

    @SuppressWarnings("unchecked")
    private static Map<String,Object> kakaoAccount(OAuth2User oauth){return (Map<String,Object>)oauth.getAttribute("kakao_account");}
    @SuppressWarnings("unchecked")
    private static Map<String,Object> kakaoProfile(OAuth2User oauth){Map<String,Object> account=kakaoAccount(oauth);return account==null?null:(Map<String,Object>)account.get("profile");}
    private static String email(OAuth2User oauth){String direct=oauth.getAttribute("email");if(direct!=null)return direct;Map<String,Object> account=kakaoAccount(oauth);return account==null?null:(String)account.get("email");}
    // 구글은 email_verified, 카카오는 kakao_account.is_email_verified 로 이메일 소유 확인 여부를 알려준다.
    private static boolean emailVerified(OAuth2User oauth,String provider){if(provider.equals("GOOGLE")){Object verified=oauth.getAttribute("email_verified");return Boolean.TRUE.equals(verified)||"true".equals(verified);}Map<String,Object> account=kakaoAccount(oauth);return account!=null&&Boolean.TRUE.equals(account.get("is_email_verified"))&&!Boolean.FALSE.equals(account.get("is_email_valid"));}
    private static String kakaoFallbackEmail(OAuth2User oauth){Object id=oauth.getAttribute("id");return id==null?null:"kakao-"+id+"@oauth.toogeduler.local";}
    private static String name(OAuth2User oauth){String direct=oauth.getAttribute("name");if(direct!=null&&!direct.isBlank())return direct;Map<String,Object> profile=kakaoProfile(oauth);String nickname=profile==null?null:(String)profile.get("nickname");return nickname==null||nickname.isBlank()?"Toogeduler 사용자":nickname;}
    private static String avatar(OAuth2User oauth){String picture=oauth.getAttribute("picture");if(picture!=null)return picture;Map<String,Object> profile=kakaoProfile(oauth);return profile==null?null:(String)profile.get("profile_image_url");}
}
