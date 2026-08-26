package com.sharded.core.modules.kits;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ItemStackUtil;
import java.io.File;
import java.sql.*;
import java.util.*;

public final class KitsDatabase {
    public record KitEntry(String name, String iconMaterial, long cooldownSeconds, String permission, byte[] contents) {}
    private final ShardedCore plugin; private Connection connection;
    public KitsDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "kits.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS custom_kits (name TEXT PRIMARY KEY, icon_material TEXT NOT NULL DEFAULT 'CHEST', cooldown_seconds INTEGER NOT NULL DEFAULT 86400, permission TEXT NOT NULL DEFAULT '', contents BLOB NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS kit_cooldowns (uuid TEXT NOT NULL, kit_name TEXT NOT NULL, last_claim INTEGER NOT NULL, PRIMARY KEY (uuid, kit_name))");
        }
    }
    public synchronized void saveKit(String name, String icon, long cooldown, String permission, byte[] contents) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO custom_kits (name, icon_material, cooldown_seconds, permission, contents) VALUES (?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET icon_material=excluded.icon_material, cooldown_seconds=excluded.cooldown_seconds, permission=excluded.permission, contents=excluded.contents")) {
            ps.setString(1,name.toLowerCase(Locale.ROOT)); ps.setString(2,icon); ps.setLong(3,cooldown); ps.setString(4,permission); ps.setBytes(5,contents); ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("[kits] save: "+e.getMessage()); }
    }
    public synchronized boolean deleteKit(String name) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM custom_kits WHERE name=?")) { ps.setString(1,name.toLowerCase(Locale.ROOT)); return ps.executeUpdate()>0; } catch (SQLException e) { return false; }
    }
    public synchronized KitEntry getCustomKit(String name) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM custom_kits WHERE name=?")) {
            ps.setString(1,name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) return null; return new KitEntry(rs.getString("name"), rs.getString("icon_material"), rs.getLong("cooldown_seconds"), rs.getString("permission"), rs.getBytes("contents")); }
        } catch (SQLException e) { return null; }
    }
    public synchronized List<String> customKitNames() {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = connection.createStatement().executeQuery("SELECT name FROM custom_kits ORDER BY name")) { while (rs.next()) names.add(rs.getString("name")); } catch (SQLException ignored) {}
        return names;
    }
    public synchronized long lastClaim(UUID uuid, String kitName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT last_claim FROM kit_cooldowns WHERE uuid=? AND kit_name=?")) {
            ps.setString(1,uuid.toString()); ps.setString(2,kitName.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong("last_claim") : 0L; }
        } catch (SQLException e) { return 0L; }
    }
    public synchronized void setClaim(UUID uuid, String kitName, long time) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO kit_cooldowns (uuid, kit_name, last_claim) VALUES (?,?,?) ON CONFLICT(uuid, kit_name) DO UPDATE SET last_claim=excluded.last_claim")) {
            ps.setString(1,uuid.toString()); ps.setString(2,kitName.toLowerCase(Locale.ROOT)); ps.setLong(3,time); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
    public static byte[] serializeContents(Map<Integer, org.bukkit.inventory.ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        for (var e : items.entrySet()) { byte[] b = ItemStackUtil.serialize(e.getValue()); if (b.length==0) continue; sb.append(e.getKey()).append(':').append(Base64.getEncoder().encodeToString(b)).append(';'); }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public static Map<Integer, org.bukkit.inventory.ItemStack> deserializeContents(byte[] raw) {
        Map<Integer, org.bukkit.inventory.ItemStack> map = new HashMap<>();
        if (raw == null || raw.length == 0) return map;
        for (String part : new String(raw, java.nio.charset.StandardCharsets.UTF_8).split(";")) {
            if (part.isBlank()) continue; int idx = part.indexOf(':'); if (idx <= 0) continue;
            try { map.put(Integer.parseInt(part.substring(0,idx)), ItemStackUtil.deserialize(Base64.getDecoder().decode(part.substring(idx+1)))); } catch (Exception ignored) {}
        }
        return map;
    }
    public synchronized void close() { if (connection != null) { try { connection.close(); } catch (SQLException ignored) {} connection = null; } }
}
