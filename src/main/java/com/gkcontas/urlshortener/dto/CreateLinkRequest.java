package com.gkcontas.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateLinkRequest(

        @NotBlank(message = "originalUrl is required")
        @URL(message = "originalUrl must be a valid URL")
        String originalUrl
) {
}
