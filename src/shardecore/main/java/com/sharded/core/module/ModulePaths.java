package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import java.io.File;

public final class ModulePaths {
   public static File moduleFolder(ShardedCore plugin, String id) {
      String s = ModuleCategories.categoryOf(id);
      return "core".equals(s) ? new File(plugin.getDataFolder(), "modules/" + id) : new File(plugin.getDataFolder(), "modules/" + s + "/" + id);
   }

   public static String resourcePath(String id, String fileName) {
      String s = ModuleCategories.categoryOf(id);
      return "core".equals(s) ? "modules/" + id + "/" + fileName : "modules/" + s + "/" + id + "/" + fileName;
   }

   private ModulePaths() {
   }
}
