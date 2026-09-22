package com.sharded.core.modules.media;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.io.File;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MediaModule extends Module implements CommandExecutor {
   public MediaModule(ShardedCore plugin) {
      super(plugin, "media");
   }

   @Override
   protected void onEnable() {
      this.syncJarResource("gui.yml");
      this.plugin.gui().loadMenu(new File(this.moduleFolder(), "gui.yml"), "media");
      this.registerCommand("media", this);
      this.registerCommand("mediareq", this);
      this.registerCommand("mediarequirements", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.media.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.plugin.gui().open(player, "media");
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}
