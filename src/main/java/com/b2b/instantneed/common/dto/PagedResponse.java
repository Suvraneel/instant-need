package com.b2b.instantneed.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int limit,
        long total
) {
    public static <T> PagedResponse<T> of(Page<T> springPage) {
        return new PagedResponse<>(
                springPage.getContent(),
                springPage.getNumber() + 1,
                springPage.getSize(),
                springPage.getTotalElements()
        );
    }
}
