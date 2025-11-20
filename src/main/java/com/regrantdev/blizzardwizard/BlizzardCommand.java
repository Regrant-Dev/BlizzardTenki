package com.regrantdev.blizzardwizard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Debug command for testing blizzard categories
// Usage: /blizzard <category 1-5> [duration in seconds]
public class BlizzardCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("blizzard")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                    .executes(context -> setBlizzardCategory(context, 60)) // Default 60 seconds
                    .then(Commands.argument("duration", IntegerArgumentType.integer(1, 3600))
                        .executes(context -> setBlizzardCategory(context,
                            IntegerArgumentType.getInteger(context, "duration")))
                    )
                )
        );
    }

    private static int setBlizzardCategory(CommandContext<CommandSourceStack> context, int durationSeconds) {
        CommandSourceStack source = context.getSource();
        ServerLevel world = source.getLevel();
        int category = IntegerArgumentType.getInteger(context, "category");

        // Set the category for this world
        BlizzardTenki.setBlizzardCategory(world, category);

        // Get category name for feedback
        String categoryName = getCategoryName(category);

        // Send category to all players in this world
        BlizzardCategoryPacket categoryPacket = new BlizzardCategoryPacket(category);
        for (ServerPlayer player : world.players()) {
            player.connection.send(categoryPacket);
        }

        // Start rain (without thunder) for the specified duration
        // setWeatherParameters(clearTime, rainTime, raining, thundering)
        world.setWeatherParameters(0, durationSeconds * 20, true, false);

        // Send feedback to command executor
        source.sendSuccess(() -> Component.literal(
            "§6[Blizzard Tenki] §fSet blizzard to Category " + category + " §7(" + categoryName + ")§f for " + durationSeconds + " seconds"
        ), true);

        BlizzardTenki.LOGGER.info("Command: Set blizzard category to " + category + " (" + categoryName + ") for " + durationSeconds + " seconds");

        return 1; // Success
    }

    private static String getCategoryName(int category) {
        return switch (category) {
            case 1 -> "Light Flurry";
            case 2 -> "Light Snow";
            case 3 -> "Moderate Blizzard";
            case 4 -> "Heavy Blizzard";
            case 5 -> "Severe Whiteout";
            default -> "Unknown";
        };
    }
}
