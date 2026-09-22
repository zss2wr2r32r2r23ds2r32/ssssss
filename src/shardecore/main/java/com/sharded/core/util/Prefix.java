package com.sharded.core.util;

import com.sharded.core.ShardedCore;

public final class Prefix {
   private Prefix() {
   }

   public static String get() {
      ShardedCore shardedcore = ShardedCore.get();
      return shardedcore == null
         ? ColorUtil.normalize("&8[&bSharded&8] &r")
         : ColorUtil.normalize(shardedcore.getConfig().getString("prefix", "&8[&bSharded&8] &r"));
   }
}
