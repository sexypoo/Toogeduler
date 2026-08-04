package app.toogeduler.security;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Configuration @RequiredArgsConstructor @Slf4j
public class SecurityConfig {
    private final JwtFilter jwtFilter; private final JwtService jwt; private final UserRepository users;
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${app.web-url}")String webUrl)throws Exception{
        http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/public/**","/oauth2/**","/login/**","/error","/actuator/health/**").permitAll().anyRequest().authenticated())
            .oauth2Login(o->o.successHandler((req,res,auth)->{
                OAuth2User oauth=(OAuth2User)auth.getPrincipal();
                String provider=auth instanceof OAuth2AuthenticationToken token?token.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT):"OAUTH";
                String email=oauthEmail(oauth);if((email==null||email.isBlank())&&provider.equals("KAKAO"))email=kakaoFallbackEmail(oauth);if(email==null||email.isBlank()){res.sendRedirect(webUrl+"/login?oauthError=email");return;}
                String name=oauthName(oauth);String avatar=oauthAvatar(oauth);String normalized=email.toLowerCase(Locale.ROOT);
                User user=users.findByEmailIgnoreCase(normalized).orElseGet(()->{User u=new User(normalized,name,null);u.setProvider(provider);u.setAvatarUrl(avatar);return users.save(u);});
                boolean changed=false;if((user.getAvatarUrl()==null||user.getAvatarUrl().isBlank())&&avatar!=null){user.setAvatarUrl(avatar);changed=true;}if(user.getFriendCode()==null||user.getFriendCode().isBlank()){user.setFriendCode(User.createFriendCode());changed=true;}if(changed)user=users.save(user);
                res.sendRedirect(webUrl+"/auth/callback?token="+jwt.create(user.getId()));
            }).failureHandler((req,res,error)->{String code=error instanceof OAuth2AuthenticationException oauth?oauth.getError().getErrorCode():"oauth_failed";log.warn("OAuth login failed: {}",code);res.sendRedirect(webUrl+"/login?oauthError="+URLEncoder.encode(code,StandardCharsets.UTF_8));}))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @SuppressWarnings("unchecked")
    private String oauthEmail(OAuth2User oauth){String direct=oauth.getAttribute("email");if(direct!=null)return direct;Map<String,Object> account=oauth.getAttribute("kakao_account");return account==null?null:(String)account.get("email");}
    private String kakaoFallbackEmail(OAuth2User oauth){Object id=oauth.getAttribute("id");return id==null?null:"kakao-"+id+"@oauth.toogeduler.local";}
    @SuppressWarnings("unchecked")
    private String oauthName(OAuth2User oauth){String direct=oauth.getAttribute("name");if(direct!=null&&!direct.isBlank())return direct;Map<String,Object> account=oauth.getAttribute("kakao_account");Map<String,Object> profile=account==null?null:(Map<String,Object>)account.get("profile");String nickname=profile==null?null:(String)profile.get("nickname");return nickname==null||nickname.isBlank()?"Toogeduler 사용자":nickname;}
    @SuppressWarnings("unchecked")
    private String oauthAvatar(OAuth2User oauth){String picture=oauth.getAttribute("picture");if(picture!=null)return picture;Map<String,Object> account=oauth.getAttribute("kakao_account");Map<String,Object> profile=account==null?null:(Map<String,Object>)account.get("profile");return profile==null?null:(String)profile.get("profile_image_url");}
    @Bean CorsConfigurationSource cors(@Value("${app.web-url}")String webUrl){
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(List.of(webUrl));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;
    }
}
