package com.sharded.core.modules.chatfilter;

public final class PlayerFilterState {
   public static final long NEVER = 0L;
   public long lastMessageAt = 0L;
   public String lastMessage = "";
   public long lastRememberedAt = 0L;
}
