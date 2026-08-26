package com.shardedcore.modules.deathmessages;
import com.shardedcore.ShardedCore; import com.shardedcore.module.Module;
import org.bukkit.event.Listener;
import com.shardedcore.util.*; import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player; import org.bukkit.event.*; import org.bukkit.event.entity.*;
public final class DeathMessagesModule extends Module implements Listener {
 public DeathMessagesModule(ShardedCore p){super(p,"death-messages");}
 public void enable(){registerListener(this);} public void disable(){cleanup();}
 @EventHandler(priority=EventPriority.HIGH) public void onDeath(PlayerDeathEvent e){
  Player p=e.getEntity(); e.deathMessage(null);
  String msg=resolve(p,e); if(msg==null||msg.isBlank())return;
  msg=Text.apply(msg,"%player%",p.getName(),"%rank%",LuckPermsHelper.prefix(p),"%killer%",p.getKiller()==null?"":p.getKiller().getName());
  var c=ColorUtil.parse(msg); for(Player v:plugin.getServer().getOnlinePlayers()) if(PlayerToggles.deathMessages(v)) v.sendMessage(c);
 }
 private String resolve(Player p,PlayerDeathEvent e){
  var causes=config.getConfigurationSection("causes");
  if(causes!=null){ if(p.getKiller()!=null){String k=causes.getString("PLAYER_KILL"); if(k!=null&&!k.isBlank())return k;}
   var lc=e.getEntity().getLastDamageCause(); if(lc!=null){String s=causes.getString(lc.getCause().name()); if(s!=null&&!s.isBlank())return s;}
   String d=causes.getString("default"); if(d!=null&&!d.isBlank())return d;}
  var formats=config.getConfigurationSection("formats"); if(formats==null)return null;
  ConfigurationSection best=null; int pri=Integer.MIN_VALUE;
  for(String k:formats.getKeys(false)){var f=formats.getConfigurationSection(k); if(f==null)continue;
   String perm=f.getString("permission",""); if(!perm.isEmpty()&&!p.hasPermission(perm))continue;
   int p2=f.getInt("priority",0); if(p2>pri){pri=p2;best=f;}}
  if(best==null)return null; return p.getKiller()!=null?best.getString("killed-by-player",best.getString("death","")):best.getString("death","");
}}