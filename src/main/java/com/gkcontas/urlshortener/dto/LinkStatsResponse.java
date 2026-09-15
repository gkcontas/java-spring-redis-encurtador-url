package com.gkcontas.urlshortener.dto;

import com.gkcontas.urlshortener.model.Link;

public record LinkStatsResponse(String code, boolean active, long totalAccesses) {

    public static LinkStatsResponse of(Link link) {
        return new LinkStatsResponse(link.getCode(), link.isActive(), link.getTotalAccesses());
    }
}
