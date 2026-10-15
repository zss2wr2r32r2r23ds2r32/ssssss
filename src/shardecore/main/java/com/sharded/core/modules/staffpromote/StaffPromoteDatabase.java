package com.sharded.core.modules.staffpromote;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StaffPromoteDatabase implements AutoCloseable {
   private final Connection connection;

   public StaffPromoteDatabase(File moduleFolder, YamlConfiguration config) throws SQLException {
      if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
         throw new SQLException("Could not create module folder " + moduleFolder);
      } else {
         String s = config.getString("storage.type", "sqlite");
         if (s != null && s.equalsIgnoreCase("mariadb")) {
            this.connection = this.openMariaDb(config);
            this.createTable(true);
         } else {
            this.connection = this.openSqlite(moduleFolder);
            this.createTable(false);
         }
      }
   }

   private Connection openSqlite(File moduleFolder) throws SQLException {
      try {
         Class.forName("org.sqlite.JDBC");
      } catch (ClassNotFoundException classnotfoundexception) {
      }

      File file1 = new File(moduleFolder, "promotions.db");
      return DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());
   }

   private Connection openMariaDb(YamlConfiguration config) throws SQLException {
      try {
         Class.forName("org.mariadb.jdbc.Driver");
      } catch (ClassNotFoundException classnotfoundexception) {
      }

      String s = config.getString("storage.mariadb.host", config.getString("storage.host", "localhost"));
      int i = config.getInt("storage.mariadb.port", config.getInt("storage.port", 3306));
      String s1 = config.getString("storage.mariadb.database", config.getString("storage.database", "sharded"));
      String s2 = config.getString("storage.mariadb.username", config.getString("storage.username", "root"));
      String s3 = config.getString("storage.mariadb.password", config.getString("storage.password", ""));
      Properties properties = new Properties();
      properties.setProperty("user", s2 == null ? "" : s2);
      properties.setProperty("password", s3 == null ? "" : s3);
      String s4 = "jdbc:mariadb://" + s + ":" + i + "/" + s1;
      return DriverManager.getConnection(s4, properties);
   }

   private void createTable(boolean mariaDb) throws SQLException {
      String s = mariaDb ? "INTEGER PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
      String s1 = "CREATE TABLE IF NOT EXISTS promotions (id "
         + s
         + ", actor TEXT NOT NULL, target TEXT NOT NULL, action TEXT NOT NULL, role TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";

      try (Statement statement = this.connection.createStatement()) {
         statement.executeUpdate(s1);
      }
   }

   public synchronized void log(String actor, String target, String action, String role) throws SQLException {
      String s = "INSERT INTO promotions(actor, target, action, role) VALUES (?, ?, ?, ?)";

      try (PreparedStatement preparedstatement = this.connection.prepareStatement(s)) {
         preparedstatement.setString(1, actor);
         preparedstatement.setString(2, target);
         preparedstatement.setString(3, action);
         preparedstatement.setString(4, role);
         preparedstatement.executeUpdate();
      }
   }

   @Override
   public synchronized void close() {
      try {
         this.connection.close();
      } catch (SQLException sqlexception) {
      }
   }
}
