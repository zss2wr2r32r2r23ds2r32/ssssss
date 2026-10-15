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

public final class SqliteDatabase implements AutoCloseable {

    private final ShardedCore plugin;
    private Connection connection;

    public SqliteDatabase(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File databaseFile = new File(folder, "data.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        connection.setAutoCommit(true);
    }

    public Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            open();
        }
        return connection;
    }

    public void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    public void executeUpdate(String sql, Object... params) throws SQLException {
        execute(sql, params);
    }

    public <T> T query(String sql, Function<ResultSet, T> mapper, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapper.apply(resultSet);
            }
        }
    }

    public void runSchema(String sql) throws SQLException {
        try (Statement statement = connection().createStatement()) {
            statement.execute(sql);
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
                plugin.getLogger().warning("Failed to close SQLite connection: " + ex.getMessage());
            }
        }
    }
}
