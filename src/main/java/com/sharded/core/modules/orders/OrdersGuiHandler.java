package com.sharded.core.modules.orders;

import com.sharded.core.util.ItemStackUtil;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import java.util.*;

final class OrdersGuiHandler {
    enum MenuType { BOARD, YOUR_ORDERS, NEW_ORDER, DELIVER, EDIT, CANCEL }
    enum SortMode { NEWEST, MOST_PAID, BEST_PER_ITEM, MOST_DELIVERED }
    enum FilterMode { ALL, BLOCKS, TOOLS, COMBAT, FOOD, POTIONS, BOOKS, INGREDIENTS, OTHER;
        boolean matches(Material m) {
            if (this == ALL) return true;
            String n = m.name();
            return switch (this) {
                case BLOCKS -> m.isBlock();
                case TOOLS -> n.contains("PICKAXE") || n.contains("AXE") || n.contains("SHOVEL") || n.contains("HOE") || n.contains("SWORD");
                case COMBAT -> n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("BOW");
                case FOOD -> m.isEdible();
                case POTIONS -> n.contains("POTION");
                case BOOKS -> n.contains("BOOK") || n.contains("MAP");
                case INGREDIENTS -> n.contains("INGOT") || n.contains("DUST") || n.contains("GEM");
                default -> true;
            };
        }
    }
    static final class OrdersGuiHolder implements InventoryHolder {
        final MenuType type; final long orderId; int page; Inventory inventory;
        OrdersGuiHolder(MenuType type, long orderId, int page) { this.type = type; this.orderId = orderId; this.page = page; }
        public Inventory getInventory() { return inventory; }
    }
    static final class OrderDraft { ItemStack item; int amount; long pricePerItem; }

    private final OrdersModule module;
    private final Map<UUID, SortMode> sortByPlayer = new HashMap<>();
    private final Map<UUID, FilterMode> filterByPlayer = new HashMap<>();
    private final Map<UUID, String> searchByPlayer = new HashMap<>();
    private final Map<UUID, OrderDraft> drafts = new HashMap<>();
    private final Set<Long> busyOrders = Collections.synchronizedSet(new HashSet<>());

    OrdersGuiHandler(OrdersModule module) { this.module = module; }
    void clearDraft(UUID uuid) { drafts.remove(uuid); }
    void clearPlayer(UUID uuid) { drafts.remove(uuid); busyOrders.removeIf(id -> { var o = module.database().getOrder(id); return o != null && o.owner().equals(uuid); }); }
    OrderDraft draft(Player p) { return drafts.computeIfAbsent(p.getUniqueId(), u -> new OrderDraft()); }

