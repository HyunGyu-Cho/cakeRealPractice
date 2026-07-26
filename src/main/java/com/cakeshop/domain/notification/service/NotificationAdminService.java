package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.dto.form.NotificationSearchForm;
import com.cakeshop.domain.notification.dto.view.AdminNotificationView;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 발송 내역 조회 전용(읽기 전용). 수신자 표시는 MemberService 공개 계약으로 채운다. */
@Service
public class NotificationAdminService {

    private final NotificationMapper notificationMapper;
    private final MemberService memberService;

    public NotificationAdminService(NotificationMapper notificationMapper,
                                    MemberService memberService) {
        this.notificationMapper = notificationMapper;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminNotificationView> getPage(NotificationSearchForm cond,
                                                     PageRequest pageRequest) {
        long total = notificationMapper.countBySearch(
            cond.normalizedType(), cond.normalizedRead(), null);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<Notification> rows = notificationMapper.findSearchPage(
            cond.normalizedType(), cond.normalizedRead(), null,
            pageRequest.getSize(), pageRequest.getOffset());
        Map<Long, MemberProfileView> profiles = memberService.getProfileMap(
            rows.stream().map(Notification::getReceiverId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<AdminNotificationView> views = rows.stream()
            .map(row -> toView(row, profiles.get(row.getReceiverId())))
            .toList();
        return new PageResult<>(views, pageRequest, total);
    }

    private AdminNotificationView toView(Notification row, MemberProfileView receiver) {
        return new AdminNotificationView(
            row.getId(),
            row.getReceiverId(),
            receiver == null ? "(탈퇴 회원)" : receiver.nickname(),
            row.getNotificationType().name(),
            row.getNotificationType().label(),
            row.getTitle(),
            row.getContent(),
            row.getOrderId(),
            row.isRead(),
            row.getCreatedAt()
        );
    }
}
