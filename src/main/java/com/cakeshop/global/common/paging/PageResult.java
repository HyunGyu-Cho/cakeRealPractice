package com.cakeshop.global.common.paging;

import java.util.List;

public class PageResult<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResult(List<T> content, PageRequest request, long totalElements) {
        this.content = content;
        this.page = request.getPage();
        this.size = request.getSize();
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / request.getSize());
    }

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
