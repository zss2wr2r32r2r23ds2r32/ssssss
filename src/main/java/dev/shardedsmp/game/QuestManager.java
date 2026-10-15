package dev.shardedsmp.game;

import dev.shardedsmp.GamePhase;
import dev.shardedsmp.ShardedSMP;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class QuestManager {
    private final ShardedSMP plugin;
    private BossBar bossBar;

    public QuestManager(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    public void updateBossBar() {
        GameManager game = plugin.game();
        if (game.phase() != GamePhase.PHASE_3 || game.endOpen()) {
            hide();
            return;
        }
        float progress = Math.min(1.0f, game.diamondsMined() / (float) Math.max(1, game.diamondsNeeded()));
        Component name = dev.shardedsmp.util.ColorUtil.color("&bCommunity Quest &7- &f"
                + game.diamondsMined() + "&7/&f" + game.diamondsNeeded() + " Diamonds");
        if (bossBar == null) {
            bossBar = BossBar.bossBar(name, progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        } else {
            bossBar.name(name);
            bossBar.progress(progress);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    public void hide() {
        if (bossBar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
        bossBar = null;
    }

    public void showTo(Player player) {
        if (bossBar != null) {
            player.showBossBar(bossBar);
        }
    }
}
