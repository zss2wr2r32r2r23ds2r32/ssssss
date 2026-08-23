package dev.sharded.velocitycore.lobby.motd;

import dev.sharded.velocitycore.lobby.config.MotdConfig;
import dev.sharded.velocitycore.lobby.config.MotdSelector;
import dev.sharded.velocitycore.lobby.util.TextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ServerIconService {

    private final JavaPlugin plugin;
    private final File iconsDirectory;
    private final Map<String, CachedServerIcon> cache = new HashMap<>();

    public ServerIconService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.iconsDirectory = new File(plugin.getDataFolder(), "icons");
        if (!iconsDirectory.exists()) {
            iconsDirectory.mkdirs();
        }
    }

    public CachedServerIcon resolve(String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(imageName, this::loadIcon);
    }

    public CachedServerIcon resolveDefault(MotdConfig config) {
        if (!config.serverIconEnabled()) {
            return null;
        }
        return resolve(config.serverIconImage());
    }

    private CachedServerIcon loadIcon(String imageName) {
        File iconFile = new File(iconsDirectory, imageName);
        if (!iconFile.exists()) {
            plugin.getLogger().warning("Missing server icon: icons/" + imageName);
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(iconFile);
            if (image == null) {
                plugin.getLogger().warning("Invalid server icon (not PNG): icons/" + imageName);
                return null;
            }
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                plugin.getLogger().warning("Server icon must be 64x64: icons/" + imageName);
                return null;
            }
            Server server = plugin.getServer();
            return server.loadServerIcon(iconFile);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to load icon icons/" + imageName, exception);
            return null;
        }
    }
}
