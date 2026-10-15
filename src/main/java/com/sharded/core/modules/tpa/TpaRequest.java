package com.sharded.core.modules.tpa;

import java.util.UUID;

public record TpaRequest(UUID requester, UUID target, TpaType type, long createdAt, long expiresAt) {
}
