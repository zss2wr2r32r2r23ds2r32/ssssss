package com.sharded.core.util;

import com.sharded.core.ShardedCore;

/** Global prefix from config.yml (%prefix% in all module messages). */
public final class Prefix {

    private Prefix() {
    }

    public static String get() {
        ShardedCore plugin = ShardedCore.get();
        if (plugin == null) return ColorUtil.normalize("&8[&bSharded&8] &r");
        return ColorUtil.normalize(plugin.getConfig().getString("prefix", "&8[&bSharded&8] &r"));
    }
}
