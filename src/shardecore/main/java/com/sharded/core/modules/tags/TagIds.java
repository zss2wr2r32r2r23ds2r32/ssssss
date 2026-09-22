package com.sharded.core.modules.tags;

final class TagIds {
   private TagIds() {
   }

   static boolean isCreatorTag(String id) {
      return id != null && ("creator".equalsIgnoreCase(id) || "streamer".equalsIgnoreCase(id));
   }
}
