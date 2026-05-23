package com.b2b.instantneed.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void strip_null_returnsNull() {
        assertThat(HtmlSanitizer.strip(null)).isNull();
    }

    @Test
    void strip_plainText_unchanged() {
        assertThat(HtmlSanitizer.strip("Hello World")).isEqualTo("Hello World");
    }

    @Test
    void strip_removesAllHtmlTags() {
        assertThat(HtmlSanitizer.strip("<b>Bold</b> and <i>italic</i>"))
                .isEqualTo("Bold and italic");
    }

    @Test
    void strip_removesScriptTag_preventingXss() {
        assertThat(HtmlSanitizer.strip("<script>alert('xss')</script>Clean"))
                .doesNotContain("<script>")
                .contains("Clean");
    }

    @Test
    void stripOrNull_null_returnsNull() {
        assertThat(HtmlSanitizer.stripOrNull(null)).isNull();
    }

    @Test
    void stripOrNull_blankAfterStripping_returnsNull() {
        // Tags only — nothing left after stripping
        assertThat(HtmlSanitizer.stripOrNull("<br/>   ")).isNull();
    }

    @Test
    void stripOrNull_contentAfterStripping_returnsTrimmedText() {
        assertThat(HtmlSanitizer.stripOrNull("  <b>text</b>  ")).isEqualTo("text");
    }
}
