package com.shortener.orchestration.agents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared text heuristics: feature naming, keyword extraction, failure-injection markers. */
public final class AgentTextUtils {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "to", "of", "for", "with", "that", "this", "should",
            "must", "will", "shall", "be", "is", "are", "was", "it", "in", "on", "as", "we",
            "our", "their", "so", "can", "add", "new", "when", "all", "each", "per", "by",
            "from", "into", "every", "any", "some", "user", "users", "able", "allow", "allows");

    private AgentTextUtils() {
    }

    /** PascalCase feature name derived from the first meaningful words of the requirement. */
    public static String featureName(String requirementText) {
        List<String> words = meaningfulWords(requirementText, 3);
        if (words.isEmpty()) {
            return "Feature";
        }
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            name.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }

    public static String featureSlug(String requirementText) {
        List<String> words = meaningfulWords(requirementText, 3);
        return words.isEmpty() ? "feature" : String.join("-", words).toLowerCase(Locale.ROOT);
    }

    public static List<String> keywords(String requirementText, int max) {
        return meaningfulWords(requirementText, max);
    }

    public static boolean hasMarker(String requirementText, String marker) {
        return requirementText != null && requirementText.contains("[inject:" + marker + "]");
    }

    private static List<String> meaningfulWords(String text, int max) {
        if (text == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String token : text.split("[^A-Za-z0-9]+")) {
            String word = token.trim();
            if (word.length() < 3 || STOPWORDS.contains(word.toLowerCase(Locale.ROOT))) {
                continue;
            }
            seen.add(word);
            if (seen.size() >= max) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }
}