    void openBoard(Player player, int page) {
        OrdersGuiLayout layout = module.layout("order");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.BOARD, 0, page);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        List<OrdersDatabase.Order> orders = filteredOrders(player);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int pageSize = slots.length, maxPage = Math.max(0, (orders.size()-1)/pageSize);
        page = Math.max(0, Math.min(page, maxPage)); holder.page = page;
        for (int i = 0; i < slots.length && page*pageSize+i < orders.size(); i++) inv.setItem(slots[i], orderDisplayItem(layout, orders.get(page*pageSize+i)));
        place(layout, inv, "previous"); place(layout, inv, "next"); place(layout, inv, "refresh"); place(layout, inv, "search", searchPh(player));
        place(layout, inv, "new"); place(layout, inv, "mine-button"); place(layout, inv, "sort"); place(layout, inv, "filter");
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void openYourOrders(Player player, int page) {
        OrdersGuiLayout layout = module.layout("your_orders");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.YOUR_ORDERS, 0, page);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        List<OrdersDatabase.Order> orders = module.database().listOrdersByOwner(player.getUniqueId());
        int[] slots = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17};
        int pageSize = slots.length, maxPage = Math.max(0, (orders.size()-1)/pageSize);
        page = Math.max(0, Math.min(page, maxPage)); holder.page = page;
        for (int slot : layout.fillerSlots()) inv.setItem(slot, layout.filler().clone());
        for (int i = 0; i < slots.length && page*pageSize+i < orders.size(); i++) inv.setItem(slots[i], yourOrderItem(layout, orders.get(page*pageSize+i)));
        place(layout, inv, "new"); place(layout, inv, "back"); place(layout, inv, "previous"); place(layout, inv, "next");
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void openNewOrder(Player player) {
        OrderDraft draft = draft(player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (draft.item == null && hand != null && !hand.getType().isAir()) { draft.item = hand.clone(); draft.item.setAmount(1); }
        OrdersGuiLayout layout = module.layout("new_order");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.NEW_ORDER, 0, 0);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        Map<String,String> ph = draftPh(draft);
        place(layout, inv, "back"); place(layout, inv, "amount", ph); place(layout, inv, "price", ph); place(layout, inv, "extra", ph);
        if (draft.item != null) { ItemStack d = draft.item.clone(); d.setAmount(1); inv.setItem(layout.slot("item"), d); } else place(layout, inv, "item", ph);
        place(layout, inv, (draft.item != null && draft.amount > 0 && draft.pricePerItem > 0) ? "confirm" : "incomplete", ph);
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void openDeliver(Player player, long orderId) {
        OrdersDatabase.Order order = module.database().getOrder(orderId);
        if (order == null) { module.send(player, "gone", "%id%", String.valueOf(orderId)); return; }
        if (order.owner().equals(player.getUniqueId())) { module.send(player, "own-order"); return; }
        if (busyOrders.contains(orderId)) { module.send(player, "busy"); return; }
        busyOrders.add(orderId);
        OrdersGuiLayout layout = module.layout("deliver");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.DELIVER, orderId, 0);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        Map<String,String> ph = orderPh(order);
        for (int slot : layout.fillerSlots()) inv.setItem(slot, layout.filler().clone());
        place(layout, inv, "back"); place(layout, inv, "confirm", ph);
        inv.setItem(layout.section("info") != null ? layout.section("info").getInt("slot", 22) : 22, layout.orderItem(layout.section("info"), ph));
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void openEdit(Player player, long orderId) {
        OrdersDatabase.Order order = module.database().getOrder(orderId);
        if (order == null || !order.owner().equals(player.getUniqueId())) { module.send(player, "not-yours"); return; }
        OrdersGuiLayout layout = module.layout("edit");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.EDIT, orderId, 0);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        Map<String,String> ph = orderPh(order);
        for (int slot : layout.fillerSlots()) inv.setItem(slot, layout.filler().clone());
        String state = order.isComplete() ? "closed" : "open";
        inv.setItem(layout.orderSlot(), layout.orderItem(layout.section(state), ph));
        place(layout, inv, "cancel"); place(layout, inv, "collect"); place(layout, inv, "back");
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void openCancelConfirm(Player player, long orderId) {
        OrdersDatabase.Order order = module.database().getOrder(orderId);
        if (order == null || !order.owner().equals(player.getUniqueId())) { module.send(player, "not-yours"); return; }
        OrdersGuiLayout layout = module.layout("cancel_order");
        OrdersGuiHolder holder = new OrdersGuiHolder(MenuType.CANCEL, orderId, 0);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), Text.c(layout.title()));
        holder.inventory = inv;
        inv.setItem(layout.orderSlot(), layout.orderItem(layout.section("order"), orderPh(order)));
        place(layout, inv, "no"); place(layout, inv, "yes");
        TrackedInventories.track(inv, holder); player.openInventory(inv); module.playSound(player, "click");
    }

    void handleClick(Player player, OrdersGuiHolder holder, int slot) {
        switch (holder.type) {
            case BOARD -> handleBoard(player, holder, slot);
            case YOUR_ORDERS -> handleYour(player, holder, slot);
            case NEW_ORDER -> handleNew(player, slot);
            case DELIVER -> { if (slot == module.layout("deliver").slot("back")) { player.closeInventory(); openBoard(player,0); }
                else if (slot == module.layout("deliver").slot("confirm")) module.processDelivery(player, holder.orderId, player.getOpenInventory().getTopInventory()); }
            case EDIT -> { OrdersGuiLayout l = module.layout("edit");
                if (slot == l.slot("back")) openYourOrders(player,0);
                else if (slot == l.slot("cancel")) openCancelConfirm(player, holder.orderId);
                else if (slot == l.slot("collect")) { module.collectItems(player, holder.orderId); openEdit(player, holder.orderId); } }
            case CANCEL -> { OrdersGuiLayout l = module.layout("cancel_order");
                if (slot == l.slot("no")) openEdit(player, holder.orderId);
                else if (slot == l.slot("yes")) module.cancelOrder(player, holder.orderId); }
            default -> {}
        }
    }
    void handleClose(OrdersGuiHolder holder) { if (holder.type == MenuType.DELIVER) busyOrders.remove(holder.orderId); }

    private void handleBoard(Player player, OrdersGuiHolder holder, int slot) {
        OrdersGuiLayout l = module.layout("order");
        if (slot == l.slot("previous")) { openBoard(player, holder.page-1); return; }
        if (slot == l.slot("next")) { openBoard(player, holder.page+1); return; }
        if (slot == l.slot("refresh")) { openBoard(player, holder.page); return; }
        if (slot == l.slot("new")) { openNewOrder(player); return; }
        if (slot == l.slot("mine-button")) { openYourOrders(player,0); return; }
        if (slot == l.slot("search")) { module.promptSearch(player); return; }
        if (slot == l.slot("sort")) { cycleSort(player); openBoard(player, holder.page); return; }
        if (slot == l.slot("filter")) { cycleFilter(player); openBoard(player, holder.page); return; }
        List<OrdersDatabase.Order> orders = filteredOrders(player);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for (int i = 0; i < slots.length; i++) if (slots[i]==slot && holder.page*slots.length+i < orders.size()) { openDeliver(player, orders.get(holder.page*slots.length+i).id()); return; }
    }
    private void handleYour(Player player, OrdersGuiHolder holder, int slot) {
        OrdersGuiLayout l = module.layout("your_orders");
        if (slot == l.slot("new")) { openNewOrder(player); return; }
        if (slot == l.slot("back")) { openBoard(player,0); return; }
        if (slot == l.slot("previous")) { openYourOrders(player, holder.page-1); return; }
        if (slot == l.slot("next")) { openYourOrders(player, holder.page+1); return; }
        List<OrdersDatabase.Order> orders = module.database().listOrdersByOwner(player.getUniqueId());
        int[] slots = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17};
        for (int i = 0; i < slots.length; i++) if (slots[i]==slot && holder.page*slots.length+i < orders.size()) { openEdit(player, orders.get(holder.page*slots.length+i).id()); return; }
    }
    private void handleNew(Player player, int slot) {
        OrdersGuiLayout l = module.layout("new_order"); OrderDraft d = draft(player);
        if (slot == l.slot("back")) { openBoard(player,0); return; }
        if (slot == l.slot("item")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) { module.send(player,"hold-item"); module.playSound(player,"error"); return; }
            if (module.isBlacklisted(hand.getType())) { module.send(player,"not-orderable"); module.playSound(player,"error"); return; }
            d.item = hand.clone(); d.item.setAmount(1); openNewOrder(player); return;
        }
        if (slot == l.slot("amount")) { module.promptAmount(player); return; }
        if (slot == l.slot("price")) { module.promptPrice(player); return; }
        if (slot == l.slot("confirm")) module.confirmOrder(player, d);
    }

    private void cycleSort(Player p) { SortMode c = sortByPlayer.getOrDefault(p.getUniqueId(), SortMode.NEWEST); sortByPlayer.put(p.getUniqueId(), SortMode.values()[(c.ordinal()+1)%SortMode.values().length]); }
    private void cycleFilter(Player p) { FilterMode c = filterByPlayer.getOrDefault(p.getUniqueId(), FilterMode.ALL); filterByPlayer.put(p.getUniqueId(), FilterMode.values()[(c.ordinal()+1)%FilterMode.values().length]); }

    private List<OrdersDatabase.Order> filteredOrders(Player player) {
        List<OrdersDatabase.Order> orders = new ArrayList<>(module.database().listOpenOrders());
        FilterMode filter = filterByPlayer.getOrDefault(player.getUniqueId(), FilterMode.ALL);
        String search = searchByPlayer.getOrDefault(player.getUniqueId(), "").toLowerCase(Locale.ROOT);
        orders.removeIf(o -> { ItemStack it = ItemStackUtil.deserialize(o.itemBytes()); if (it == null) return true;
            if (!filter.matches(it.getType())) return true;
            if (!search.isBlank()) { String n = ItemStackUtil.displayName(it).toLowerCase(); String owner = OfflinePlayers.name(o.owner()).toLowerCase();
                return !n.contains(search) && !owner.contains(search); } return false; });
        SortMode sort = sortByPlayer.getOrDefault(player.getUniqueId(), SortMode.NEWEST);
        Comparator<OrdersDatabase.Order> cmp = switch (sort) {
            case NEWEST -> Comparator.comparingLong(OrdersDatabase.Order::createdAt).reversed();
            case MOST_PAID -> Comparator.<OrdersDatabase.Order>comparingLong(OrdersDatabase.Order::paidOut).reversed();
            case BEST_PER_ITEM -> Comparator.comparingLong(OrdersDatabase.Order::pricePerItem).reversed();
            case MOST_DELIVERED -> Comparator.comparingInt(OrdersDatabase.Order::deliveredAmount).reversed();
        };
        orders.sort(cmp); return orders;
    }

    private ItemStack orderDisplayItem(OrdersGuiLayout layout, OrdersDatabase.Order order) {
        ItemStack item = ItemStackUtil.deserialize(order.itemBytes()); if (item == null) item = new ItemStack(Material.BARRIER);
        item = item.clone(); item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), order.remaining())));
        Map<String,String> ph = orderPh(order); var meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Text.c(layout.raw("order.name", ph)));
            meta.lore(layout.loreList("order.lore", ph).stream().map(Text::c).toList()); item.setItemMeta(meta); }
        return item;
    }
    private ItemStack yourOrderItem(OrdersGuiLayout layout, OrdersDatabase.Order order) {
        ItemStack item = ItemStackUtil.deserialize(order.itemBytes()); if (item == null) item = new ItemStack(Material.BARRIER);
        item = item.clone(); item.setAmount(1);
        String state = order.isComplete() ? "closed" : "open"; Map<String,String> ph = orderPh(order); var meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Text.c(layout.raw(state+".name", ph)));
            meta.lore(layout.loreList(state+".lore", ph).stream().map(Text::c).toList());
            if (layout.section(state) != null && layout.section(state).getBoolean("glow", false)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            item.setItemMeta(meta); }
        return item;
    }
    Map<String,String> orderPh(OrdersDatabase.Order order) {
        ItemStack item = ItemStackUtil.deserialize(order.itemBytes());
        String itemName = item == null ? "Unknown" : ItemStackUtil.displayName(item);
        long rem = Math.max(0, order.expiresAt()-System.currentTimeMillis());
        long del = Math.max(0, order.expiresAt()+module.deletionDays()*86400000L-System.currentTimeMillis());
        return Map.of("player_name", OfflinePlayers.name(order.owner()), "item_name", itemName, "total_amount", String.valueOf(order.totalAmount()),
                "total_delivered", String.valueOf(order.deliveredAmount()), "price_per_item", module.formatMoney(order.pricePerItem()),
                "total_price", module.formatMoney(order.totalPrice()), "total_paid", module.formatMoney(order.paidOut()),
                "time_remaining", Text.time(rem/1000), "time_until_delete", Text.time(del/1000), "id", String.valueOf(order.id()));
    }
    private Map<String,String> draftPh(OrderDraft d) {
        String itemName = d.item == null ? module.layout("new_order").raw("none") : ItemStackUtil.displayName(d.item);
        return Map.of("item_name", itemName, "item", itemName, "current_amount", d.amount<=0?module.layout("new_order").raw("none"):String.valueOf(d.amount),
                "amount", d.amount<=0?module.layout("new_order").raw("none"):String.valueOf(d.amount),
                "current_price", d.pricePerItem<=0?module.layout("new_order").raw("none"):module.formatMoney(d.pricePerItem),
                "price", d.pricePerItem<=0?module.layout("new_order").raw("none"):module.formatMoney(d.pricePerItem),
                "total_price", module.formatMoney((long)d.amount*d.pricePerItem));
    }
    private Map<String,String> searchPh(Player player) {
        String s = searchByPlayer.getOrDefault(player.getUniqueId(), "");
        return Map.of("search", s.isBlank() ? module.layout("order").raw("no-search") : s);
    }
    void setSearch(Player player, String search) {
        if (search == null || search.isBlank() || search.equalsIgnoreCase("cancel")) { searchByPlayer.remove(player.getUniqueId()); module.send(player,"search-cleared"); }
        else { searchByPlayer.put(player.getUniqueId(), search); module.send(player,"search-set","%search%",search); }
        openBoard(player,0);
    }
    void setDraftAmount(Player player, int amount) { draft(player).amount = amount; openNewOrder(player); }
    void setDraftPrice(Player player, long price) { draft(player).pricePerItem = price; openNewOrder(player); }
    private void place(OrdersGuiLayout l, Inventory inv, String key) { var b = l.button(key); if (b != null) inv.setItem(b.slot(), b.item()); }
    private void place(OrdersGuiLayout l, Inventory inv, String key, Map<String,String> ph) { var b = l.button(key, ph); if (b != null) inv.setItem(b.slot(), b.item()); }
}
