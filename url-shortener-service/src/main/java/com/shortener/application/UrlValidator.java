package com.shortener.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    public String validateAndNormalize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL must contain between 1 and 2048 characters");
        }

        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new InvalidUrlException("Only absolute HTTP and HTTPS URLs are allowed");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("URL is malformed");
        }
    }

    public static String normalizeAlias(String alias) {
        if (alias == null || !alias.matches("[A-Za-z0-9_-]{4,20}")) {
            throw new InvalidAliasException("Alias must be 4-20 URL-safe characters");
        }
        return alias.toLowerCase(Locale.ROOT);
    }
}
