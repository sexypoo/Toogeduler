package app.toogeduler.web;

import app.toogeduler.domain.User;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

public final class ApiSupport {
    private ApiSupport(){}
    public static User user(Authentication auth){ if(auth==null||!(auth.getPrincipal() instanceof User u))throw new ApiException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");return u; }
}
class ApiException extends RuntimeException{
    final HttpStatus status; ApiException(HttpStatus status,String message){super(message);this.status=status;}
}
@RestControllerAdvice class ApiErrors{
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message",e.getBindingResult().getAllErrors().getFirst().getDefaultMessage()));}
}

