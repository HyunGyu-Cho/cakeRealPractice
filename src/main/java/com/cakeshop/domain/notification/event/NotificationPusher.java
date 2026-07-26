package com.cakeshop.domain.notification.event;

import com.cakeshop.domain.member.service.MemberService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * STOMP 전송 한 곳. 커밋 후 즉시 푸시와 스케줄러 재시도가 같은 코드를 쓴다.
 * 수신자 이메일 해석({@link MemberService#getProfile})은 여기서만 하며,
 * 업무 트랜잭션 안에서는 절대 호출되지 않는다(스펙 6장 규칙 2).
 */
@Component
public class NotificationPusher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;

    public NotificationPusher(SimpMessagingTemplate messagingTemplate, MemberService memberService) {
        this.messagingTemplate = messagingTemplate;
        this.memberService = memberService;
    }

    /**
     * 수신자 개인 큐로 전송하고 전달 이력에 남길 recipient(로그인 username)를 반환한다.
     * 회원 조회·전송 실패는 그대로 던지고, 실패 판정은 호출자가 한다.
     */
    public String pushToMember(NotificationEvent event) {
        String username = memberService.getProfile(event.receiverId()).email();
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", event);
        return username;
    }

    /** 관리자 공용 토픽. 수신자별 성공·실패를 판정할 수 없어 전달 이력을 남기지 않는다. */
    public void pushToAdminTopic(NotificationEvent event) {
        messagingTemplate.convertAndSend("/topic/admin/notifications", event);
    }
}
