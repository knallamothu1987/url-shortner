package com.shortener.orchestration.tools;

import com.shortener.orchestration.config.OrchestratorProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lightweight brownfield codebase reasoning: keyword-relevance retrieval over the target
 * codebase. Stands in for the pgvector RAG described in docs/03 (documented trade-off:
 * no new infrastructure required, deterministic and auditable).
 */
@Component
public class CodebaseContextService {

    public record ImpactedFile(String path, int relevance, String reason) {
    }

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(".java", ".sql", ".yml", ".yaml");
    private static final int MAX_RESULTS = 10;

    private final Path codebaseRoot;

    public CodebaseContextService(OrchestratorProperties properties) {
        this.codebaseRoot = resolveCodebaseRoot(Path.of(properties.codebaseRoot()));
    }

    /**
     * Resolves the target codebase whether the service is launched from the repository
     * root or from the module directory (mvn -pl ... spring-boot:run).
     */
    public static Path resolveCodebaseRoot(Path configured) {
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path absoluteConfigured = configured.toAbsolutePath();
        Path configuredName = configured.getFileName();
        Path siblingOfModule = absoluteConfigured.getParent() != null && configuredName != null
                ? absoluteConfigured.getParent().resolveSibling(configuredName)
                : configured;
        return Files.isDirectory(siblingOfModule) ? siblingOfModule : configured;
    }

    public List<ImpactedFile> findImpactedFiles(List<String> keywords) {
        if (!Files.isDirectory(codebaseRoot)) {
            return List.of();
        }
        List<ImpactedFile> results = new ArrayList<>();
        try (Stream<Path> files = Files.walk(codebaseRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .filter(file -> !file.toString().contains("target"))
                    .forEach(file -> scoreFile(file, keywords, results));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan codebase at " + codebaseRoot, exception);
        }
        results.sort(Comparator.comparingInt(ImpactedFile::relevance).reversed());
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    public String readSnippet(String relativePath, int maxLines) {
        Path file = codebaseRoot.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(file);
            return String.join("\n", lines.subList(0, Math.min(maxLines, lines.size())));
        } catch (IOException exception) {
            return "";
        }
    }

    private boolean isSourceFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private void scoreFile(Path file, List<String> keywords, List<ImpactedFile> results) {
        try {
            String content = Files.readString(file).toLowerCase(Locale.ROOT);
            String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            int score = 0;
            List<String> matched = new ArrayList<>();
            for (String keyword : keywords) {
                String lower = keyword.toLowerCase(Locale.ROOT);
                if (lower.length() < 3) {
                    continue;
                }
                if (fileName.contains(lower)) {
                    score += 5;
                    matched.add(keyword + " (filename)");
                } else if (content.contains(lower)) {
                    score += 1;
                    matched.add(keyword);
                }
            }
            if (score > 0) {
                results.add(new ImpactedFile(
                        codebaseRoot.relativize(file).toString().replace('\\', '/'),
                        score,
                        "matches: " + String.join(", ", matched)));
            }
        } catch (IOException ignored) {
            // Unreadable file — skip.
        }
    }
}
