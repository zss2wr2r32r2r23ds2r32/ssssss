package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class BoardStorage {
   private final ShardedCore plugin;
   private final File file;
   private final Map<String, BoardDefinition> boards = new LinkedHashMap<>();

   BoardStorage(ShardedCore plugin, File folder) {
      this.plugin = plugin;
      this.file = new File(folder, "boards.yml");
   }

   Map<String, BoardDefinition> boards() {
      return this.boards;
   }

   BoardDefinition get(String id) {
      if (id == null) {
         return null;
      } else {
         BoardDefinition boarddefinition = this.boards.get(id.toLowerCase(Locale.ROOT));
         if (boarddefinition != null) {
            return boarddefinition;
         } else {
            for (BoardDefinition boarddefinition1 : this.boards.values()) {
               if (boarddefinition1.id.equalsIgnoreCase(id) || boarddefinition1.statisticKey().equalsIgnoreCase(id)) {
                  return boarddefinition1;
               }
            }

            return null;
         }
      }
   }

   void load() {
      this.boards.clear();
      if (!this.file.exists()) {
         this.copyDefault();
      }

      YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("boards");
      if (configurationsection == null) {
         this.seedDefaults();
         this.save();
      } else {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               BoardDefinition boarddefinition = new BoardDefinition();
               boarddefinition.load(s.toLowerCase(Locale.ROOT), configurationsection1);
               this.boards.put(boarddefinition.id, boarddefinition);
            }
         }

         if (this.boards.isEmpty()) {
            this.seedDefaults();
            this.save();
         } else {
            int i = yamlconfiguration.getInt("config-version", 1);
            boolean flag = false;
            if (i < 3) {
               this.migrateStyled(i);
               flag = true;
            }
            if (this.ensureRefreshLines()) {
               flag = true;
            }

            if (this.ensureBoard("tokens")) {
               flag = true;
            }

            if (flag) {
               this.save();
            }
         }
      }
   }

   void save() {
      YamlConfiguration yamlconfiguration = new YamlConfiguration();
      yamlconfiguration.set("config-version", 4);

      for (BoardDefinition boarddefinition : this.boards.values()) {
         boarddefinition.save(yamlconfiguration.createSection("boards." + boarddefinition.id));
      }

      try {
         yamlconfiguration.save(this.file);
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("[leaderboards] Could not save boards.yml: " + ioexception.getMessage());
      }
   }

   BoardDefinition create(String rawId, String statistic, List<String> lines, String title) {
      String s = sanitize(rawId);
      if (!s.isEmpty() && !this.boards.containsKey(s)) {
         BoardDefinition boarddefinition = new BoardDefinition();
         boarddefinition.id = s;
         boarddefinition.statistic = BoardStyles.statistic(statistic);
         boarddefinition.entries = 10;
         boarddefinition.hologramEnabled = true;
         boarddefinition.title = title;
         boarddefinition.lines = new ArrayList<>(lines);
         this.boards.put(s, boarddefinition);
         this.save();
         return boarddefinition;
      } else {
         return null;
      }
   }

   boolean delete(String id) {
      BoardDefinition boarddefinition = this.get(id);
      if (boarddefinition == null) {
         return false;
      } else {
         this.boards.remove(boarddefinition.id);
         this.save();
         return true;
      }
   }

   private boolean ensureRefreshLines() {
      boolean changed = false;
      for (BoardDefinition board : this.boards.values()) {
         if (board.lines == null) {
            continue;
         }
         List<String> cleaned = new ArrayList<>();
         for (String line : board.lines) {
            if (line != null && line.contains("%leaderboard_refresh%")) {
               changed = true;
               continue;
            }
            cleaned.add(line);
         }
         String color = BoardStyles.color(board.statisticKey());
         String refresh = color + "Refreshes in &f%leaderboard_refresh%";
         int insert = 1;
         for (int i = 0; i < cleaned.size(); i++) {
            String line = cleaned.get(i);
            if (line == null) {
               continue;
            }
            String plain = line.replaceAll("(?i)&[0-9a-fk-or]", "")
               .replaceAll("(?i)&#[0-9a-f]{6}", "")
               .toLowerCase(Locale.ROOT);
            if (plain.contains("leaderboard") && !plain.contains("%")) {
               insert = i + 1;
               break;
            }
         }
         if (insert > cleaned.size()) {
            insert = Math.min(1, cleaned.size());
         }
         cleaned.add(insert, refresh);
         if (!cleaned.equals(board.lines)) {
            board.lines = cleaned;
            changed = true;
         }
      }
      return changed;
   }

   private boolean ensureBoard(String id) {
      String s = id.toLowerCase(Locale.ROOT);
      if (!this.boards.containsKey(s) && this.get(s) == null) {
         BoardDefinition boarddefinition = new BoardDefinition();
         boarddefinition.id = s;
         boarddefinition.statistic = BoardStyles.statistic(s);
         BoardStyles.apply(boarddefinition);
         this.boards.put(s, boarddefinition);
         return true;
      } else {
         return false;
      }
   }

   private void migrateStyled(int ignoredVersion) {
      for (BoardDefinition boarddefinition : this.boards.values()) {
         if (BoardStyles.builtin(boarddefinition.id) || BoardStyles.builtin(boarddefinition.statisticKey())) {
            BoardStyles.apply(boarddefinition);
         }
      }

      if (this.boards.containsKey("duels") && !this.boards.containsKey("elo")) {
         BoardDefinition boarddefinition1 = this.boards.get("duels");
         BoardStyles.apply(boarddefinition1);
      }
   }

   static String sanitize(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
   }

   private void copyDefault() {
      this.file.getParentFile().mkdirs();

      try {
         try (InputStream inputstream = this.plugin.getResource("modules/leaderboardboards/boards.yml")) {
            if (inputstream != null) {
               YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(new InputStreamReader(inputstream, StandardCharsets.UTF_8));
               yamlconfiguration.save(this.file);
               return;
            }
         }
      } catch (Exception exception) {
      }
   }

   private void seedDefaults() {
      for (String s : List.of("kills", "killstreaks", "deaths", "tokens", "playtime", "elo")) {
         BoardDefinition boarddefinition = new BoardDefinition();
         boarddefinition.id = s;
         boarddefinition.statistic = s;
         boarddefinition.entries = 10;
         boarddefinition.title = BoardStyles.title(s);
         boarddefinition.lines = BoardStyles.lines(s, boarddefinition.entries);
         boarddefinition.hologramEnabled = true;
         this.boards.put(s, boarddefinition);
      }
   }
}
