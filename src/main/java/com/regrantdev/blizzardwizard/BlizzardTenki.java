package com.regrantdev.blizzardwizard;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.Direction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BlizzardTenki.MODID)
public class BlizzardTenki {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "blizzardtenki";
    // Directly reference a slf4j logger - useful for debugging!
    public static final Logger LOGGER = LogUtils.getLogger();
    // Game Tick Counter
    private int tickCounter = 0;

    // Track blizzard category per world (1-5 intensity)
    private static final Map<ServerLevel, Integer> BLIZZARD_CATEGORIES = new ConcurrentHashMap<>();

    // Track previous rain level per world (to detect when rain starts)
    private static final Map<ServerLevel, Float> PREVIOUS_RAIN_LEVELS = new ConcurrentHashMap<>();

    // Track which worlds have manually-set categories (skip auto-detection for these)
    private static final Map<ServerLevel, Boolean> MANUAL_CATEGORY_SET = new ConcurrentHashMap<>();

    // The constructor for the mod class is the first code that is run when your mod
    // is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and
    // pass them in automatically.
    public BlizzardTenki(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register network packets
        modEventBus.addListener(this::onRegisterPayloads);

        // Register our blocks
        ModBlocks.BLOCKS.register(modEventBus);

        // Register our items
        ModItems.ITEMS.register(modEventBus);

        // Register our creative tab
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // This allows us to use @SubscribeEvent methods in this class
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's config file
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Code that runs during mod initialization
        LOGGER.info("Blizzard Tenki (Weather Wizard) is loading...");
        LOGGER.info("Preparing to summon intense blizzards!");
    }

    // Register network packets
    public void onRegisterPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1"); // "1" is the protocol version

        // Register BlizzAura positions packet (server → client only)
        registrar.playToClient(
            BlizzAuraPositionsPacket.TYPE,
            BlizzAuraPositionsPacket.STREAM_CODEC,
            BlizzAuraPositionsPacket::handle
        );

        // Register blizzard category packet (server → client only)
        registrar.playToClient(
            BlizzardCategoryPacket.TYPE,
            BlizzardCategoryPacket.STREAM_CODEC,
            BlizzardCategoryPacket::handle
        );

        LOGGER.info("Blizzard Tenki: Registered network packets!");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("Blizzard Tenki: Server starting - weather systems ready!");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        // Send BlizzAura positions to the player when they join
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel world = (ServerLevel) player.level();

            // Send BlizzAura positions for this world
            Set<BlockPos> blizzauraBlocks = getBlizzAuraPositions(world);
            BlizzAuraPositionsPacket posPacket = new BlizzAuraPositionsPacket(blizzauraBlocks);
            player.connection.send(posPacket);

            // Send current blizzard category for this world
            int category = BLIZZARD_CATEGORIES.getOrDefault(world, 3);
            BlizzardCategoryPacket categoryPacket = new BlizzardCategoryPacket(category);
            player.connection.send(categoryPacket);

