package com.cakeshop.global.common.paging;

public class PageRequest {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final int page;   // 1부터 시작
    private final int size;

    public PageRequest(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        this.page = p;
        this.size = s;
    }

    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getOffset() { return (page - 1) * size; }
}
