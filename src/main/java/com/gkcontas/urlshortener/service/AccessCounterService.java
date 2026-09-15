package com.gkcontas.urlshortener.service;

import com.gkcontas.urlshortener.repository.LinkRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Increments the access counter out-of-band from the redirect response —
 * a slow or failed write here must never delay or break a redirect.
 */
@Service
public class AccessCounterService {

    private final LinkRepository linkRepository;

    public AccessCounterService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Async
    @Transactional
    public void increment(String code) {
        linkRepository.incrementAccesses(code);
    }
}