            LOGGER.info("Sent " + blizzauraBlocks.size() + " BlizzAura positions and category " + category + " to player " + player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        // Register the /blizzard command
        BlizzardCommand.register(event.getDispatcher());
        LOGGER.info("Blizzard Tenki: Registered /blizzard command!");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        // Get the server (has all worlds)
        MinecraftServer server = event.getServer();

        // Check which worlds need accumulation this tick (category-dependent tick rate)
        boolean shouldRunAccumulation = false;

        // Determine tick interval based on highest category across all worlds
        int highestCategory = 3; // Default
        for (ServerLevel world : server.getAllLevels()) {
            int worldCategory = BLIZZARD_CATEGORIES.getOrDefault(world, 3);
            if (worldCategory > highestCategory) {
                highestCategory = worldCategory;
            }
        }

        // Category-specific tick intervals (how often accumulation runs)
        int tickInterval = switch (highestCategory) {
            case 1, 2, 3 -> 20; // Categories 1-3: Once per second (20 ticks)
            case 4 -> 4;        // Category 4: 5 times per second (every 4 ticks)
            case 5 -> 2;        // Category 5: 10 times per second (every 2 ticks)
            default -> 20;
        };

        if (tickCounter >= tickInterval) {// Variable interval based on category

            // Loop through each world/dimension
            for (ServerLevel world : server.getAllLevels()) {
                // === BLIZZARD CATEGORY DETECTION ===
                // Detect when rain starts and pick a random blizzard intensity category
                float currentRainLevel = world.getRainLevel(1.0F);
                float previousRainLevel = PREVIOUS_RAIN_LEVELS.getOrDefault(world, 0.0F);

                // Rain just started! (went from not raining to raining)
                // Only auto-detect if category wasn't manually set
                if (previousRainLevel < 0.2F && currentRainLevel >= 0.2F && !MANUAL_CATEGORY_SET.getOrDefault(world, false)) {
                    // Pick blizzard category based on config
                    int category;
                    if (Config.ENABLE_RANDOM_BLIZZARD_INTENSITY.get()) {
                        // Random category (1-5)
                        category = world.random.nextInt(5) + 1; // Random: 1, 2, 3, 4, or 5
                    } else {
                        // Fixed category 3 (Moderate Blizzard)
                        category = 3;
                    }
                    BLIZZARD_CATEGORIES.put(world, category);

                    // Send category to all players in this world
                    BlizzardCategoryPacket categoryPacket = new BlizzardCategoryPacket(category);
                    for (ServerPlayer player : world.players()) {
                        player.connection.send(categoryPacket);
                    }

                    // Log the category for debugging
                    String categoryName = getCategoryName(category);
                    LOGGER.info("Blizzard starting in " + world.dimension().location() + " with intensity: " + categoryName);
                }

                // Clear manual category flag when rain stops (allow auto-detection for next natural rain)
                if (currentRainLevel < 0.1F && previousRainLevel >= 0.1F) {
                    MANUAL_CATEGORY_SET.remove(world);
                    LOGGER.info("Rain stopped in " + world.dimension().location() + " - cleared manual category flag");
                }

                // Update previous rain level for next tick
                PREVIOUS_RAIN_LEVELS.put(world, currentRainLevel);

                if (world.isRaining() && Config.ENABLE_SNOW_ACCUMULATION.get()) {

                    // Track which positions we've already processed (prevents overlap doubling)
                    Set<BlockPos> processedPositions = new HashSet<>();

                    // === PART 1: BlizzAura Block Processing ===
                    // Find all BlizzAura blocks in loaded chunks
                    Set<BlockPos> blizzauraBlocks = findAllBlizzAuraBlocks(world);

                    // Always send packet to update client (even if empty, so client clears positions)
                    LOGGER.info("Found " + blizzauraBlocks.size() + " BlizzAura block(s) in " + world.dimension().location());
                    BlizzAuraPositionsPacket packet = new BlizzAuraPositionsPacket(blizzauraBlocks);
                    for (ServerPlayer player : world.players()) {
                        player.connection.send(packet);
                    }

                    // For each BlizzAura block, make snow around IT
                    for (BlockPos blizzauraPos : blizzauraBlocks) {
                        int blizzauraRange = Config.BLIZZAURA_RANGE.get();
                        processSnowAccumulation(world, processedPositions, blizzauraRange, blizzauraPos);
                    }

                    // === PART 2: Natural Cold Biome Processing ===
                    // Also handle natural snow accumulation in cold biomes (around players)
                    for (ServerPlayer player : world.players()) {
                        BlockPos playerPos = player.blockPosition();
                        Biome biome = world.getBiome(playerPos).value();

                        // Only process if naturally cold (NOT from BlizzAura)
                        if (biome.coldEnoughToSnow(playerPos, world.getSeaLevel())) {
                            processSnowAccumulation(world, processedPositions, 50, playerPos);
                        }
                    }
                }
            }

            tickCounter = 0;
        }
    }

