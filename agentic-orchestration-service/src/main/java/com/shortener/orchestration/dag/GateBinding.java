package com.shortener.orchestration.dag;

/**
 * A gate attached to a stage, e.g. {@code schema:design-spec} or {@code approval:major-findings}.
 */
public record GateBinding(String type, String param) {

    public static GateBinding parse(String spec) {
        int idx = spec.indexOf(':');
        if (idx < 0) {
            return new GateBinding(spec.trim(), null);
        }
        return new GateBinding(spec.substring(0, idx).trim(), spec.substring(idx + 1).trim());
    }

    public String displayName() {
        return param == null ? type : type + ":" + param;
    }
}
