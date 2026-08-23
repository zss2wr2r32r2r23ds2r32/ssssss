package com.shardedmc.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Standard Minecraft/Paper-style server.properties loader.
 */
public final class ServerProperties {

    private String motd = "A ShardedMC High-Performance Server";
    private int serverPort = 25565;
    private int maxPlayers = 100;
    private boolean onlineMode = true;
    private String levelName = "world";
    private String levelSeed = "";
    private String levelType = "minecraft\\:normal";
    private int viewDistance = 10;
    private int simulationDistance = 10;
    private boolean hardcore = false;
    private boolean pvp = true;
    private int spawnProtection = 16;
    private int maxTickTime = 60000;
    private int maxWorldSize = 29999984;
    private String gamemode = "survival";
    private boolean forceGamemode = false;
    private boolean allowNether = true;
    private boolean enableCommandBlock = false;
    private boolean spawnMonsters = true;
    private boolean spawnAnimals = true;
    private boolean spawnNpcs = true;
    private boolean whiteList = false;
    private boolean enforceWhitelist = false;
    private int playerIdleTimeout = 0;
    private String resourcePack = "";
    private boolean useNativeTransport = true;
    private boolean enableJmxMonitoring = false;
    private boolean enableStatus = true;
    private boolean hideOnlinePlayers = false;
    private int entityBroadcastRangePercentage = 100;
    private String textFilteringConfig = "";
    private int opPermissionLevel = 4;
    private int functionPermissionLevel = 2;
    private int rateLimit = 0;
    private boolean syncChunkWrites = true;
    private boolean enableQuery = false;
    private int queryPort = 25565;
    private boolean preventProxyConnections = false;
    private boolean logIps = true;

