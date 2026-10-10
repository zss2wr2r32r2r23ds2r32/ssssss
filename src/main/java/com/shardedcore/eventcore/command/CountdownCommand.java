package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.CountdownModule;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** {@code /countdown <seconds|preset|stop>} — tab completes the configured presets. */
public final class CountdownCommand extends BaseCommand {

    public CountdownCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.countdown", CountdownModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);

        if (args.length == 0) {
            int seconds = countdown.defaultSeconds();
            if (countdown.start(seconds)) {
                plugin.messages().send(sender, "countdown.started", "%seconds%", Integer.toString(seconds));
            }
            return;
        }

        String argument = args[0];
        if (argument.equalsIgnoreCase("stop") || argument.equalsIgnoreCase("cancel")) {
            countdown.stop(true);
            plugin.messages().send(sender, "countdown.stopped");
            return;
        }

        if (countdown.hasPreset(argument)) {
            if (countdown.startPreset(argument)) {
                plugin.messages().send(sender, "countdown.started-preset", "%preset%", argument.toLowerCase());
            } else {
                plugin.messages().send(sender, "countdown.invalid", "%input%", argument);
            }
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(argument);
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "countdown.invalid", "%input%", argument);
            return;
        }
        if (seconds <= 0 || !countdown.start(seconds)) {
            plugin.messages().send(sender, "countdown.invalid", "%input%", argument);
            return;
        }
        plugin.messages().send(sender, "countdown.started", "%seconds%", Integer.toString(seconds));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
        return countdown == null ? Collections.emptyList() : filter(countdown.suggestions(), args[0]);
    }
}
