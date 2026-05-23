package com.b2b.instantneed.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Strips all HTML tags from user-supplied free-text fields before persistence.
 * Prevents stored XSS when content is later rendered in web or mobile clients.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    /**
     * Remove all HTML tags, leaving plain text. Returns null if input is null.
     */
    public static String strip(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.none());
    }

    /**
     * Strip HTML and trim whitespace. Returns null if result is blank.
     */
    public static String stripOrNull(String input) {
        if (input == null) return null;
        String cleaned = Jsoup.clean(input, Safelist.none()).strip();
        return cleaned.isBlank() ? null : cleaned;
    }
}
