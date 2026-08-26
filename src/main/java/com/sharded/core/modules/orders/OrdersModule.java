package com.sharded.core.modules.orders;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.economy.EconomyModule;
import com.sharded.core.modules.economy.EconomyService;
import com.sharded.core.util.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.*;

public final class OrdersModule extends Module implements CommandExecutor, TabCompleter {
    private static final String ANNOUNCE_KEY = "orders-announce";
    enum PromptType { SEARCH, AMOUNT, PRICE }
    record Prompt(PromptType type, long expiresAt) {}

    private OrdersDatabase database;
    private OrdersGuiHandler guiHandler;
    private YamlConfiguration sounds;
    private final Map<String, OrdersGuiLayout> layouts = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new HashMap<>();
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);
    private BukkitTask expiryTask;

    public OrdersModule(ShardedCore plugin) { super(plugin, "orders"); }
    OrdersDatabase database() { return database; }
    OrdersGuiLayout layout(String name) { return layouts.getOrDefault(name, layouts.get("order")); }
    long deletionDays() { return config.getLong("deletion-days", 7); }
    EconomyService economy() { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? null : m.service(); }
    String formatMoney(long amount) { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? Numbers.format(amount) : m.formatBalance(amount); }

    @Override protected void onEnable() {
        try {
            database = new OrdersDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open orders database", e);
        }
        guiHandler = new OrdersGuiHandler(this);
        reloadResources();
        registerCommand("order", this);
        registerCommand("orderadmin", this);
        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {}, 20L*60, 20L*60*5);
    }
    @Override protected void onDisable() {
        if (expiryTask != null) expiryTask.cancel();
        prompts.clear(); layouts.clear();
        if (database != null) database.close();
        database = null; guiHandler = null;
    }
    void reloadResources() {
        for (String f : List.of("config.yml","messages.yml","sounds.yml","items/blacklisted.yml")) syncJarResource(f);
        for (String g : List.of("order","your_orders","new_order","deliver","edit","cancel_order","item_select"))
            layouts.put(g, new OrdersGuiLayout(syncJarResource("gui/"+g+".yml")));
        loadConfigs();
        sounds = YamlConfiguration.loadConfiguration(new File(moduleFolder(), "sounds.yml"));
        blacklist.clear();
        for (String raw : YamlConfiguration.loadConfiguration(new File(moduleFolder(), "items/blacklisted.yml")).getStringList("items")) {
            Material m = Material.matchMaterial(raw.toUpperCase(Locale.ROOT)); if (m != null) blacklist.add(m);
        }
    }
    boolean isBlacklisted(Material m) { return blacklist.contains(m); }
    void playSound(Player p, String key) {
        if (sounds == null || !sounds.getBoolean(key+".enabled", true)) return;
        try { p.playSound(p.getLocation(), Sound.valueOf(sounds.getString(key+".sound","UI_BUTTON_CLICK").toUpperCase(Locale.ROOT).replace('.','_')),
                (float)sounds.getDouble(key+".volume",1),(float)sounds.getDouble(key+".pitch",1)); } catch (IllegalArgumentException ignored) {}
    }
    void promptSearch(Player p) { prompts.put(p.getUniqueId(), new Prompt(PromptType.SEARCH, System.currentTimeMillis()+60000)); send(p,"search-prompt"); p.closeInventory(); }
    void promptAmount(Player p) { prompts.put(p.getUniqueId(), new Prompt(PromptType.AMOUNT, System.currentTimeMillis()+60000)); send(p,"ask-amount"); p.closeInventory(); }
    void promptPrice(Player p) { prompts.put(p.getUniqueId(), new Prompt(PromptType.PRICE, System.currentTimeMillis()+60000)); send(p,"ask-price"); p.closeInventory(); }

    void confirmOrder(Player player, OrdersGuiHandler.OrderDraft draft) {
        if (draft.item == null || draft.amount <= 0 || draft.pricePerItem <= 0) { send(player,"draft-incomplete"); playSound(player,"error"); return; }
        int maxAmount = config.getInt("max-amount", 3456);
        if (draft.amount < 1 || draft.amount > maxAmount) { send(player,"bad-amount","%max%",String.valueOf(maxAmount)); playSound(player,"error"); return; }
        long minP = Numbers.parseAmount(String.valueOf(config.getDouble("min-price", 1.0)));
        long maxP = Numbers.parseAmount(String.valueOf(config.getDouble("max-price", 10000000.0)));
        if (draft.pricePerItem < minP || draft.pricePerItem > maxP) { send(player,"bad-price","%min%",formatMoney(minP),"%max%",formatMoney(maxP)); playSound(player,"error"); return; }
        if (database.countOpenOrders(player.getUniqueId()) >= config.getInt("max-per-player", 5)) { send(player,"too-many","%limit%",String.valueOf(config.getInt("max-per-player",5))); playSound(player,"error"); return; }
        EconomyService eco = economy();
        if (eco == null) { send(player,"no-economy"); playSound(player,"error"); return; }
        long total = (long) draft.amount * draft.pricePerItem;
        if (!eco.take(player.getUniqueId(), total)) { send(player,"cannot-afford","%price%",formatMoney(total)); playSound(player,"error"); return; }
        long id = database.createOrder(player.getUniqueId(), ItemStackUtil.serialize(draft.item), draft.amount, draft.pricePerItem, System.currentTimeMillis(), System.currentTimeMillis()+config.getLong("expiry-days",7)*86400000L);
        if (id <= 0) { eco.add(player.getUniqueId(), total); send(player,"create-failed"); playSound(player,"error"); return; }
        send(player,"created","%amount%",String.valueOf(draft.amount),"%item%",ItemStackUtil.displayName(draft.item),"%price%",formatMoney(total));
        playSound(player,"click");
        if (config.getBoolean("announce", true)) Bukkit.getOnlinePlayers().stream().filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> plugin.stateStore().getBool(p.getUniqueId(), ANNOUNCE_KEY, true))
                .forEach(p -> send(p,"announce","%player%",player.getName(),"%amount%",String.valueOf(draft.amount),"%item%",ItemStackUtil.displayName(draft.item),"%price%",formatMoney(draft.pricePerItem)));
        guiHandler.clearDraft(player.getUniqueId()); guiHandler.openBoard(player, 0);
    }

    void processDelivery(Player player, long orderId, Inventory top) {
        OrdersDatabase.Order order = database.getOrder(orderId);
        if (order == null) { send(player,"gone","%id%",String.valueOf(orderId)); player.closeInventory(); return; }
        ItemStack template = ItemStackUtil.deserialize(order.itemBytes());
        if (template == null) { send(player,"gone","%id%",String.valueOf(orderId)); player.closeInventory(); return; }
        int matched = 0; List<ItemStack> bad = new ArrayList<>();
        for (int slot : layout("deliver").contentSlots()) {
            ItemStack stack = top.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            if (ItemStackUtil.similar(stack, template)) matched += stack.getAmount(); else bad.add(stack.clone());
            top.setItem(slot, null);
        }
        if (matched <= 0) { bad.forEach(s -> giveOrDrop(player,s)); send(player,"nothing-matched"); playSound(player,"error"); return; }
        int deliver = Math.min(matched, order.remaining());
        if (!database.deliver(orderId, deliver)) {
            ItemStack refund = template.clone(); refund.setAmount(matched); giveOrDrop(player, refund); bad.forEach(s -> giveOrDrop(player,s));
            send(player,"delivery-failed"); playSound(player,"error"); return;
        }
        EconomyService eco = economy(); long payout = deliver * order.pricePerItem();
        if (eco == null || payout <= 0 || !eco.canReceive(player.getUniqueId(), payout)) {
            database.deliver(orderId, -deliver);
            ItemStack refund = template.clone(); refund.setAmount(matched); giveOrDrop(player, refund); bad.forEach(s -> giveOrDrop(player,s));
            send(player,"payout-failed","%money%",formatMoney(payout)); playSound(player,"error"); return;
        }
        eco.add(player.getUniqueId(), payout);
        database.recordDelivery(player.getUniqueId(), payout, deliver * order.pricePerItem());
        send(player,"delivered","%amount%",String.valueOf(deliver),"%item%",ItemStackUtil.displayName(template),"%money%",formatMoney(payout));
        playSound(player,"delivered");
        Player owner = Bukkit.getPlayer(order.owner());
        if (owner != null) { send(owner,"owner-delivery","%player%",player.getName(),"%amount%",String.valueOf(deliver),"%item%",ItemStackUtil.displayName(template)); playSound(owner,"received"); }
        if (matched > deliver) { ItemStack extra = template.clone(); extra.setAmount(matched-deliver); giveOrDrop(player, extra); }
        bad.forEach(s -> giveOrDrop(player,s)); player.closeInventory(); guiHandler.openBoard(player, 0);
    }

    void collectItems(Player player, long orderId) {
        OrdersDatabase.Order order = database.getOrder(orderId);
        if (order == null || !order.owner().equals(player.getUniqueId())) { send(player,"not-yours"); return; }
        int available = order.deliveredAmount() - order.collectedAmount();
        if (available <= 0) { send(player,"nothing-to-collect"); playSound(player,"error"); return; }
        ItemStack template = ItemStackUtil.deserialize(order.itemBytes()); if (template == null) return;
        if (!database.collect(orderId, available)) { send(player,"delivery-failed"); return; }
        int left = available;
        while (left > 0) { int stack = Math.min(left, template.getMaxStackSize()); ItemStack give = template.clone(); give.setAmount(stack); giveOrDrop(player,give); left -= stack; }
        send(player,"collected","%amount%",String.valueOf(available),"%item%",ItemStackUtil.displayName(template)); playSound(player,"received");
    }

    void cancelOrder(Player player, long orderId) {
        OrdersDatabase.Order order = database.getOrder(orderId);
        if (order == null || !order.owner().equals(player.getUniqueId())) { send(player,"not-yours"); return; }
        if (order.isComplete()) { send(player,"already-closed"); return; }
        long refund = order.remaining() * order.pricePerItem();
        if (!database.deleteOrder(orderId)) { send(player,"cancel-failed"); playSound(player,"error"); return; }
        EconomyService eco = economy();
        if (refund > 0 && eco != null) eco.add(player.getUniqueId(), refund);
        send(player,"cancelled","%item%",ItemStackUtil.displayName(ItemStackUtil.deserialize(order.itemBytes())),"%money%",formatMoney(refund));
        playSound(player,"click"); player.closeInventory(); guiHandler.openYourOrders(player, 0);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        OrdersGuiHandler.OrdersGuiHolder holder = TrackedInventories.lookup(event.getView().getTopInventory(), OrdersGuiHandler.OrdersGuiHolder.class);
        if (holder == null) return;
        if (holder.type == OrdersGuiHandler.MenuType.DELIVER) {
            int raw = event.getRawSlot();
            if (raw >= 0 && raw < holder.inventory.getSize() && layout("deliver").contentSlots().contains(raw)) return;
            if (event.getClickedInventory() == event.getView().getBottomInventory()) return;
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            guiHandler.handleClick(player, holder, event.getSlot()); return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        OrdersGuiHandler.OrdersGuiHolder holder = TrackedInventories.lookup(event.getView().getTopInventory(), OrdersGuiHandler.OrdersGuiHolder.class);
        if (holder == null || holder.type != OrdersGuiHandler.MenuType.DELIVER) return;
        for (int raw : event.getRawSlots()) if (raw < holder.inventory.getSize() && !layout("deliver").contentSlots().contains(raw)) { event.setCancelled(true); return; }
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        OrdersGuiHandler.OrdersGuiHolder holder = TrackedInventories.untrack(event.getInventory(), OrdersGuiHandler.OrdersGuiHolder.class);
        if (holder == null) return;
        if (holder.type == OrdersGuiHandler.MenuType.DELIVER && event.getPlayer() instanceof Player player) {
            for (int slot : layout("deliver").contentSlots()) {
                ItemStack stack = event.getInventory().getItem(slot);
                if (stack != null && !stack.getType().isAir()) { giveOrDrop(player, stack); event.getInventory().setItem(slot, null); }
            }
        }
        guiHandler.handleClose(holder);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onChat(AsyncChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null || System.currentTimeMillis() > prompt.expiresAt()) return;
        event.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handlePrompt(event.getPlayer(), prompt.type(), msg));
    }
    private void handlePrompt(Player player, PromptType type, String message) {
        if (message.equalsIgnoreCase("cancel")) { guiHandler.openNewOrder(player); return; }
        switch (type) {
            case SEARCH -> guiHandler.setSearch(player, message);
            case AMOUNT -> { try { guiHandler.setDraftAmount(player, Integer.parseInt(message)); } catch (NumberFormatException e) { send(player,"bad-number","%value%",message); } }
            case PRICE -> { long p = Numbers.parseAmount(message); if (p <= 0) send(player,"bad-number","%value%",message); else guiHandler.setDraftPrice(player, p); }
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { prompts.remove(event.getPlayer().getUniqueId()); guiHandler.clearPlayer(event.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("orderadmin")) return handleAdmin(sender, args);
        if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
        if (args.length == 0) { guiHandler.openBoard(player,0); return true; }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mine","your","yours" -> { guiHandler.openYourOrders(player,0); yield true; }
            case "new" -> { if (args.length >= 3) { var d = guiHandler.draft(player); ItemStack h = player.getInventory().getItemInMainHand();
                if (h != null && !h.getType().isAir()) { d.item = h.clone(); d.item.setAmount(1); }
                d.amount = Integer.parseInt(args[1]); d.pricePerItem = Numbers.parseAmount(args[2]); confirmOrder(player,d); }
                else guiHandler.openNewOrder(player); yield true; }
            case "stats" -> { var s = database.stats(player.getUniqueId()); for (String line : rawList("stats","%placed%",String.valueOf(s.placed()),"%open%",String.valueOf(database.countOpenOrders(player.getUniqueId())),"%limit%",String.valueOf(config.getInt("max-per-player",5)),"%deliveries%",String.valueOf(s.deliveries()),"%earned%",formatMoney(s.earned()),"%spent%",formatMoney(s.spent()))) sender.sendMessage(Text.c(line)); yield true; }
            case "toggle" -> { boolean c = plugin.stateStore().getBool(player.getUniqueId(), ANNOUNCE_KEY, true); plugin.stateStore().setBool(player.getUniqueId(), ANNOUNCE_KEY, !c); send(player, c?"announce-off":"announce-on"); yield true; }
            case "help" -> { for (String line : rawList("help")) sender.sendMessage(Text.c(line)); yield true; }
            default -> { guiHandler.openBoard(player,0); yield true; }
        };
    }
    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharded.orders.admin")) { send(sender,"no-permission"); return true; }
        if (args.length == 0) { for (String line : rawList("admin-help")) sender.sendMessage(Text.c(line)); return true; }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> { send(sender,"admin-list","%amount%",String.valueOf(database.countAll()));
                for (var o : database.listOpenOrders()) sender.sendMessage(Text.c("&#FF3DE0#"+o.id()+" &7- "+OfflinePlayers.name(o.owner())+" | "+ItemStackUtil.displayName(ItemStackUtil.deserialize(o.itemBytes())))); yield true; }
            case "remove" -> { if (args.length < 2) { send(sender,"usage-remove"); yield true; }
                send(sender, database.deleteOrder(Numbers.parseAmount(args[1])) ? "removed" : "remove-failed", "%id%", args[1]); yield true; }
            case "migrate" -> { send(sender,"migrated","%amount%","0"); yield true; }
            default -> { for (String line : rawList("admin-help")) sender.sendMessage(Text.c(line)); yield true; }
        };
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("orderadmin")) {
            if (args.length == 1) return TabCompleteHelper.filter(args[0], "list","remove","migrate");
            return List.of();
        }
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "mine","new","stats","toggle","help");
        return List.of();
    }
}
