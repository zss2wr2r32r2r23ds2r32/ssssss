package com.shardedcore.modules.crates;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.event.Listener;
import com.shardedcore.util.TabCompleteHelper;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CratesModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private CrateStorage storage;
    private CratesGuiHandler guiHandler;
    private final Map<UUID, String> pendingDelete = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingOpen = new ConcurrentHashMap<>();

    public CratesModule(ShardedCore plugin) { super(plugin, "crates"); }

    @Override
    public void enable() {
        File dir = new File(moduleFolder, "crates");
        if (!dir.exists()) dir.mkdirs();
        storage = new CrateStorage(plugin, dir);
        storage.loadAll();
        guiHandler = new CratesGuiHandler(this);
        registerListener(this);
        registerCommand("crate", this);
    }

    @Override public void disable() { pendingDelete.clear(); pendingOpen.clear(); cleanup(); }

    CrateStorage storage() { return storage; }

    void setPendingDelete(UUID uuid, String id) { pendingDelete.put(uuid, id); }
    String takePendingDelete(UUID uuid) { return pendingDelete.remove(uuid); }
    void setPendingOpen(UUID uuid, String id) { pendingOpen.put(uuid, id); }
    String takePendingOpen(UUID uuid) { return pendingOpen.remove(uuid); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("shardedcore.command.crate")) guiHandler.openList(p);
            else send(sender, args.length == 0 && !(sender instanceof Player) ? "players-only" : "usage");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> { if (!sender.hasPermission("shardedcore.crates.admin")) { send(sender,"no-permission"); yield true; }
                if (args.length<2){send(sender,"create-usage"); yield true;} storage.create(args[1].toLowerCase()); send(sender,"created","crate",args[1]); yield true; }
            case "edit" -> { if (!(sender instanceof Player p)){send(sender,"players-only"); yield true;}
                if (args.length<2){send(p,"edit-usage"); yield true;} guiHandler.openEditor(p,args[1].toLowerCase()); yield true; }
            case "delete" -> { if (!sender.hasPermission("shardedcore.crates.admin")){send(sender,"no-permission"); yield true;}
                if (args.length<2){send(sender,"delete-usage"); yield true;}
                if (sender instanceof Player p){setPendingDelete(p.getUniqueId(),args[1]); guiHandler.openDeleteConfirm(p,args[1]);}
                else {storage.delete(args[1]); send(sender,"deleted","crate",args[1]);} yield true; }
            case "key" -> { if (!(sender instanceof Player p)){send(sender,"players-only"); yield true;}
                if (args.length<3){send(p,"key-usage"); yield true;} int amt=1; try{amt=Integer.parseInt(args[2]);}catch(Exception ignored){}
                p.getInventory().addItem(storage.createKey(args[1].toLowerCase(),amt)); send(p,"key-given","crate",args[1],"amount",String.valueOf(amt)); yield true; }
            case "list" -> { for (String id:storage.listIds()) send(sender,"list-line","crate",id); yield true; }
            case "confirm" -> { if (!(sender instanceof Player p)){send(sender,"players-only"); yield true;}
                String del=takePendingDelete(p.getUniqueId()); if(del!=null){storage.delete(del); send(p,"deleted","crate",del); yield true;}
                String open=takePendingOpen(p.getUniqueId()); if(open!=null){guiHandler.finishOpen(p,open); yield true;}
                send(p,"nothing-to-confirm"); yield true; }
            case "unplace" -> { if (args.length<2){send(sender,"unplace-usage"); yield true;} storage.unplace(args[1]); send(sender,"unplaced","crate",args[1]); yield true; }
            default -> { send(sender,"usage"); yield true; }
        };
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null) return;
        String id = storage.crateAt(event.getClickedBlock().getLocation());
        if (id==null) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (storage.hasKey(p,id)) { setPendingOpen(p.getUniqueId(),id); guiHandler.openOpenConfirm(p,id); }
        else guiHandler.openPreview(p,id);
    }

    @EventHandler public void onClick(InventoryClickEvent e){ guiHandler.handleClick(e); }

    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(args.length==1) return TabCompleteHelper.filter(java.util.List.of("create","edit","delete","key","list","confirm","unplace"), args[0]);
        if(args.length==2&&!args[0].equalsIgnoreCase("create")) return TabCompleteHelper.filter(storage.listIds(), args[1]);
        return List.of();
    }
}
