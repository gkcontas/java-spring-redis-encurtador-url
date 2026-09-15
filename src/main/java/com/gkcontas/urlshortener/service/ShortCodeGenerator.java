package com.gkcontas.urlshortener.service;

import com.gkcontas.urlshortener.repository.LinkRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    static final int CODE_LENGTH = 7;
    static final int MAX_ATTEMPTS = 5;

    private final LinkRepository linkRepository;
    private final SecureRandom random = new SecureRandom();

    public ShortCodeGenerator(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (linkRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique short code after %d attempts".formatted(MAX_ATTEMPTS));
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
