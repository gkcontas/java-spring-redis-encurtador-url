package com.gkcontas.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gkcontas.urlshortener.dto.CreateLinkRequest;
import com.gkcontas.urlshortener.exception.LinkNotFoundException;
import com.gkcontas.urlshortener.model.Link;
import com.gkcontas.urlshortener.repository.LinkRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private AccessCounterService accessCounterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(
                linkRepository, shortCodeGenerator, accessCounterService, redisTemplate,
                "http://localhost:8080", Duration.ofHours(1)
        );
    }

    @Test
    void shouldCreateLinkWithGeneratedCode() {
        when(shortCodeGenerator.generateUniqueCode()).thenReturn("abc1234");
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = linkService.create(new CreateLinkRequest("https://example.com"));

        assertThat(response.code()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(response.originalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void shouldReturnCachedUrlOnCacheHitWithoutQueryingTheDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("link:abc1234")).thenReturn("https://example.com");

        String resolved = linkService.resolve("abc1234");

        assertThat(resolved).isEqualTo("https://example.com");
        verify(linkRepository, never()).findByCode(any());
        verify(accessCounterService).increment("abc1234");
    }

    @Test
    void shouldFallBackToDatabaseAndPopulateCacheOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("link:abc1234")).thenReturn(null);
        when(linkRepository.findByCode("abc1234")).thenReturn(Optional.of(new Link("abc1234", "https://example.com")));

        String resolved = linkService.resolve("abc1234");

        assertThat(resolved).isEqualTo("https://example.com");
        verify(valueOperations).set(eq("link:abc1234"), eq("https://example.com"), eq(Duration.ofHours(1)));
        verify(accessCounterService).increment("abc1234");
    }

    @Test
    void shouldThrowNotFoundWhenLinkIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("link:missing")).thenReturn(null);
        when(linkRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.resolve("missing"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenLinkIsInactiveEvenIfFoundInDatabase() {
        Link inactive = new Link("abc1234", "https://example.com");
        inactive.deactivate();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("link:abc1234")).thenReturn(null);
        when(linkRepository.findByCode("abc1234")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> linkService.resolve("abc1234"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void shouldEvictCacheWhenDeactivatingALink() {
        Link link = new Link("abc1234", "https://example.com");
        when(linkRepository.findByCode("abc1234")).thenReturn(Optional.of(link));

        linkService.deactivate("abc1234");

        assertThat(link.isActive()).isFalse();
        verify(redisTemplate).delete("link:abc1234");
    }

    @Test
    void shouldReturnStatsForExistingLink() {
        Link link = new Link("abc1234", "https://example.com");
        when(linkRepository.findByCode("abc1234")).thenReturn(Optional.of(link));

        var stats = linkService.stats("abc1234");

        assertThat(stats.code()).isEqualTo("abc1234");
        assertThat(stats.active()).isTrue();
        assertThat(stats.totalAccesses()).isZero();
    }
}
