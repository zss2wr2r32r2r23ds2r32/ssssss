package dev.shardedsmp.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class GlowManager {
    private static final String TEAM_NAME = "ssmp_yglow";
    private Team team;

    public void setup() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team existing = scoreboard.getTeam(TEAM_NAME);
        if (existing == null) {
            existing = scoreboard.registerNewTeam(TEAM_NAME);
        }
        existing.color(NamedTextColor.YELLOW);
        existing.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        existing.setCanSeeFriendlyInvisibles(false);
        this.team = existing;
    }

    public void glowEntity(Entity entity) {
        if (team == null) {
            setup();
        }
        team.addEntity(entity);
        entity.setGlowing(true);
    }

    public void setPlayerGlowing(Player player, boolean glowing) {
        if (team == null) {
            setup();
        }
        if (glowing) {
            if (!team.hasEntity(player)) {
                team.addEntity(player);
            }
            player.setGlowing(true);
        } else if (team.hasEntity(player)) {
            team.removeEntity(player);
            player.setGlowing(false);
        }
    }

    public void shutdown() {
        if (team == null) {
            return;
        }
        for (String entry : new java.util.HashSet<>(team.getEntries())) {
            team.removeEntry(entry);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isGlowing()) {
                player.setGlowing(false);
            }
        }
    }
}
