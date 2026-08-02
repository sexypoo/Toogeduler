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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter; private final JwtService jwt; private final UserRepository users;
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${app.web-url}")String webUrl)throws Exception{
        http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/public/**","/oauth2/**","/login/**","/error").permitAll().anyRequest().authenticated())
            .oauth2Login(o->o.successHandler((req,res,auth)->{
                OAuth2User oauth=(OAuth2User)auth.getPrincipal();
                String email=Optional.ofNullable(oauth.<String>getAttribute("email")).orElseGet(()->{
                    Map<String,Object> account=oauth.getAttribute("kakao_account"); return account==null?null:(String)account.get("email");
                });
                if(email==null){res.sendError(400,"이메일 제공 동의가 필요합니다.");return;}
                String name=Optional.ofNullable(oauth.<String>getAttribute("name")).orElse("Toogeduler 사용자");
                User user=users.findByEmailIgnoreCase(email).orElseGet(()->{User u=new User(email,name,null);u.setProvider("OAUTH");return users.save(u);});
                res.sendRedirect(webUrl+"/auth/callback?token="+jwt.create(user.getId()));
            }))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.web-url}")String webUrl){
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(List.of(webUrl));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;
    }
}

