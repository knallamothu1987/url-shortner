package com.shortener.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(validator.validateAndNormalize("https://example.com/a%20path"))
                .isEqualTo("https://example.com/a%20path");
        assertThat(validator.validateAndNormalize("http://example.com"))
                .isEqualTo("http://example.com");
    }

    @Test
    void rejectsUnsupportedOrMalformedUrls() {
        assertThatThrownBy(() -> validator.validateAndNormalize("javascript:alert(1)"))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https:///missing-host"))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://user:pass@example.com"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void validatesAndNormalizesAliases() {
        assertThat(UrlValidator.normalizeAlias("Abcd_1")).isEqualTo("abcd_1");
        assertThatThrownBy(() -> UrlValidator.normalizeAlias("no spaces"))
                .isInstanceOf(InvalidAliasException.class);
    }
}
