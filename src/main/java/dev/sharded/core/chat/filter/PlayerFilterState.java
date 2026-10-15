package dev.sharded.core.chat.filter;

public final class PlayerFilterState {
    public static final long NEVER = -1L;
    public long lastMessageAt = NEVER;
    public String lastMessage = "";
    public long lastRememberedAt;
}
