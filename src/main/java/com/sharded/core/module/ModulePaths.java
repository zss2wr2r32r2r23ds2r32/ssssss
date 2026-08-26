package com.sharded.core.module;

import com.sharded.core.ShardedCore;

import java.io.File;

/** Resolves categorized module folders on disk and in the jar. */
public final class ModulePaths {

    public static File moduleFolder(ShardedCore plugin, String id) {
        String category = ModuleCategories.categoryOf(id);
        if ("core".equals(category)) {
            return new File(plugin.getDataFolder(), "modules/" + id);
        }
        return new File(plugin.getDataFolder(), "modules/" + category + "/" + id);
    }

    public static String resourcePath(String id, String fileName) {
        String category = ModuleCategories.categoryOf(id);
        if ("core".equals(category)) {
            return "modules/" + id + "/" + fileName;
        }
        String categorized = "modules/" + category + "/" + id + "/" + fileName;
        return categorized;
    }

    private ModulePaths() {
    }
}
