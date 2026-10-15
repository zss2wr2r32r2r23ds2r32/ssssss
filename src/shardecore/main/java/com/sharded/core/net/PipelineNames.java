package com.sharded.core.net;

import java.util.List;
import java.util.Locale;

public final class PipelineNames {
   public static final String HANDLER = "shardedcore_add_entity_fix";

   private PipelineNames() {
   }

   public static String encoderName(List<String> names) {
      if (names != null && !names.isEmpty()) {
         if (names.contains("encoder")) {
            return "encoder";
         } else if (names.contains("outbound_config")) {
            return "outbound_config";
         } else {
            for (String s : names) {
               if (s != null && s.toLowerCase(Locale.ROOT).contains("encoder")) {
                  return s;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }
}
