package dev.sharded.velocitycore.motd;

import com.velocitypowered.api.util.Favicon;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ServerIconService {

    private final Path iconsDirectory;
    private final Logger logger;
    private final Map<String, Favicon> cache = new HashMap<>();

    public ServerIconService(Path dataDirectory, Logger logger) {
        this.iconsDirectory = dataDirectory.resolve("icons");
        this.logger = logger;
        try {
            Files.createDirectories(iconsDirectory);
        } catch (IOException exception) {
            logger.warn("Unable to create icons directory", exception);
        }
    }

    public Favicon resolve(String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(imageName, this::loadIcon);
    }

    private Favicon loadIcon(String imageName) {
        Path iconPath = iconsDirectory.resolve(imageName);
        if (!Files.exists(iconPath)) {
            logger.warn("Missing server icon: icons/{}", imageName);
            return null;
        }
        try {
            return Favicon.create(iconPath);
        } catch (IOException | IllegalArgumentException exception) {
            logger.warn("Failed to load icon icons/{}: {}", imageName, exception.getMessage());
            return null;
        }
    }
}
