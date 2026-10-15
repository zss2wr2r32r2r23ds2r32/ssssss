package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class LineEditor implements Listener {
   private final LeaderboardBoardsModule module;
   private final Map<UUID, LineEditor.Prompt> prompts = new ConcurrentHashMap<>();

   LineEditor(LeaderboardBoardsModule module) {
      this.module = module;
   }

   void open(Player player, BoardDefinition board) {
      LineEditor.Holder lineeditor$holder = new LineEditor.Holder(board.id);
      Inventory inventory = Bukkit.createInventory(lineeditor$holder, 54, Text.c("&8Editing " + board.id));
      lineeditor$holder.inventory = inventory;
      this.draw(inventory, board);
      TrackedInventories.track(inventory, lineeditor$holder);
      player.openInventory(inventory);
   }

   private void draw(Inventory inventory, BoardDefinition board) {
      inventory.clear();
      List<String> list = this.editable(board);

      for (int i = 0; i < Math.min(45, list.size()); i++) {
         String s = list.get(i);
         inventory.setItem(
            i,
            new ItemBuilder(Material.PAPER)
               .name("&#5C94FC&lLINE " + (i + 1))
               .lore(
                  List.of(
                     "&8Hologram",
                     "",
                     "&#5C94FC" + (s.isBlank() ? "&o(blank)" : s),
                     "",
                     "&7Click to edit in chat",
                     "&7Shift-click to delete",
                     "",
                     "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&lCLICK&r &x&F&F&B&A&0&0To Edit"
                  )
               )
               .hideAll()
               .build()
         );
      }

      inventory.setItem(
         45, new ItemBuilder(Material.LIME_CONCRETE).name("&#94FF00&lADD LINE").lore(List.of("&7Adds a line, then asks you to paste it.")).hideAll().build()
      );
      inventory.setItem(
         46,
         new ItemBuilder(Material.WRITABLE_BOOK)
            .name("&#FFEE00&lRESET LINES")
            .lore(List.of("&7Restore the default layout for this statistic."))
            .hideAll()
            .build()
      );
      inventory.setItem(
         49, new ItemBuilder(Material.BARRIER).name("&#FF0000&lCLOSE").lore(List.of("&7Close the editor. Lines already save as you edit.")).hideAll().build()
      );
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         LineEditor.Holder lineeditor$holder = TrackedInventories.lookup(event.getView().getTopInventory(), LineEditor.Holder.class);
         if (lineeditor$holder != null) {
            event.setCancelled(true);
            BoardDefinition boarddefinition = this.module.board(lineeditor$holder.boardId);
            if (boarddefinition == null) {
               player.closeInventory();
            } else {
               int i = event.getRawSlot();
               List<String> list = this.editable(boarddefinition);
               if (i == 49) {
                  player.closeInventory();
               } else if (i == 45) {
                  this.beginPrompt(player, boarddefinition, list.size());
               } else if (i == 46) {
                  this.module.resetLines(boarddefinition);
                  this.draw(event.getView().getTopInventory(), boarddefinition);
                  this.module.msg(player, "lines-reset", "board", boarddefinition.id);
               } else if (i >= 0 && i < list.size()) {
                  if (event.isShiftClick()) {
                     list.remove(i);
                     this.apply(boarddefinition, list);
                     this.draw(event.getView().getTopInventory(), boarddefinition);
                     this.module.msg(player, "line-removed", "board", boarddefinition.id);
                  } else {
                     this.beginPrompt(player, boarddefinition, i);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      TrackedInventories.untrack(event.getInventory(), LineEditor.Holder.class);
   }

   void beginPrompt(Player player, BoardDefinition board, int index) {
      this.prompts.put(player.getUniqueId(), new LineEditor.Prompt(board.id, index));
      player.closeInventory();
      this.module.msg(player, "line-prompt", "board", board.id, "line", String.valueOf(index + 1));
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onChat(AsyncChatEvent event) {
      if (this.prompts.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
         String s = PlainTextComponentSerializer.plainText().serialize(event.message());
         Bukkit.getScheduler().runTask(this.module.plugin(), () -> this.finish(event.getPlayer(), s));
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.prompts.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
         this.finish(event.getPlayer(), event.getMessage());
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.prompts.remove(event.getPlayer().getUniqueId());
   }

   private void finish(Player player, String raw) {
      LineEditor.Prompt lineeditor$prompt = this.prompts.remove(player.getUniqueId());
      if (lineeditor$prompt != null) {
         BoardDefinition boarddefinition = this.module.board(lineeditor$prompt.boardId);
         if (boarddefinition == null) {
            this.module.msg(player, "unknown-board", "board", lineeditor$prompt.boardId);
         } else {
            String s = raw == null ? "" : raw.trim();
            if (s.startsWith("/")) {
               s = s.substring(1).trim();
            }

            if (!s.equalsIgnoreCase("cancel") && !s.equalsIgnoreCase("stop") && !s.equalsIgnoreCase("exit")) {
               List<String> list = this.editable(boarddefinition);
               if (lineeditor$prompt.index >= list.size()) {
                  list.add(s);
               } else if (lineeditor$prompt.index >= 0) {
                  list.set(lineeditor$prompt.index, s);
               }

               this.apply(boarddefinition, list);
               this.module.msg(player, "line-set", "board", boarddefinition.id, "line", String.valueOf(lineeditor$prompt.index + 1));
               this.open(player, boarddefinition);
            } else {
               this.module.msg(player, "line-cancelled");
               this.open(player, boarddefinition);
            }
         }
      }
   }

   private void apply(BoardDefinition board, List<String> lines) {
      if (!board.title.isBlank() && !lines.isEmpty() && lines.getFirst().equals(board.title)) {
         board.lines = new ArrayList<>(lines.subList(1, lines.size()));
      } else {
         board.title = "";
         board.lines = new ArrayList<>(lines);
      }

      this.module.saveAndRespawn(board);
   }

   private List<String> editable(BoardDefinition board) {
      List<String> list = new ArrayList<>();
      if (board.title != null && !board.title.isBlank()) {
         list.add(board.title);
      }

      if (board.lines != null) {
         list.addAll(board.lines);
      }

      return list;
   }

   private static final class Holder implements InventoryHolder {
      private final String boardId;
      private Inventory inventory;

      private Holder(String boardId) {
         this.boardId = boardId;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record Prompt(String boardId, int index) {
   }
}
