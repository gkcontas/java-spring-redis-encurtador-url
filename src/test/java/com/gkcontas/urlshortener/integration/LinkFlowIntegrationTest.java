package com.gkcontas.urlshortener.integration;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gkcontas.urlshortener.repository.LinkRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class LinkFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private LinkRepository linkRepository;

    @Test
    void shouldResolveFromCacheOnSecondAccessWithoutHittingTheDatabaseAgain() throws Exception {
        String code = createLink("https://example.com/page");

        // Creating the link already called findByCode once, because ShortCodeGenerator
        // checks that the generated code is not taken. Counting that setup call together
        // with the resolves would make the assertion below mean something else than what
        // it claims, so the spy starts from a clean slate here.
        clearInvocations(linkRepository);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));

        // findByCode is only hit once: the first access is a cache miss that
        // populates Redis; the second is served entirely from the cache.
        verify(linkRepository, times(1)).findByCode(code);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/links/{code}/stats", code))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalAccesses").value(2))
        );
    }

    @Test
    void shouldInvalidateCacheAndReturnNotFoundAfterDeactivation() throws Exception {
        String code = createLink("https://example.com/other");

        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());

        mockMvc.perform(delete("/links/{code}", code)).andExpect(status().isNoContent());

        mockMvc.perform(get("/{code}", code)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/{code}", "doesnotexist")).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestForInvalidUrl() throws Exception {
        mockMvc.perform(post("/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originalUrl").exists());
    }

    private String createLink(String originalUrl) throws Exception {
        String body = mockMvc.perform(post("/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"%s\"}".formatted(originalUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("code").asText();
    }
}