    // Helper method: Find all BlizzAura blocks near players (from saved data)
    private Set<BlockPos> findAllBlizzAuraBlocks(ServerLevel world) {
        BlizzAuraSavedData data = BlizzAuraSavedData.get(world);
        return data.getPositions();
    }

    // Helper method: Try to add a snow layer at a position
    private void addSnowLayer(ServerLevel world, BlockPos pos) {
        // Get max snow layers from config and clamp to valid values (8, 16, or 24)
        int configValue = Config.MAX_SNOW_LAYERS.get();
        int maxLayers = clampToValidSnowLayers(configValue);

        BlockState currentState = world.getBlockState(pos);
        BlockState belowState = world.getBlockState(pos.below());

        // LOGGER.info("DEBUG: addSnowLayer at " + pos + ", currentState=" + currentState.getBlock() + ", belowState=" + belowState.getBlock());

        // Case 1: Empty air above a block that can support snow - start fresh snow
        // Use more permissive check than isFaceSturdy (vanilla checks if block is solid or has collision)
        if (currentState.isAir() && !belowState.isAir() && belowState.getCollisionShape(world, pos.below()).max(Direction.Axis.Y) >= 1.0) {
            // Place 1 layer of snow (flag 2 = UPDATE_CLIENTS only, no sound)
            BlockState newSnow = Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1);
            world.setBlock(pos, newSnow, 2 | 128);
            // LOGGER.info("DEBUG: Placed NEW snow layer at " + pos);
            return;
        }

