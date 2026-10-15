package com.shardedmc;

import com.shardedmc.bootstrap.ServerBootstrap;
import com.shardedmc.config.ServerProperties;
import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.reliability.ShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the ShardedMC high-performance Minecraft server.
 */
public final class ShardedMC {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardedMC.class);

    private ShardedMC() {
    }

    public static void main(String[] args) {
        Path serverRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        LOGGER.info("Starting ShardedMC v{} from {}", ServerBootstrap.VERSION, serverRoot.toAbsolutePath());

        ShutdownHandler.install();

        try {
            ServerProperties properties = ServerProperties.load(serverRoot.resolve("server.properties"));
            ShardedMCConfig config = ShardedMCConfig.load(serverRoot.resolve("config/shardedmc.yml"));

            ServerBootstrap bootstrap = new ServerBootstrap(serverRoot, properties, config);
            bootstrap.start();
            bootstrap.awaitShutdown();
        } catch (Exception e) {
            LOGGER.error("Fatal error during startup", e);
            System.exit(1);
        }
    }
}
