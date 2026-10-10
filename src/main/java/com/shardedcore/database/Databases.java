package com.shardedcore.database;

import com.shardedcore.ShardedCore;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

public final class Databases {

    private Databases() {
    }

    public static Sqlite open(ShardedCore plugin, ConfigurationSection section, Sqlite fallback, String label) {
        if (section == null) return fallback;
        String type = section.getString("type", "sqlite");
        if (type == null || type.isBlank() || type.equalsIgnoreCase("sqlite")) return fallback;
        ConfigurationSection remote = section.getConfigurationSection("mariadb");
        if (remote == null) remote = section.getConfigurationSection("mysql");
        if (remote == null) return fallback;
        String host = remote.getString("host", "localhost");
        int port = remote.getInt("port", 3306);
        String database = remote.getString("database", "shardedcore");
        String user = remote.getString("username", "root");
        String pass = remote.getString("password", "");
        boolean maria = type.toLowerCase(Locale.ROOT).contains("maria");
        String scheme = maria ? "jdbc:mariadb://" : "jdbc:mysql://";
        String url = scheme + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        try {
            Sqlite sql = new Sqlite(plugin, url, user, pass);
            sql.open();
            plugin.getLogger().info(label + " database is using " + (maria ? "MariaDB" : "MySQL") + ".");
            return sql;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, label + " remote database failed, using SQLite instead", ex);
            return fallback;
        }
    }

    public static boolean remote(Sqlite sqlite) {
        return sqlite != null && sqlite.mysql();
    }
}
