package com.gkcontas.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gkcontas.urlshortener.model.Link;
import com.gkcontas.urlshortener.repository.LinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortCodeGeneratorTest {

    @Mock
    private LinkRepository linkRepository;

    @Test
    void shouldGenerateCodeWithExpectedLengthAndAlphabet() {
        when(linkRepository.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        ShortCodeGenerator generator = new ShortCodeGenerator(linkRepository);

        String code = generator.generateUniqueCode();

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
        assertThat(code).matches("[A-Za-z0-9]+");
    }

    @Test
    void shouldRetryWhenGeneratedCodeAlreadyExists() {
        Link existing = new Link("collision", "https://example.com");
        when(linkRepository.findByCode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());
        ShortCodeGenerator generator = new ShortCodeGenerator(linkRepository);

        String code = generator.generateUniqueCode();

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
    }

    @Test
    void shouldThrowAfterExhaustingAllAttempts() {
        Link existing = new Link("collision", "https://example.com");
        when(linkRepository.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(existing));
        ShortCodeGenerator generator = new ShortCodeGenerator(linkRepository);

        assertThatThrownBy(generator::generateUniqueCode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique short code");
    }
}
