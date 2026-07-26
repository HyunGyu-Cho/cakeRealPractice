package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.dto.view.NotificationSliceView;
import com.cakeshop.domain.notification.dto.view.NotificationView;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.error.NotificationErrorCode;
import com.cakeshop.domain.notification.event.NotificationEvent;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종단 도메인 — 업무 도메인은 {@link #notify}/{@link #notifyAdmins}만 호출하고,
 * 이 서비스는 업무 도메인을 역참조하지 않는다.
 * 저장은 호출한 업무 트랜잭션에 참여하며, 실시간 전달은 커밋 후 브로드캐스터가 맡는다.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_SLICE_SIZE = 50;

    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryService deliveryService;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationMapper notificationMapper,
                               NotificationDeliveryService deliveryService,
                               MemberService memberService,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMapper = notificationMapper;
        this.deliveryService = deliveryService;
        this.memberService = memberService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * [공개 계약] 회원 한 명에게 알림을 발행한다.
     * 업무 트랜잭션에 남기는 것은 INSERT 두 건(알림 + 전달 이력)뿐이다.
     * 수신자 조회와 STOMP 전송은 커밋 후 브로드캐스터가 맡는다.
     */
    @Transactional
    public void notify(NotificationCommand command) {
        if (command.receiverId() == null) {
            throw new BusinessException(NotificationErrorCode.INVALID_RECEIVER);
        }
        Notification saved = save(command);
        Long deliveryId = deliveryService.enqueue(saved.getId());
        eventPublisher.publishEvent(NotificationEvent.toMember(
            command.receiverId(), deliveryId, NotificationView.from(saved)));
    }

    /**
     * [공개 계약] 전체 관리자에게 알림을 발행한다.
     * 저장·전달 모두 관리자마다 한 건씩 한다 — 공용 토픽 브로드캐스트로는 수신자별
     * 성공·실패를 판정할 수 없어 전달 이력과 재시도를 걸 수 없기 때문이다(스펙 6장 규칙 1).
     */
    @Transactional
    public void notifyAdmins(NotificationCommand command) {
        List<Long> adminIds = memberService.findAdminMemberIds();
        if (adminIds.isEmpty()) {
            log.warn("수신할 관리자가 없어 알림을 건너뜁니다. type={}", command.type());
            return;
        }
        for (Long adminId : adminIds) {
            Notification saved = save(command.withReceiver(adminId));
            Long deliveryId = deliveryService.enqueue(saved.getId());
            eventPublisher.publishEvent(NotificationEvent.toMember(
                adminId, deliveryId, NotificationView.from(saved)));
        }
    }

    /** [공개 계약] 헤더 미읽음 뱃지용 집계. 저장하지 않는 파생값이다. */
    @Transactional(readOnly = true)
    public long countUnread(Long receiverId) {
        return notificationMapper.countUnread(receiverId);
    }

    @Transactional(readOnly = true)
    public NotificationSliceView getSlice(Long receiverId, Long cursor, int requestedSize) {
        int size = Math.min(Math.max(requestedSize, 1), MAX_SLICE_SIZE);
        List<Notification> rows = notificationMapper.findSliceByReceiver(
            receiverId, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<Notification> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getId();
        return new NotificationSliceView(
            page.stream().map(NotificationView::from).toList(),
            hasNext,
            nextCursor,
            notificationMapper.countUnread(receiverId)
        );
    }

    /** 이미 읽은 알림을 다시 읽어도 성공으로 처리한다(멱등). */
    @Transactional
    public void markRead(Long notificationId, Long receiverId) {
        Notification notification = notificationMapper.findById(notificationId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOT_FOUND));
        if (!notification.getReceiverId().equals(receiverId)) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN);
        }
        notificationMapper.markRead(notificationId, receiverId);
    }

    @Transactional
    public void markAllRead(Long receiverId) {
        notificationMapper.markAllRead(receiverId);
    }

    private Notification save(NotificationCommand command) {
        Notification notification = new Notification();
        notification.setReceiverId(command.receiverId());
        notification.setNotificationType(command.type());
        notification.setTitle(command.title());
        notification.setContent(command.content());
        notification.setTargetUrl(command.targetUrl());
        notification.setOrderId(command.orderId());
        notification.setChatMessageId(command.chatMessageId());
        notificationMapper.insert(notification);
        return notification;
    }
}
