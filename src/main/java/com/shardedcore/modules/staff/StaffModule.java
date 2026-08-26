package com.shardedcore.modules.staff;
import com.shardedcore.ShardedCore; import com.shardedcore.module.Module; import com.shardedcore.util.OfflinePlayers;
import org.bukkit.*; import org.bukkit.command.*; import org.bukkit.entity.Player; import java.util.List;
public final class StaffModule extends Module implements CommandExecutor, TabCompleter {
 public StaffModule(ShardedCore p){super(p,"staff");}
 public void enable(){registerCommand("tp",this);} public void disable(){cleanup();}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){ if(!(s instanceof Player p)){send(s,"players-only");return true;} if(!p.hasPermission("shardedcore.command.tp")){send(p,"no-permission");return true;} if(a.length<1){send(p,"usage");return true;} Player t=Bukkit.getPlayerExact(a[0]); if(t==null){send(p,"player-not-found","player",a[0]);return true;} p.teleportAsync(t.getLocation()); send(p,"teleported","player",t.getName()); return true;}
 public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){ return a.length==1&&s.hasPermission("shardedcore.command.tp")?OfflinePlayers.onlinePlayers(a[0]):List.of(); }
}