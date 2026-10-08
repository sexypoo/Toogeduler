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
/**
 * 모든 오류를 {"message": ...} 형태로 돌려준다. 클라이언트 잘못(형식·누락·없는 ID)은 4xx 로,
 * 그 밖의 예상하지 못한 오류만 500 으로 남겨 로그에 기록한다.
 */
@RestControllerAdvice @lombok.extern.slf4j.Slf4j class ApiErrors{
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return body(e.status,e.getMessage());}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException e){return body(HttpStatus.BAD_REQUEST,e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());}
    /** 본문이 JSON 이 아니거나, 날짜·enum 같은 값의 형식이 틀린 경우. */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,java.time.DateTimeException.class})
    ResponseEntity<?> malformed(Exception e){return body(HttpStatus.BAD_REQUEST,"요청 형식이 올바르지 않습니다. 날짜·시간과 입력값을 확인해주세요.");}
    /** findById(...).orElseThrow() 처럼 없는 대상을 찾은 경우. */
    @ExceptionHandler(java.util.NoSuchElementException.class) ResponseEntity<?> missing(java.util.NoSuchElementException e){return body(HttpStatus.NOT_FOUND,"요청한 대상을 찾을 수 없습니다.");}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(org.springframework.dao.DataIntegrityViolationException e){log.warn("Data integrity violation: {}",e.getMostSpecificCause().getMessage());return body(HttpStatus.CONFLICT,"저장할 수 없는 값이 있습니다. 입력값을 확인해주세요.");}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e)throws Exception{
        // 인증·권한 오류는 Spring Security 가 처리하도록 그대로 던진다.
        if(e instanceof org.springframework.security.access.AccessDeniedException||e instanceof org.springframework.security.core.AuthenticationException)throw e;
        // 404 경로·405 메서드처럼 Spring 이 이미 상태를 정한 오류는 그 상태를 그대로 쓴다.
        if(e instanceof org.springframework.web.ErrorResponse framework){HttpStatus status=HttpStatus.valueOf(framework.getStatusCode().value());if(status.is4xxClientError())return body(status,"요청을 처리할 수 없습니다.");}
        log.error("Unhandled API error",e);return body(HttpStatus.INTERNAL_SERVER_ERROR,"잠시 후 다시 시도해주세요.");
    }
    private static ResponseEntity<Map<String,String>> body(HttpStatus status,String message){return ResponseEntity.status(status).body(Map.of("message",message));}
}
