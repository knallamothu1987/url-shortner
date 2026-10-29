package com.shortener.orchestration.gates;

public record GateResult(Outcome outcome, String message) {

    public enum Outcome {
        PASS,
        FAIL,
        PENDING_APPROVAL
    }

    public static GateResult pass(String message) {
        return new GateResult(Outcome.PASS, message);
    }

    public static GateResult fail(String message) {
        return new GateResult(Outcome.FAIL, message);
    }

    public static GateResult pendingApproval(String message) {
        return new GateResult(Outcome.PENDING_APPROVAL, message);
    }
}
