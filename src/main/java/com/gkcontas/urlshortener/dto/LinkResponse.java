package com.gkcontas.urlshortener.dto;

import com.gkcontas.urlshortener.model.Link;

public record LinkResponse(String code, String shortUrl, String originalUrl) {

    public static LinkResponse of(Link link, String baseUrl) {
        return new LinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getOriginalUrl());
    }
}
