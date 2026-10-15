package com.sharded.core.modules.cosmeticschat;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticDatabase;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.module.Module;
import com.sharded.core.modules.chatcolor.ChatColorModule;
import com.sharded.core.modules.namegradients.NameGradientsModule;
import com.sharded.core.modules.tags.TagsModule;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class CosmeticsChatModule extends Module implements CommandExecutor {
   public CosmeticsChatModule(ShardedCore plugin) {
      super(plugin, "cosmeticschat");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("cosmetics", this);
      this.registerListener(this);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::rebindCommands, 40L);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         HandlerList.unregisterAll(this);
         this.registerListener(this);
         this.rebindCommands();
      }, 80L);
   }

   private void rebindCommands() {
      Module module = this.plugin.modules().get(TagsModule.class);
      Module module1 = this.plugin.modules().get(ChatColorModule.class);
      Module module2 = this.plugin.modules().get(NameGradientsModule.class);
      Module module3 = this.plugin.modules().get(WardrobeModule.class);
      if (module instanceof CommandExecutor commandexecutor) {
         this.bind("tags", commandexecutor);
         this.bind("tag", commandexecutor);
      }

      if (module1 instanceof CommandExecutor commandexecutor1) {
         this.bind("chatcolor", commandexecutor1);
         this.bind("chatcolors", commandexecutor1);
      }

      if (module2 instanceof CommandExecutor commandexecutor2) {
         this.bind("namegradients", commandexecutor2);
         this.bind("namegradient", commandexecutor2);
         this.bind("namecolor", commandexecutor2);
         this.bind("namecolors", commandexecutor2);
      }

      if (module3 instanceof CommandExecutor commandexecutor3) {
         this.bind("wardrobe", commandexecutor3);
         this.bind("hatshop", commandexecutor3);
      }

      this.bind("cosmetics", this);
      this.bind("cosmetic", this);
   }

   private void bind(String name, CommandExecutor exec) {
      PluginCommand plugincommand = this.plugin.getCommand(name);
      if (plugincommand == null) {
         this.plugin.getLogger().warning("[cosmeticschat] Missing command '" + name + "' in plugin.yml");
      } else {
         plugincommand.setExecutor(exec);
         if (exec instanceof TabCompleter tabcompleter) {
            plugincommand.setTabCompleter(tabcompleter);
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         this.openHub(player);
         return true;
      } else {
         sender.sendMessage(Text.c("&#FF0000&lERROR &8▷ &fOnly players can open cosmetics."));
         return true;
      }
   }

   private void openHub(Player player) {
      CosmeticsChatModule.Holder cosmeticschatmodule$holder = new CosmeticsChatModule.Holder();
      Inventory inventory = Bukkit.createInventory(cosmeticschatmodule$holder, 27, Text.c("&#FF0072&lCosmetics"));
      cosmeticschatmodule$holder.inventory = inventory;
      TrackedInventories.track(inventory, cosmeticschatmodule$holder);
      ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, filler);
      }
      inventory.setItem(
         11,
         new ItemBuilder(Material.NAME_TAG)
            .name("&#FCFF00&lTags")
            .lore(
               "&8Description",
               "",
               "&#FCFF00Information:",
               "&#FCFF00| &fOpen the tags you own",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Open"
            )
            .build()
      );
      cosmeticschatmodule$holder.actions.put(11, "tags");
      inventory.setItem(
         13,
         new ItemBuilder(Material.RED_DYE)
            .name("&#FF0000&lChat Color")
            .lore(
               "&8Description",
               "",
               "&#FF0000Information:",
               "&#FF0000| &fOpen the chat colors you own",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Open"
            )
            .build()
      );
      cosmeticschatmodule$holder.actions.put(13, "chatcolor");
      inventory.setItem(
         15,
         new ItemBuilder(Material.PAPER)
            .name("&#00E0FF&lName Gradients")
            .lore(
               "&8Description",
               "",
               "&#00E0FFInformation:",
               "&#00E0FF| &fOpen the name gradients you own",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Open"
            )
            .build()
      );
      cosmeticschatmodule$holder.actions.put(15, "namegradients");
      player.openInventory(inventory);
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         CosmeticsChatModule.Holder cosmeticschatmodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), CosmeticsChatModule.Holder.class);
         if (cosmeticschatmodule$holder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = cosmeticschatmodule$holder.actions.get(event.getSlot());
               if (s != null) {
                  this.openOwnedMenu(player, s);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (this.plugin.cosmetics() != null && player.isOnline()) {
            this.plugin.cosmetics().applyDisplay(player);
         }
      }, 20L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      if (this.plugin.modules().get(com.sharded.core.modules.chatformat.ChatFormatModule.class) != null
         && this.plugin.modules().get(com.sharded.core.modules.chatformat.ChatFormatModule.class).isEnabled()) {
         return;
      }
      if (this.plugin.cosmetics() != null && this.plugin.cosmetics().database() != null) {
         Player player = event.getPlayer();
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.plugin.cosmetics().database().get(player.getUniqueId());
         String s = cosmeticdatabase$playercosmetics.tagDisplay() == null ? "" : cosmeticdatabase$playercosmetics.tagDisplay();
         String s1 = cosmeticdatabase$playercosmetics.nameColor();
         String s2 = cosmeticdatabase$playercosmetics.chatColor();
         String s3 = PlainTextComponentSerializer.plainText().serialize(event.message());
         Component component = CosmeticService.looksLikeCommand(s3) ? event.message() : Text.c(CosmeticService.colorizeChat(s3, s2));
         event.message(component);
         Component component1 = this.formatChatLine(player, s, s1, component);
         event.renderer((source, sourceDisplayName, message, viewer) -> component1);
      }
   }

   private void openOwnedMenu(Player player, String action) {
      switch (action) {
         case "tags" -> {
            TagsModule tags = this.plugin.modules().get(TagsModule.class);
            if (tags != null && tags.isEnabled()) {
               tags.openOwned(player);
            }
         }
         case "chatcolor" -> {
            ChatColorModule colors = this.plugin.modules().get(ChatColorModule.class);
            if (colors != null && colors.isEnabled()) {
               colors.openOwned(player);
            }
         }
         case "namegradients" -> {
            NameGradientsModule gradients = this.plugin.modules().get(NameGradientsModule.class);
            if (gradients != null && gradients.isEnabled()) {
               gradients.openOwned(player);
            }
         }
         default -> {
         }
      }
   }

   private Component formatChatLine(Player player, String tag, String nameColor, Component message) {
      Component component = Component.empty();
      if (this.plugin.luckPerms() != null) {
         String s = this.plugin.luckPerms().prefix(player);
         if (s != null && !s.isBlank()) {
            component = component.append(Text.c(s.trim()));
            if (!s.endsWith(" ")) {
               component = component.append(Component.space());
            }
         }
      }

      if (tag != null && !tag.isBlank()) {
         component = component.append(Text.c(tag)).append(Component.space());
      }

      component = component.append(CosmeticService.nameComponent(player.getName(), nameColor));
      return component.append(Text.c(" &8▷ ")).append(message);
   }

   private static final class Holder implements InventoryHolder {
      private final Map<Integer, String> actions = new HashMap<>();
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