    public static ServerProperties load(Path path) throws IOException {
        if (!Files.exists(path)) {
            ServerProperties defaults = defaults();
            defaults.save(path);
            return defaults;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        ServerProperties sp = new ServerProperties();
        sp.motd = props.getProperty("motd", sp.motd);
        sp.serverPort = Integer.parseInt(props.getProperty("server-port", "25565"));
        sp.maxPlayers = Integer.parseInt(props.getProperty("max-players", "100"));
        sp.onlineMode = Boolean.parseBoolean(props.getProperty("online-mode", "true"));
        sp.levelName = props.getProperty("level-name", sp.levelName);
        sp.levelSeed = props.getProperty("level-seed", sp.levelSeed);
        sp.levelType = props.getProperty("level-type", sp.levelType);
        sp.viewDistance = Integer.parseInt(props.getProperty("view-distance", "10"));
        sp.simulationDistance = Integer.parseInt(props.getProperty("simulation-distance", "10"));
        sp.hardcore = Boolean.parseBoolean(props.getProperty("hardcore", "false"));
        sp.pvp = Boolean.parseBoolean(props.getProperty("pvp", "true"));
        sp.spawnProtection = Integer.parseInt(props.getProperty("spawn-protection", "16"));
        sp.maxTickTime = Integer.parseInt(props.getProperty("max-tick-time", "60000"));
        sp.maxWorldSize = Integer.parseInt(props.getProperty("max-world-size", "29999984"));
        sp.gamemode = props.getProperty("gamemode", sp.gamemode);
        sp.forceGamemode = Boolean.parseBoolean(props.getProperty("force-gamemode", "false"));
        sp.allowNether = Boolean.parseBoolean(props.getProperty("allow-nether", "true"));
        sp.enableCommandBlock = Boolean.parseBoolean(props.getProperty("enable-command-block", "false"));
        sp.spawnMonsters = Boolean.parseBoolean(props.getProperty("spawn-monsters", "true"));
        sp.spawnAnimals = Boolean.parseBoolean(props.getProperty("spawn-animals", "true"));
        sp.spawnNpcs = Boolean.parseBoolean(props.getProperty("spawn-npcs", "true"));
        sp.whiteList = Boolean.parseBoolean(props.getProperty("white-list", "false"));
        sp.enforceWhitelist = Boolean.parseBoolean(props.getProperty("enforce-whitelist", "false"));
        sp.playerIdleTimeout = Integer.parseInt(props.getProperty("player-idle-timeout", "0"));
        sp.resourcePack = props.getProperty("resource-pack", "");
        sp.useNativeTransport = Boolean.parseBoolean(props.getProperty("use-native-transport", "true"));
        sp.enableJmxMonitoring = Boolean.parseBoolean(props.getProperty("enable-jmx-monitoring", "false"));
        sp.enableStatus = Boolean.parseBoolean(props.getProperty("enable-status", "true"));
        sp.hideOnlinePlayers = Boolean.parseBoolean(props.getProperty("hide-online-players", "false"));
        sp.entityBroadcastRangePercentage = Integer.parseInt(
                props.getProperty("entity-broadcast-range-percentage", "100"));
        sp.textFilteringConfig = props.getProperty("text-filtering-config", "");
        sp.opPermissionLevel = Integer.parseInt(props.getProperty("op-permission-level", "4"));
        sp.functionPermissionLevel = Integer.parseInt(props.getProperty("function-permission-level", "2"));
        sp.rateLimit = Integer.parseInt(props.getProperty("rate-limit", "0"));
        sp.syncChunkWrites = Boolean.parseBoolean(props.getProperty("sync-chunk-writes", "true"));
        sp.enableQuery = Boolean.parseBoolean(props.getProperty("enable-query", "false"));
        sp.queryPort = Integer.parseInt(props.getProperty("query.port", "25565"));
        sp.preventProxyConnections = Boolean.parseBoolean(props.getProperty("prevent-proxy-connections", "false"));
        sp.logIps = Boolean.parseBoolean(props.getProperty("log-ips", "true"));
        return sp;
    }

    public static ServerProperties defaults() {
        return new ServerProperties();
    }

    public void save(Path path) throws IOException {
        Properties props = new Properties();
        props.setProperty("motd", motd);
        props.setProperty("server-port", String.valueOf(serverPort));
        props.setProperty("max-players", String.valueOf(maxPlayers));
        props.setProperty("online-mode", String.valueOf(onlineMode));
        props.setProperty("level-name", levelName);
        props.setProperty("level-seed", levelSeed);
        props.setProperty("level-type", levelType);
        props.setProperty("view-distance", String.valueOf(viewDistance));
        props.setProperty("simulation-distance", String.valueOf(simulationDistance));
        props.setProperty("hardcore", String.valueOf(hardcore));
        props.setProperty("pvp", String.valueOf(pvp));
        props.setProperty("spawn-protection", String.valueOf(spawnProtection));
        props.setProperty("max-tick-time", String.valueOf(maxTickTime));
        props.setProperty("max-world-size", String.valueOf(maxWorldSize));
        props.setProperty("gamemode", gamemode);
        props.setProperty("force-gamemode", String.valueOf(forceGamemode));
        props.setProperty("allow-nether", String.valueOf(allowNether));
        props.setProperty("enable-command-block", String.valueOf(enableCommandBlock));
        props.setProperty("spawn-monsters", String.valueOf(spawnMonsters));
        props.setProperty("spawn-animals", String.valueOf(spawnAnimals));
        props.setProperty("spawn-npcs", String.valueOf(spawnNpcs));
        props.setProperty("white-list", String.valueOf(whiteList));
        props.setProperty("enforce-whitelist", String.valueOf(enforceWhitelist));
        props.setProperty("player-idle-timeout", String.valueOf(playerIdleTimeout));
        props.setProperty("resource-pack", resourcePack);
        props.setProperty("use-native-transport", String.valueOf(useNativeTransport));
        props.setProperty("enable-jmx-monitoring", String.valueOf(enableJmxMonitoring));
        props.setProperty("enable-status", String.valueOf(enableStatus));
        props.setProperty("hide-online-players", String.valueOf(hideOnlinePlayers));
        props.setProperty("entity-broadcast-range-percentage", String.valueOf(entityBroadcastRangePercentage));
        props.setProperty("text-filtering-config", textFilteringConfig);
        props.setProperty("op-permission-level", String.valueOf(opPermissionLevel));
        props.setProperty("function-permission-level", String.valueOf(functionPermissionLevel));
        props.setProperty("rate-limit", String.valueOf(rateLimit));
        props.setProperty("sync-chunk-writes", String.valueOf(syncChunkWrites));
        props.setProperty("enable-query", String.valueOf(enableQuery));
        props.setProperty("query.port", String.valueOf(queryPort));
        props.setProperty("prevent-proxy-connections", String.valueOf(preventProxyConnections));
        props.setProperty("log-ips", String.valueOf(logIps));
        try (Writer writer = Files.newBufferedWriter(path)) {
            props.store(writer, "ShardedMC Server Properties");
        }
    }

    public String getMotd() {
        return motd;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public String getLevelName() {
        return levelName;
    }

    public String getLevelSeed() {
        return levelSeed;
    }

    public String getLevelType() {
        return levelType;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public int getSimulationDistance() {
        return simulationDistance;
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public boolean isPvp() {
        return pvp;
    }

    public int getSpawnProtection() {
        return spawnProtection;
    }

    public int getMaxTickTime() {
        return maxTickTime;
    }

    public int getMaxWorldSize() {
        return maxWorldSize;
    }

    public String getGamemode() {
        return gamemode;
    }

    public boolean isForceGamemode() {
        return forceGamemode;
    }

    public boolean isAllowNether() {
        return allowNether;
    }

    public boolean isEnableCommandBlock() {
        return enableCommandBlock;
    }

    public boolean isSpawnMonsters() {
        return spawnMonsters;
    }

    public boolean isSpawnAnimals() {
        return spawnAnimals;
    }

    public boolean isSpawnNpcs() {
        return spawnNpcs;
    }

    public boolean isWhiteList() {
        return whiteList;
    }

    public boolean isEnforceWhitelist() {
        return enforceWhitelist;
    }

    public int getPlayerIdleTimeout() {
        return playerIdleTimeout;
    }

    public String getResourcePack() {
        return resourcePack;
    }

    public boolean isUseNativeTransport() {
        return useNativeTransport;
    }

    public boolean isEnableJmxMonitoring() {
        return enableJmxMonitoring;
    }

    public boolean isEnableStatus() {
        return enableStatus;
    }

    public boolean isHideOnlinePlayers() {
        return hideOnlinePlayers;
    }

    public int getEntityBroadcastRangePercentage() {
        return entityBroadcastRangePercentage;
    }

    public String getTextFilteringConfig() {
        return textFilteringConfig;
    }

    public int getOpPermissionLevel() {
        return opPermissionLevel;
    }

    public int getFunctionPermissionLevel() {
        return functionPermissionLevel;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public boolean isSyncChunkWrites() {
        return syncChunkWrites;
    }

    public boolean isEnableQuery() {
        return enableQuery;
    }

    public int getQueryPort() {
        return queryPort;
    }

    public boolean isPreventProxyConnections() {
        return preventProxyConnections;
    }

    public boolean isLogIps() {
        return logIps;
    }
}
