package com.cakeshop.domain.chat.entity;

public enum ChatRoomStatus {
    OPEN("상담 중"),
    CLOSED("상담 종료");

    private final String label;

    ChatRoomStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
