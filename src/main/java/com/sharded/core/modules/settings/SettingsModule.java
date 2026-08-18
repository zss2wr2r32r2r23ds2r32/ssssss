package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import com.sharded.core.gui.GuiListener;
import com.sharded.core.gui.GuiManager;
import com.sharded.core.module.Module;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.util.CommandOverride;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/** /settings - overrides other plugins; opens gui.yml settings menu. */
public final class SettingsModule extends Module implements CommandExecutor {

    private GuiManager guiManager;

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    protected void onEnable() {
        guiManager = new GuiManager(plugin);
        File guiFile = new File(moduleFolder(), "gui.yml");
        if (!guiFile.exists()) plugin.saveResource("modules/settings/gui.yml", false);
        guiManager.loadFolder(moduleFolder());

        guiManager.registerAction("toggle_chat", player -> {
            ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
            if (chat != null && chat.isEnabled()) chat.setChatEnabled(player, !chat.isChatEnabled(player));
        });
        guiManager.registerAction("toggle_msg", player -> {
            PrivateMessagesModule pms = plugin.modules().get(PrivateMessagesModule.class);
            if (pms != null && pms.isEnabled()) pms.setMsgEnabled(player, !pms.isMsgEnabled(player));
        });
        guiManager.registerAction("toggle_nightvision", player -> {
            if (!player.hasPermission("sharded.nightvision.use")) {
                send(player, "no-permission");
                return;
            }
            NightVisionModule nv = plugin.modules().get(NightVisionModule.class);
            if (nv != null && nv.isEnabled()) nv.setNightVision(player, !nv.isNightVisionEnabled(player));
        });

        registerListener(new GuiListener(guiManager));
        CommandOverride.takeOver(plugin, "settings", this, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.settings.use")) {
            send(player, "no-permission");
            return true;
        }
        guiManager.open(player, "gui");
        return true;
    }
}
