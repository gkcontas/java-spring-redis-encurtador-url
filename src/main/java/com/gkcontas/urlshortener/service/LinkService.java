package com.gkcontas.urlshortener.service;

import com.gkcontas.urlshortener.dto.CreateLinkRequest;
import com.gkcontas.urlshortener.dto.LinkResponse;
import com.gkcontas.urlshortener.dto.LinkStatsResponse;
import com.gkcontas.urlshortener.exception.LinkNotFoundException;
import com.gkcontas.urlshortener.model.Link;
import com.gkcontas.urlshortener.repository.LinkRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache-aside pattern: reads try Redis first; on a miss, fall back to
 * PostgreSQL and populate the cache with a TTL. Writes (create, deactivate)
 * always go to PostgreSQL, and deactivation actively evicts the cache key
 * instead of waiting for it to expire.
 */
@Service
@Transactional
public class LinkService {

    static final String CACHE_KEY_PREFIX = "link:";

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final AccessCounterService accessCounterService;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;
    private final Duration cacheTtl;

    public LinkService(
            LinkRepository linkRepository,
            ShortCodeGenerator shortCodeGenerator,
            AccessCounterService accessCounterService,
            StringRedisTemplate redisTemplate,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.cache.ttl}") Duration cacheTtl
    ) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.accessCounterService = accessCounterService;
        this.redisTemplate = redisTemplate;
        this.baseUrl = baseUrl;
        this.cacheTtl = cacheTtl;
    }

    public LinkResponse create(CreateLinkRequest request) {
        String code = shortCodeGenerator.generateUniqueCode();
        Link link = linkRepository.save(new Link(code, request.originalUrl()));
        return LinkResponse.of(link, baseUrl);
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        String cacheKey = cacheKey(code);
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            accessCounterService.increment(code);
            return cachedUrl;
        }

        Link link = linkRepository.findByCode(code)
                .filter(Link::isActive)
                .orElseThrow(() -> new LinkNotFoundException(code));

        redisTemplate.opsForValue().set(cacheKey, link.getOriginalUrl(), cacheTtl);
        accessCounterService.increment(code);
        return link.getOriginalUrl();
    }

    public void deactivate(String code) {
        Link link = findEntityByCode(code);
        link.deactivate();
        redisTemplate.delete(cacheKey(code));
    }

    @Transactional(readOnly = true)
    public LinkStatsResponse stats(String code) {
        return LinkStatsResponse.of(findEntityByCode(code));
    }

    private Link findEntityByCode(String code) {
        return linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    private String cacheKey(String code) {
        return CACHE_KEY_PREFIX + code;
    }
}
