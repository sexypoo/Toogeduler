package app.toogeduler.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter; private final OAuthLoginHandler oauthLogin;
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{
        // 인증 상태는 요청마다 JWT 로만 결정한다. 세션에 저장하면 로그아웃(토큰 삭제) 후에도
        // JSESSIONID 쿠키만으로 로그인이 유지되고, CSRF 를 끈 상태에서 쿠키 인증이 열리게 된다.
        // 세션 자체는 OAuth2 인가 요청(state)과 로그인 시작 정보(OAuthStart) 보관용으로만 쓴다.
        http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(c->c.securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/public/**","/oauth2/**","/login/**","/error","/actuator/health/**").permitAll().anyRequest().authenticated())
            .oauth2Login(o->o.successHandler(oauthLogin).failureHandler(oauthLogin))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.web-url}")String webUrl){
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(List.of(webUrl));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;
    }
}
