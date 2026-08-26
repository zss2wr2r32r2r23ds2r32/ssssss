package com.shardedcore.modules.nametags;
import com.shardedcore.ShardedCore; import com.shardedcore.module.Module;
import org.bukkit.event.Listener; import com.shardedcore.util.*;
import org.bukkit.*; import org.bukkit.entity.*; import org.bukkit.event.*; import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType; import org.bukkit.scheduler.BukkitTask; import org.bukkit.util.Transformation;
import org.joml.Vector3f; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
public final class NametagsModule extends Module implements Listener {
 private final Map<UUID,List<UUID>> displays=new ConcurrentHashMap<>(); private NamespacedKey tagKey; private BukkitTask tick;
 public NametagsModule(ShardedCore p){super(p,"nametags");}
 public void enable(){tagKey=new NamespacedKey(plugin,"nametag"); tick=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,5,5); registerListener(this); for(Player p:Bukkit.getOnlinePlayers())spawn(p);}
 public void disable(){if(tick!=null)tick.cancel(); displays.keySet().forEach(this::remove); cleanup();}
 @EventHandler public void onJoin(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->spawn(e.getPlayer()),5);}
 @EventHandler public void onQuit(PlayerQuitEvent e){remove(e.getPlayer().getUniqueId());}
 private void tick(){for(Player p:Bukkit.getOnlinePlayers()) if(!displays.containsKey(p.getUniqueId()))spawn(p); else update(p);}
 private void spawn(Player p){remove(p.getUniqueId()); List<String> lines=config.getStringList("lines"); if(lines.isEmpty())lines=List.of("%luckperms_prefix%%player_name%");
  double h=config.getDouble("height",2.35), gap=config.getDouble("line-gap",0.28); List<UUID> ids=new ArrayList<>();
  for(int i=0;i<lines.size();i++){Location at=p.getLocation().clone().add(0,h-i*gap,0); TextDisplay d=p.getWorld().spawn(at,TextDisplay.class,en->{en.setBillboard(Display.Billboard.CENTER); en.setPersistent(false); en.getPersistentDataContainer().set(tagKey,PersistentDataType.STRING,p.getUniqueId().toString());});
   d.text(ColorUtil.parse(Text.applyPlaceholders(lines.get(i),p))); ids.add(d.getUniqueId()); if(!config.getBoolean("hide-own",false)) p.showEntity(plugin,d); for(Player v:Bukkit.getOnlinePlayers()) if(!v.equals(p)) v.showEntity(plugin,d);} displays.put(p.getUniqueId(),ids);}
 private void update(Player p){List<UUID> ids=displays.get(p.getUniqueId()); if(ids==null)return; List<String> lines=config.getStringList("lines"); double h=config.getDouble("height",2.35), gap=config.getDouble("line-gap",0.28);
  for(int i=0;i<ids.size();i++){var e=Bukkit.getEntity(ids.get(i)); if(!(e instanceof TextDisplay d)){spawn(p);return;} d.teleport(p.getLocation().clone().add(0,h-i*gap,0)); d.text(ColorUtil.parse(Text.applyPlaceholders(lines.get(i),p)));}}
 private void remove(UUID id){List<UUID> ids=displays.remove(id); if(ids==null)return; for(UUID u:ids){var e=Bukkit.getEntity(u); if(e!=null)e.remove();}}
}