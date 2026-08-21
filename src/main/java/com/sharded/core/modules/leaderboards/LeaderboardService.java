package com.sharded.core.modules.leaderboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.teams.TeamDatabase;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds sorted leaderboard rows from plugin data sources. */
final class LeaderboardService {

    record Entry(String key, String displayName, long value, UUID uuid) {
    }

    private final ShardedCore plugin;
    private final YamlConfiguration config;

    LeaderboardService(ShardedCore plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    List<Entry> entries(String type) {
        return switch (type.toLowerCase()) {
            case "tokens", "token" -> tokenEntries();
            case "kills", "kill" -> statEntries(Statistic.PLAYER_KILLS);
            case "deaths", "death" -> statEntries(Statistic.DEATHS);
            case "playtime", "time" -> statEntries(Statistic.PLAY_ONE_MINUTE);
            case "killstreaks", "killstreak", "streak" -> killstreakEntries();
            case "teams", "team" -> teamEntries();
            default -> List.of();
        };
    }

    int rankOf(String type, UUID uuid) {
        List<Entry> list = entries(type);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid() != null && list.get(i).uuid().equals(uuid)) return i + 1;
        }
        return -1;
    }

    int teamRank(int teamId) {
        List<Entry> list = teamEntries();
        for (int i = 0; i < list.size(); i++) {
            if (String.valueOf(teamId).equals(list.get(i).key())) return i + 1;
        }
        return -1;
    }

    Entry teamEntry(int teamId) {
        TeamsModule teams = plugin.modules().get(TeamsModule.class);
        if (teams == null) return null;
        TeamDatabase db = teams.database();
        TeamDatabase.Team team = db.getTeamById(teamId);
        if (team == null) return null;
        long score = teamScore(team.id(), db);
        return new Entry(String.valueOf(teamId), team.name(), score, team.leaderUuid());
    }

    private List<Entry> tokenEntries() {
        TokensModule module = plugin.modules().get(TokensModule.class);
        if (module == null) return List.of();
        TokenDatabase db = module.database();
        if (db == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (TokenDatabase.LeaderEntry row : db.top(config.getInt("fetch-limit", 5000))) {
            out.add(new Entry(row.uuid().toString(), OfflinePlayers.name(row.uuid()), row.value(), row.uuid()));
        }
        return out;
    }

    private List<Entry> killstreakEntries() {
        KillstreaksModule module = plugin.modules().get(KillstreaksModule.class);
        if (module == null || module.database() == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (KillstreakDatabase.LeaderEntry row : module.database().topBest(config.getInt("fetch-limit", 5000))) {
            out.add(new Entry(row.uuid().toString(), OfflinePlayers.name(row.uuid()), row.value(), row.uuid()));
        }
        return out;
    }

    private List<Entry> statEntries(Statistic stat) {
        List<Entry> out = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getUniqueId() == null) continue;
            try {
                long value = offline.getStatistic(stat);
                if (stat == Statistic.PLAY_ONE_MINUTE) value = Text.ticksToMinutes(value);
                if (value <= 0) continue;
                out.add(new Entry(offline.getUniqueId().toString(), OfflinePlayers.name(offline.getUniqueId()), value, offline.getUniqueId()));
            } catch (IllegalStateException | UnsupportedOperationException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(Entry::value).reversed());
        int limit = config.getInt("fetch-limit", 5000);
        return out.size() <= limit ? out : out.subList(0, limit);
    }

    private List<Entry> teamEntries() {
        TeamsModule teams = plugin.modules().get(TeamsModule.class);
        if (teams == null) return List.of();
        TeamDatabase db = teams.database();
        List<Entry> out = new ArrayList<>();
        for (TeamDatabase.Team team : db.listTeams()) {
            out.add(new Entry(String.valueOf(team.id()), team.name(), teamScore(team.id(), db), team.leaderUuid()));
        }
        out.sort(Comparator.comparingLong(Entry::value).reversed());
        return out;
    }

    private long teamScore(int teamId, TeamDatabase db) {
        long tokenWeight = config.getLong("teams.token-weight", 1L);
        long killWeight = config.getLong("teams.kill-weight", 100L);
        long hourWeight = config.getLong("teams.playtime-hour-weight", 50L);
        long tokens = 0;
        int kills = 0;
        long playtime = 0;
        TokensModule tokenModule = plugin.modules().get(TokensModule.class);
        for (TeamDatabase.Member member : db.getMembers(teamId)) {
            kills += member.kills();
            playtime += member.playtimeMs();
            if (tokenModule != null && tokenModule.service() != null) {
                tokens += tokenModule.service().getBalance(member.uuid());
            }
        }
        long hours = playtime / 3_600_000L;
        return tokens * tokenWeight + kills * killWeight + hours * hourWeight;
    }

    String formatValue(String type, long value) {
        return switch (type.toLowerCase()) {
            case "playtime", "time" -> Text.formatPlaytime(value);
            default -> String.valueOf(value);
        };
    }

    StatsSnapshot statsFor(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        long kills = safeStat(offline, Statistic.PLAYER_KILLS);
        long deaths = safeStat(offline, Statistic.DEATHS);
        long playMinutes = Text.ticksToMinutes(safeStat(offline, Statistic.PLAY_ONE_MINUTE));
        long tokens = 0;
        TokensModule tokensModule = plugin.modules().get(TokensModule.class);
        if (tokensModule != null && tokensModule.service() != null) {
            tokens = tokensModule.service().getBalance(uuid);
        }
        int bestStreak = 0;
        KillstreaksModule ks = plugin.modules().get(KillstreaksModule.class);
        if (ks != null && ks.database() != null) bestStreak = ks.database().getBest(uuid);
        String teamName = config.getString("stats.no-team", "None");
        TeamsModule teams = plugin.modules().get(TeamsModule.class);
        if (teams != null && teams.database() != null) {
            Integer teamId = teams.database().getTeamId(uuid);
            if (teamId != null) {
                TeamDatabase.Team team = teams.database().getTeamById(teamId);
                if (team != null) teamName = team.name();
            }
        }
        String prefix = plugin.luckPerms().prefix(uuid);
        return new StatsSnapshot(OfflinePlayers.name(uuid), prefix, kills, deaths, playMinutes, tokens, bestStreak, teamName);
    }

    record StatsSnapshot(String name, String prefix, long kills, long deaths, long playMinutes,
                         long tokens, int bestStreak, String team) {
    }

    private static long safeStat(OfflinePlayer player, Statistic stat) {
        try {
            return player.getStatistic(stat);
        } catch (Exception e) {
            return 0;
        }
    }
}
