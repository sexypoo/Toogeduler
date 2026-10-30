package app.toogeduler.service;

import app.toogeduler.domain.PushSubscription;

/** 구독한 브라우저로 웹 푸시 하나를 보낸다. 테스트에서는 모의 구현으로 바꾼다. */
public interface PushSender {
    /**
     * payload 를 암호화해 구독의 endpoint 로 보내고 푸시 서비스의 HTTP 응답 코드를 돌려준다. 404·410 은 구독이 만료됐다는 뜻이다. 네트워크 오류나
     * 키 오류는 예외로 던진다.
     */
    int send(PushSubscription subscription, byte[] payload) throws Exception;
}
