package dev.sharded.core.chat.filter;

public final class FilterVerdict {
    public enum Status {
        ALLOW,
        MODIFY,
        BLOCK
    }

    private final Status status;
    private final String rule;
    private final String action;
    private final String original;
    private final String result;
    private final int waitSeconds;
    private final int maxCharacters;

    private FilterVerdict(Status status, String rule, String action, String original, String result,
                          int waitSeconds, int maxCharacters) {
        this.status = status;
        this.rule = rule;
        this.action = action;
        this.original = original;
        this.result = result;
        this.waitSeconds = waitSeconds;
        this.maxCharacters = maxCharacters;
    }

    public static FilterVerdict allow(String message) {
        return new FilterVerdict(Status.ALLOW, "clean", "ALLOW", message, message, 0, 0);
    }

    public static FilterVerdict modify(String rule, String action, String original, String result) {
        return new FilterVerdict(Status.MODIFY, rule, action, original, result, 0, 0);
    }

    public static FilterVerdict block(String rule, String action, String original, int waitSeconds, int maxCharacters) {
        return new FilterVerdict(Status.BLOCK, rule, action, original, original, waitSeconds, maxCharacters);
    }

    public Status status() {
        return status;
    }

    public boolean blocked() {
        return status == Status.BLOCK;
    }

    public boolean modified() {
        return status == Status.MODIFY;
    }

    public boolean passed() {
        return status != Status.BLOCK;
    }

    public String rule() {
        return rule;
    }

    public String action() {
        return action;
    }

    public String original() {
        return original;
    }

    public String result() {
        return result;
    }

    public int waitSeconds() {
        return waitSeconds;
    }

    public int maxCharacters() {
        return maxCharacters;
    }

    public boolean isWordRule() {
        return "CANCEL".equals(action) || "MASK".equals(action);
    }

    public boolean isCheck() {
        return switch (rule) {
            case "slowmode", "length", "spamming", "repeat", "shouting" -> true;
            default -> false;
        };
    }
}
