package com.shortener.orchestration.tools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Regex-based credential scanner used by the policy guardrails and the ReviewAgent. */
@Component
public class SecretScanner {

    public record SecretFinding(String file, String patternName) {
    }

    private record NamedPattern(String name, Pattern pattern) {
    }

    private static final List<NamedPattern> PATTERNS = List.of(
            new NamedPattern("aws-access-key", Pattern.compile("AKIA[0-9A-Z]{16}")),
            new NamedPattern("github-token", Pattern.compile("ghp_[A-Za-z0-9]{36}")),
            new NamedPattern("private-key-block", Pattern.compile("-----BEGIN (RSA |EC )?PRIVATE KEY-----")),
            new NamedPattern("hardcoded-password", Pattern.compile(
                    "(?i)(password|passwd|pwd)\\s*[:=]\\s*[\"'][^\"']{4,}[\"']")),
            new NamedPattern("hardcoded-secret", Pattern.compile(
                    "(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*[\"'][A-Za-z0-9+/_-]{16,}[\"']")));

    public List<SecretFinding> scanText(String sourceName, String content) {
        List<SecretFinding> findings = new ArrayList<>();
        for (NamedPattern named : PATTERNS) {
            if (named.pattern().matcher(content).find()) {
                findings.add(new SecretFinding(sourceName, named.name()));
            }
        }
        return findings;
    }

    public List<SecretFinding> scanDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<SecretFinding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    findings.addAll(scanText(file.toString(), content));
                } catch (IOException ignored) {
                    // Binary or unreadable file — skip.
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan directory " + directory, exception);
        }
        return findings;
    }
}
