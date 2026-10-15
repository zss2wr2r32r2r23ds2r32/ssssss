package com.shardedcore.modules.commands.tpa;

import java.util.UUID;

public record TpaRequest(UUID requester, UUID target, TpaType type, long expiresAt) {
}
