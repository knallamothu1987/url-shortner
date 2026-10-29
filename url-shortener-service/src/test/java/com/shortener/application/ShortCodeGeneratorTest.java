package com.shortener.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    @Test
    void base62EncodingIsStableAndUsesTheExpectedAlphabet() {
        assertThat(ShortCodeGenerator.encodeBase62(0)).isEqualTo("0");
        assertThat(ShortCodeGenerator.encodeBase62(61)).isEqualTo("z");
        assertThat(ShortCodeGenerator.encodeBase62(62)).isEqualTo("10");
    }

    @Test
    void rejectsNegativeSequences() {
        assertThatThrownBy(() -> ShortCodeGenerator.encodeBase62(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
