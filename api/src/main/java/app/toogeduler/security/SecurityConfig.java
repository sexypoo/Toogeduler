package app.toogeduler.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
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
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.*;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final OAuthLoginHandler oauthLogin;
    private final AuthCookies cookies;

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** 인증 쿠키를 "받는" 요청. 비밀번호·verifier·Bearer 토큰이 있어야 성공하므로 CSRF 토큰을 요구하지 않는다. */
    private static final Set<String> CSRF_EXEMPT =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/oauth/exchange",
                    "/api/auth/session");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        // 인증 상태는 요청마다 JWT(Bearer 헤더 또는 tg_at 쿠키)로만 결정한다. 세션에 저장하면 로그아웃 후에도
        // JSESSIONID 쿠키만으로 로그인이 유지된다. 세션 자체는 OAuth2 인가 요청(state)과
        // 로그인 시작 정보(OAuthStart) 보관용으로만 쓴다.
        // 쿠키 인증은 브라우저가 알아서 쿠키를 싣기 때문에 CSRF 이중 제출 토큰으로 지킨다(docs/AUTH_COOKIE_DESIGN.md 결정 1).
        // Bearer 헤더 요청은 다른 사이트가 헤더를 붙일 수 없으므로(CORS 사전 요청) 검사하지 않는다.
        http.csrf(
                        c ->
                                c.csrfTokenRepository(cookies.csrfRepository())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .requireCsrfProtectionMatcher(this::needsCsrf)
                                        // 기본 전략은 "새 로그인"마다 토큰을 지운다. JwtFilter 는 요청마다 인증하므로
                                        // 그대로 두면 인증된 응답마다 XSRF 쿠키가 빈 값이 된다.
                                        .sessionAuthenticationStrategy((auth, req, res) -> {}))
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(
                        c ->
                                c.securityContextRepository(
                                        new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/api/auth/**",
                                                "/api/public/**",
                                                "/oauth2/**",
                                                "/login/**",
                                                "/error",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(o -> o.successHandler(oauthLogin).failureHandler(oauthLogin))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                        (req, res, ex) ->
                                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(SecurityConfig::loadCsrfToken, CsrfFilter.class);
        return http.build();
    }

    /** Authorization 헤더 없이 인증 쿠키를 실은 상태 변경 요청만 검사한다. */
    private boolean needsCsrf(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        return !SAFE_METHODS.contains(req.getMethod())
                && req.getHeader("Authorization") == null
                && cookies.access(req) != null
                && !CSRF_EXEMPT.contains(path);
    }

    /** Spring 은 CSRF 토큰을 쓸 때까지 만들지 않는다. 한 번 건드려 쿠키가 없으면 응답에 실리게 한다. */
    private static void loadCsrfToken(ServletRequest req, ServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        if (token != null) token.getToken();
        chain.doFilter(req, res);
    }

    @Bean
    CorsConfigurationSource cors(@Value("${app.web-url}") String webUrl) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(webUrl));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