        // Case 2: Already has snow layers (1-7) - add more
        if (currentState.is(Blocks.SNOW)) {
            int currentLayers = currentState.getValue(BlockStateProperties.LAYERS);

            if (currentLayers < 8) {
                // Add one more layer (flag 2 = no sound)
                BlockState moreSnow = currentState.setValue(BlockStateProperties.LAYERS, currentLayers + 1);
                world.setBlock(pos, moreSnow, 2 | 128);
                return;
            }

            // 8 layers reached - check total height before converting
            int totalLayers = countSnowLayers(world, pos);
            if (totalLayers >= maxLayers) {
                return; // Already at max
            }

            // Convert to full snow block (flag 2 = no sound)
            world.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2 | 128);
            return;
        }

        // Case 3: Full snow block - try to stack on top
        if (currentState.is(Blocks.SNOW_BLOCK)) {
            // Check total height first
            int totalLayers = countSnowLayers(world, pos);
            if (totalLayers >= maxLayers) {
                return; // Already at max
            }

            // Try to place snow on top
            BlockPos above = pos.above();
            BlockState aboveState = world.getBlockState(above);

            if (aboveState.isAir()) {
                // Start a new layer on top (flag 2 = no sound)
                BlockState newSnow = Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1);
                world.setBlock(above, newSnow, 2 | 128);
            } else if (aboveState.is(Blocks.SNOW)) {
                // Continue adding to existing snow layers on top (flag 2 = no sound)
                int aboveLayers = aboveState.getValue(BlockStateProperties.LAYERS);
                if (aboveLayers < 8) {
                    BlockState moreSnow = aboveState.setValue(BlockStateProperties.LAYERS, aboveLayers + 1);
                    world.setBlock(above, moreSnow, 2 | 128);
                } else {
                    // Convert top layer to snow block too (if under max, flag 2 = no sound)
                    int totalAfterConvert = countSnowLayers(world, pos) + 8; // Count what we'd have
                    if (totalAfterConvert <= maxLayers) {
                        world.setBlock(above, Blocks.SNOW_BLOCK.defaultBlockState(), 2 | 128);
                    }
                }
            }
        }
    }

    // Helper method: Count total snow layers at position (including stacked blocks)
    private int countSnowLayers(ServerLevel world, BlockPos pos) {
        int totalLayers = 0;

        // Start from the position and count upward
        BlockPos checkPos = pos;

        // Count upward for up to 3 blocks (24 layers max)
        for (int i = 0; i < 3; i++) {
            BlockState state = world.getBlockState(checkPos);

            if (state.is(Blocks.SNOW)) {
                totalLayers += state.getValue(BlockStateProperties.LAYERS);
                checkPos = checkPos.above();
            } else if (state.is(Blocks.SNOW_BLOCK)) {
                totalLayers += 8;
                checkPos = checkPos.above();
            } else {
                break; // Not snow, stop counting
            }
        }

        return totalLayers;
    }

    private void processSnowAccumulation(ServerLevel world, Set<BlockPos> processedPositions,
            int range, BlockPos offsetPosition) {
        // Get current blizzard category for this world
        int category = BLIZZARD_CATEGORIES.getOrDefault(world, 3); // Default to category 3

        // Category multiplier for accumulation speed
        float categoryMultiplier = getCategoryAccumulationMultiplier(category);

        // Accumulation chance increases with category (higher = more snow)
        // Category 4 & 5 are MUCH more aggressive
        int chancePercent = switch (category) {
            case 1 -> 70;  // Light Flurry - moderate chance
            case 2 -> 80;  // Light Snow
            case 3 -> 90;  // Moderate Blizzard
            case 4 -> 98;  // Heavy Blizzard - almost always!
            case 5 -> 100; // Severe Whiteout - ALWAYS places snow!
            default -> 90;
        };

        // Number of blocks to check increases with category (more blocks = faster coverage)
        int blocksToCheck = (int)(100 * categoryMultiplier); // 40-300 blocks (doubled from 20-150)

        // LOGGER.info("DEBUG: Processing snow accumulation at " + offsetPosition + ", range=" + range + ", blocksToCheck=" + blocksToCheck + ", chance=" + chancePercent + "%");

        // Check random blocks within range
        for (int i = 0; i < blocksToCheck; i++) {
            int offsetX = world.random.nextInt(range * 2 + 1) - range;
            int offsetZ = world.random.nextInt(range * 2 + 1) - range;
            BlockPos snowPos = offsetPosition.offset(offsetX, 0, offsetZ);

            // Find the highest solid block (the surface)
            BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, snowPos);

            // Now find where to actually place/add snow
            BlockPos targetPos = findSnowTargetPosition(world, surfacePos);

            // Skip if we already processed this position (prevents doubling from overlapping BlizzAura)
            if (processedPositions.contains(targetPos)) {
                continue;
            }

            // Random chance based on category
            if (world.random.nextInt(100) < chancePercent) {
                // LOGGER.info("DEBUG: Attempting to add snow at " + targetPos);
                addSnowLayer(world, targetPos);
                processedPositions.add(targetPos); // Mark as processed
            }
        }
    }

    // Helper method: Get accumulation speed multiplier for blizzard category
    private float getCategoryAccumulationMultiplier(int category) {
        return switch (category) {
            case 1 -> 0.4f;   // Light Flurry - slow accumulation (40 blocks/sec)
            case 2 -> 0.7f;   // Light Snow (70 blocks/sec)
            case 3 -> 1.0f;   // Moderate Blizzard - baseline (100 blocks/sec)
            case 4 -> 4.0f;   // Heavy Blizzard - fast (400 blocks * 5x/sec = 2000/sec)
            case 5 -> 6.0f;   // Severe Whiteout - very fast! (600 blocks * 10x/sec = 6000/sec)
            default -> 1.0f;  // Default to moderate
        };
    }

    // Helper method: Clamp config value to valid snow layer amounts (8, 16, or 24)
    private int clampToValidSnowLayers(int value) {
        // Round to nearest valid value
        if (value <= 12) return 8;   // 8 or less, or closer to 8 than 16
        else if (value <= 20) return 16; // Closer to 16 than 24
        else return 24; // 20 or more
    }

    // Helper method: Find the correct position to place/add snow
    private BlockPos findSnowTargetPosition(ServerLevel world, BlockPos surfacePos) {
        // Start from the surface and work upward to find the top of any snow stack
        BlockPos checkPos = surfacePos;

        // Check up to 3 blocks above surface (for stacked snow)
        for (int i = 0; i < 3; i++) {
            BlockState state = world.getBlockState(checkPos);
            BlockState above = world.getBlockState(checkPos.above());

            // If current is snow/snow block and above is air, this is the top
            if ((state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) && above.isAir()) {
                return checkPos; // Add to this snow layer
            }

            // If current is snow/snow block and above is also snow/snow block, keep going up
            if ((state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) &&
                (above.is(Blocks.SNOW) || above.is(Blocks.SNOW_BLOCK))) {
                checkPos = checkPos.above();
                continue;
            }

            // If current is not snow, this is the surface - place snow here
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) {
                return checkPos; // getHeightmapPos already returned position above solid block
            }
        }

        // Fallback: return surface position
        return surfacePos;
    }

    // Event listener: Track BlizzAura blocks when placed
    @SubscribeEvent
    public void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        // Only run on server side
        if (event.getLevel().isClientSide()) return;

        // Check if the placed block is BlizzAura
        if (event.getPlacedBlock().getBlock() == ModBlocks.BLIZZAURA.get()) {
            ServerLevel world = (ServerLevel) event.getLevel();
            BlockPos pos = event.getPos();
            registerBlizzAura(world, pos);

            // Immediately send updated positions to all players
            syncBlizzAuraPositions(world);
        }
    }

    // Event listener: Track BlizzAura blocks when broken
    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        // Only run on server side
        if (event.getLevel().isClientSide()) return;

        // Check if the broken block is BlizzAura
        if (event.getState().getBlock() == ModBlocks.BLIZZAURA.get()) {
            ServerLevel world = (ServerLevel) event.getLevel();
            BlockPos pos = event.getPos();
            unregisterBlizzAura(world, pos);

            // Immediately send updated positions to all players
            syncBlizzAuraPositions(world);
        }
    }

    // Helper method: Send BlizzAura positions to all players in a world
    private void syncBlizzAuraPositions(ServerLevel world) {
        Set<BlockPos> blizzauraBlocks = getBlizzAuraPositions(world);
        BlizzAuraPositionsPacket packet = new BlizzAuraPositionsPacket(blizzauraBlocks);
        for (ServerPlayer player : world.players()) {
            player.connection.send(packet);
        }
        LOGGER.info("Synced " + blizzauraBlocks.size() + " BlizzAura position(s) to clients");
    }

    // Helper method: Register a BlizzAura block position (saved to disk)
    public static void registerBlizzAura(ServerLevel world, BlockPos pos) {
        BlizzAuraSavedData data = BlizzAuraSavedData.get(world);
        data.addPosition(pos);
        LOGGER.info("Registered BlizzAura at " + pos + " (saved to disk)");
    }

    // Helper method: Unregister a BlizzAura block position (saved to disk)
    public static void unregisterBlizzAura(ServerLevel world, BlockPos pos) {
        BlizzAuraSavedData data = BlizzAuraSavedData.get(world);
        data.removePosition(pos);
        LOGGER.info("Unregistered BlizzAura at " + pos + " (saved to disk)");
    }

    // Helper method: Get all BlizzAura positions for a world (from disk)
    public static Set<BlockPos> getBlizzAuraPositions(ServerLevel world) {
        BlizzAuraSavedData data = BlizzAuraSavedData.get(world);
        return data.getPositions();
    }

    // Helper method: Manually set blizzard category (for commands/testing)
    public static void setBlizzardCategory(ServerLevel world, int category) {
        BLIZZARD_CATEGORIES.put(world, category);
        MANUAL_CATEGORY_SET.put(world, true); // Flag as manually set to prevent auto-detection override
        LOGGER.info("Manually set blizzard category to " + category + " in " + world.dimension().location());
    }

    // Helper method: Get the name of a blizzard category
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
