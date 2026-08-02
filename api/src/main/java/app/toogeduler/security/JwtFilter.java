package app.toogeduler.security;

import app.toogeduler.repo.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component @RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final UserRepository users;
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String value=req.getHeader("Authorization");
        if(value!=null&&value.startsWith("Bearer ")) try{
            users.findById(jwt.parse(value.substring(7))).ifPresent(user-> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of())));
        }catch(Exception ignored){}
        chain.doFilter(req,res);
    }
}

