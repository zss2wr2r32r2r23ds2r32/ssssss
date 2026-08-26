package com.shardedcore.database;

import com.shardedcore.ShardedCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

public final class Sqlite implements AutoCloseable {

    private final ShardedCore plugin;
    private final File file;
    private Connection connection;

    public Sqlite(ShardedCore plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void open() throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
    }

    public Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) open();
        return connection;
    }

    public void run(String sql) throws SQLException {
        try (Statement statement = connection().createStatement()) {
            statement.execute(sql);
        }
    }

    public void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    public <T> T query(String sql, Function<ResultSet, T> mapper, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapper.apply(resultSet);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed to close SQLite: " + ex.getMessage());
            }
        }
    }
}
