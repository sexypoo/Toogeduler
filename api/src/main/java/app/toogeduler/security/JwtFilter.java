package app.toogeduler.security;

import app.toogeduler.repo.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final AuthCookies cookies;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // Authorization 헤더가 있으면 그것만 본다(쿠키로 넘어가지 않는다). CSRF 검사는 헤더 없는 쿠키 요청만 하므로,
        // 헤더가 붙은 요청이 쿠키로 인증되면 검사를 건너뛴 채 쿠키 인증이 열린다(SecurityConfig).
        String value = req.getHeader("Authorization");
        String token =
                value == null
                        ? cookies.access(req)
                        : value.startsWith("Bearer ") ? value.substring(7) : null;
        if (token != null)
            try {
                users.findById(jwt.parse(token))
                        .ifPresent(
                                user ->
                                        SecurityContextHolder.getContext()
                                                .setAuthentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                user, null, List.of())));
            } catch (Exception ignored) {
            }
        chain.doFilter(req, res);
    }
}
