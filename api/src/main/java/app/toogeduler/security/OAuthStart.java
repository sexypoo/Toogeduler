package app.toogeduler.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 소셜 로그인을 시작할 때 세션에 남겨 두는 정보. 콜백에서 한 번 꺼내 쓰고 바로 지운다. */
public record OAuthStart(String challenge,boolean mobile){
    private static final String CHALLENGE="oauth_challenge",MOBILE="oauth_mobile";

    public static void save(HttpSession session,String challenge,boolean mobile){session.setAttribute(CHALLENGE,challenge);session.setAttribute(MOBILE,mobile);}

    static OAuthStart take(HttpServletRequest request){
        HttpSession session=request.getSession(false);
        if(session==null)return new OAuthStart(null,false);
        OAuthStart start=new OAuthStart((String)session.getAttribute(CHALLENGE),Boolean.TRUE.equals(session.getAttribute(MOBILE)));
        session.removeAttribute(CHALLENGE);session.removeAttribute(MOBILE);
        return start;
    }
}
