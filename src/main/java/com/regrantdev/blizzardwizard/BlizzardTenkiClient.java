package com.regrantdev.blizzardwizard;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
// This is where we'll handle client-only features like particles and visual effects!
@Mod(value = BlizzardTenki.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods
// in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = BlizzardTenki.MODID, value = Dist.CLIENT)
public class BlizzardTenkiClient {

    // Client-side storage of BlizzAura positions (received from server via packet)
    private static Set<BlockPos> clientBlizzAuraPositions = new HashSet<>();

    // Client-side storage of current blizzard category (1-5, received from server)
    private static int currentBlizzardCategory = 3; // Default to moderate (category 3)

    // Track when we last played wind sound (to control frequency)
    private static long lastWindSoundTime = 0;
    private static long lastIndoorSoundTime = 0;

    // Track if player was indoors last tick (to detect transitions)
    private static boolean wasIndoorLastTick = false;

    public BlizzardTenkiClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your
        // mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Client-side initialization
        BlizzardTenki.LOGGER.info("Blizzard Tenki: Client-side weather effects loading...");
    }

    @SubscribeEvent
    public static void onRenderFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        // Get the minecraft instance (client)
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Make sure we're in a world and player exists
        if (mc.level == null || mc.player == null)
            return;

        // Check if it's raining - required for fog
        if (!mc.level.isRaining()) {
            return; // No rain, no fog
        }

        // Get player position and biome
        net.minecraft.core.BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.biome.Biome biome = mc.level.getBiome(playerPos).value();

        // Check if near a BlizzAura block
        boolean nearBlizzAura = isNearBlizzAuraClient(mc.level, playerPos);

        // Check if it's cold enough to snow (for natural cold biomes)
        boolean isColdEnough = biome.coldEnoughToSnow(playerPos, mc.level.getSeaLevel());

        // Only apply fog if: (cold biome) OR (near BlizzAura)
        if (!isColdEnough && !nearBlizzAura) {
            return; // Not a blizzard area, skip fog
        }

        // Check if player is indoors
        boolean canSeeSky = mc.level.canSeeSky(playerPos.above());

        // Get fog density based on category
        float fogStart = getCategoryFogStart(currentBlizzardCategory);
        float fogEnd = getCategoryFogEnd(currentBlizzardCategory);

        // If indoors, use much lighter fog (push fog further away so outdoor areas still look foggy)
        if (!canSeeSky) {
            // Indoor fog is pushed much further out so you still see fog outside through doors/windows
            fogStart = fogStart * 3.0f;
            fogEnd = fogEnd * 2.0f;
        }

        // Apply fog settings (no need to cancel, just set the values)
        event.setNearPlaneDistance(fogStart);
        event.setFarPlaneDistance(fogEnd);
    }

    @SubscribeEvent
    public static void onComputeFogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        // Get the minecraft instance (client)
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Make sure we're in a world and player exists
        if (mc.level == null || mc.player == null)
            return;

        // Check if it's raining
        if (!mc.level.isRaining()) {
            return;
        }

        // Get player position and biome
        net.minecraft.core.BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.biome.Biome biome = mc.level.getBiome(playerPos).value();

        // Check if near a BlizzAura block or in cold biome
        boolean nearBlizzAura = isNearBlizzAuraClient(mc.level, playerPos);
        boolean isColdEnough = biome.coldEnoughToSnow(playerPos, mc.level.getSeaLevel());

        // Only apply fog color if: (cold biome) OR (near BlizzAura)
        if (!isColdEnough && !nearBlizzAura) {
            return;
        }

        // Check if player is indoors
        boolean canSeeSky = mc.level.canSeeSky(playerPos.above());

        // If indoors, use much lighter fog color (less white)
        float mixAmount;
        if (!canSeeSky) {
            // Indoors - only slight white tint
            mixAmount = 0.1f;
        } else {
            // Outdoors - full category-based fog color
            mixAmount = getCategoryFogColorMix(currentBlizzardCategory);
        }

        float blizzardR = 0.9f; // Light grey/white
        float blizzardG = 0.9f;
        float blizzardB = 0.95f; // Slight blue tint

        event.setRed(event.getRed() * (1 - mixAmount) + blizzardR * mixAmount);
        event.setGreen(event.getGreen() * (1 - mixAmount) + blizzardG * mixAmount);
        event.setBlue(event.getBlue() * (1 - mixAmount) + blizzardB * mixAmount);
        // Note: This fog color also affects sky rendering, making the sky white during blizzards
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // Get the minecraft instance (client)
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Make sure we're in a world and player exists
        if (mc.level == null || mc.player == null)
            return;

        // Only run every 4 ticks (for performance)
        if (mc.level.getGameTime() % 4 != 0)
            return;

        // Get player position and biome
        net.minecraft.core.BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.biome.Biome biome = mc.level.getBiome(playerPos).value();

        // Check if it's raining - required for any particles to spawn
        if (!mc.level.isRaining()) {
            return; // No rain, no particles
        }

        // Check if near a BlizzAura block
        boolean nearBlizzAura = isNearBlizzAuraClient(mc.level, playerPos);

        // Check if it's cold enough to snow (for natural cold biomes)
        boolean isColdEnough = biome.coldEnoughToSnow(playerPos, mc.level.getSeaLevel());

        // Debug every second
        if (mc.level.getGameTime() % 20 == 0) {
            BlizzardTenki.LOGGER.info("CLIENT: Cold=" + isColdEnough + ", NearBlizzAura=" + nearBlizzAura +
                    ", Temp=" + biome.getBaseTemperature());
        }

        // Only spawn particles if: (cold biome) OR (near BlizzAura)
        if (!isColdEnough && !nearBlizzAura) {
            return; // Not a blizzard area, skip particles
        }

        // Get game time (needed for both indoor and outdoor sounds)
        long gameTime = mc.level.getGameTime();

        // Check if player is indoors (can't see sky)
        boolean canSeeSky = mc.level.canSeeSky(playerPos.above()); // Check block above player's head
        boolean isIndoors = !canSeeSky;

        // Detect transition (entering or leaving shelter)
        boolean justTransitioned = (isIndoors != wasIndoorLastTick);
        wasIndoorLastTick = isIndoors;

        // Reset timers on transition for immediate sound change
        if (justTransitioned) {
            lastWindSoundTime = 0;
            lastIndoorSoundTime = 0;
        }

        // Play appropriate sounds based on location
        if (isIndoors) {
            // Play dampened wind sounds indoors (force if just entered)
            playIndoorWindSounds(mc, playerPos, gameTime, justTransitioned);
        } else {
            // Play normal wind sounds outdoors (force if just exited)
            playWindSounds(mc, gameTime, justTransitioned);
        }

        // Get category-specific multipliers
        float categoryParticleMultiplier = getCategoryParticleMultiplier(currentBlizzardCategory);
        float categoryWindMultiplier = getCategoryWindMultiplier(currentBlizzardCategory);

        // Base particle counts (much higher for visual impact!)
        int baseSnowCount = 100; // Increased from 5 to 100
        int baseWindCount = 200; // Increased from 30 to 200

        int totalSnowParticles = (int)(baseSnowCount * Config.PARTICLE_MULTIPLIER.get() * categoryParticleMultiplier);
        int totalWindParticles = (int)(baseWindCount * categoryParticleMultiplier);

        // Debug logging once per second (not every frame!)
        if (mc.level.getGameTime() % 20 == 0) {
            BlizzardTenki.LOGGER.info("CLIENT: Spawning " + totalSnowParticles + " snow + " + totalWindParticles + " wind particles! (Category " + currentBlizzardCategory + ")");
        }

        // Calculate consistent wind direction for this frame (changes slowly over time)
        // Use game time to create slowly changing wind direction

        double windAngle = (gameTime / 100.0); // Wind direction changes slowly
        double windStrength = Config.WIND_STRENGTH.get() * 1.5 * categoryWindMultiplier; // Apply category wind multiplier
        double windX = Math.sin(windAngle) * windStrength;
        double windZ = Math.cos(windAngle) * windStrength;

        // Spawn snow particles around the player
        int particleCount = totalSnowParticles; // Use calculated total
        int spawnedCount = 0; // Track how many actually spawned

        // When indoors, only spawn particles further away to prevent wall clipping
        double minSpawnDistance = isIndoors ? 8.0 : 0.0;
        double maxSpawnDistance = 16.0;

        for (int i = 0; i < particleCount; i++) {
            // Random position around player
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 2 * maxSpawnDistance;
            double offsetY = (mc.level.random.nextDouble()) * 8;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 2 * maxSpawnDistance;

            // If indoors, check if spawn point is far enough away
            if (isIndoors) {
                double distanceFromPlayer = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                if (distanceFromPlayer < minSpawnDistance) {
                    continue; // Too close to indoor player, skip
                }
            }

            double x = mc.player.getX() + offsetX;
            double y = mc.player.getY() + offsetY;
            double z = mc.player.getZ() + offsetZ;

            // Check if this position is outdoors (can particles fall here?)
            BlockPos particlePos = new BlockPos((int)x, (int)y, (int)z);

            // TEMPORARILY DISABLED - Testing if this is too restrictive
            /*
            // Use heightmap to check if position is below terrain/structures
            int topY = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, particlePos.getX(), particlePos.getZ());

            // If particle is below the top solid block, skip it (it's indoors/underground)
            if (particlePos.getY() < topY) {
                continue; // Skip - particle would be inside a structure or underground
            }
            */

            spawnedCount++;

            // Add slight variation to wind for each snowflake (more natural)
            double snowWindX = windX + (mc.level.random.nextDouble() - 0.5) * 0.2;
            double snowWindZ = windZ + (mc.level.random.nextDouble() - 0.5) * 0.2;

            // Spawn a snowflake particle that blows with the wind!
            mc.level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    x, y, z, // Position
                    snowWindX, -0.1, snowWindZ // Velocity (falling + blowing with wind!)
            );
        }

        // Spawn wind particles (always spawn, regardless of wind strength config)
        if (true) { // Always spawn wind/ash particles for blizzard effect
            int windParticleCount = totalWindParticles; // Use calculated total
            int windSpawnedCount = 0; // Track how many actually spawned

            for (int i = 0; i < windParticleCount; i++) {
                // Random position around player
                double offsetX = (mc.level.random.nextDouble() - 0.5) * 2 * maxSpawnDistance;
                double offsetY = (mc.level.random.nextDouble()) * 8;
                double offsetZ = (mc.level.random.nextDouble() - 0.5) * 2 * maxSpawnDistance;

                // If indoors, check if spawn point is far enough away
                if (isIndoors) {
                    double distanceFromPlayer = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                    if (distanceFromPlayer < minSpawnDistance) {
                        continue; // Too close to indoor player, skip
                    }
                }

                double x = mc.player.getX() + offsetX;
                double y = mc.player.getY() + offsetY;
                double z = mc.player.getZ() + offsetZ;

                // Check if this position is outdoors (can particles fall here?)
                BlockPos particlePos = new BlockPos((int)x, (int)y, (int)z);

                // TEMPORARILY DISABLED - Testing if this is too restrictive
                /*
                // Use heightmap to check if position is below terrain/structures
                int topY = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, particlePos.getX(), particlePos.getZ());

                // If particle is below the top solid block, skip it (it's indoors/underground)
                if (particlePos.getY() < topY) {
                    continue; // Skip - particle would be inside a structure or underground
                }
                */

                windSpawnedCount++;

                // Add variation to wind particles for more natural look
                double particleWindX = windX + (mc.level.random.nextDouble() - 0.5) * 0.3;
                double particleWindZ = windZ + (mc.level.random.nextDouble() - 0.5) * 0.3;

                // Use cloud particles for wind effect (they follow velocity unlike WHITE_ASH)
                mc.level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.CLOUD,
                        x, y, z, // Position
                        particleWindX, -0.05, particleWindZ // Velocity (horizontal wind + slight fall)
                );
            }
        }
    }

    // Helper method: Check if there's a BlizzAura block near the player
    // (client-side)
    // Now uses positions received from server instead of searching!
    private static boolean isNearBlizzAuraClient(net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.core.BlockPos playerPos) {
        int searchRadius = Config.BLIZZAURA_RANGE.get(); // Check within BlizzAura range

        // Check if any stored BlizzAura position is near the player
        for (BlockPos blizzAuraPos : clientBlizzAuraPositions) {
            double distance = Math.sqrt(playerPos.distSqr(blizzAuraPos));
            if (distance <= searchRadius) {
                return true; // Found one nearby!
            }
        }
        return false;
    }

    // Called by packet handler when server sends BlizzAura positions
    public static void updateBlizzAuraPositions(Set<BlockPos> positions) {
        clientBlizzAuraPositions = new HashSet<>(positions);
        BlizzardTenki.LOGGER.info("CLIENT: Updated BlizzAura positions, now tracking " + positions.size() + " blocks");
    }

    // Called by packet handler when server sends blizzard category
    public static void updateBlizzardCategory(int category) {
        currentBlizzardCategory = category;
        String categoryName = getCategoryName(category);
        BlizzardTenki.LOGGER.info("CLIENT: Blizzard intensity updated to Category " + category + " (" + categoryName + ")");
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

    // Helper method: Get particle multiplier for a blizzard category
    private static float getCategoryParticleMultiplier(int category) {
        return switch (category) {
            case 1 -> 0.4f;  // Light Flurry - 40% particles
            case 2 -> 0.7f;  // Light Snow - 70% particles
            case 3 -> 1.0f;  // Moderate Blizzard - 100% (baseline)
            case 4 -> 1.5f;  // Heavy Blizzard - 150% particles
            case 5 -> 3.0f;  // Severe Whiteout - 300% particles (DENSE!)
            default -> 1.0f; // Default to moderate
        };
    }

    // Helper method: Get wind multiplier for a blizzard category
    private static float getCategoryWindMultiplier(int category) {
        return switch (category) {
            case 1 -> 0.5f;  // Light Flurry - 50% wind
            case 2 -> 0.7f;  // Light Snow - 70% wind
            case 3 -> 1.0f;  // Moderate Blizzard - 100% (baseline)
            case 4 -> 1.3f;  // Heavy Blizzard - 130% wind
            case 5 -> 1.8f;  // Severe Whiteout - 180% wind (STRONG!)
            default -> 1.0f; // Default to moderate
        };
    }

    // Helper method: Get fog start distance for a blizzard category
    private static float getCategoryFogStart(int category) {
        return switch (category) {
            case 1 -> 20.0f;  // Light Flurry - fog starts far away
            case 2 -> 15.0f;  // Light Snow
            case 3 -> 10.0f;  // Moderate Blizzard
            case 4 -> 5.0f;   // Heavy Blizzard - fog starts close
            case 5 -> 2.0f;   // Severe Whiteout - fog starts very close!
            default -> 10.0f; // Default to moderate
        };
    }

    // Helper method: Get fog end distance for a blizzard category
    private static float getCategoryFogEnd(int category) {
        return switch (category) {
            case 1 -> 80.0f;  // Light Flurry - can see far
            case 2 -> 60.0f;  // Light Snow
            case 3 -> 40.0f;  // Moderate Blizzard - baseline
            case 4 -> 25.0f;  // Heavy Blizzard - limited visibility
            case 5 -> 12.0f;  // Severe Whiteout - very limited visibility! (reduced from 15)
            default -> 40.0f; // Default to moderate
        };
    }

    // Helper method: Get fog color mix amount for a blizzard category
    private static float getCategoryFogColorMix(int category) {
        return switch (category) {
            case 1 -> 0.3f;  // Light Flurry - subtle white tint
            case 2 -> 0.5f;  // Light Snow - noticeable white
            case 3 -> 0.7f;  // Moderate Blizzard - mostly white
            case 4 -> 0.85f; // Heavy Blizzard - very white
            case 5 -> 0.95f; // Severe Whiteout - almost pure white (sky disappears)
            default -> 0.7f; // Default to moderate
        };
    }

    // Helper method: Play wind sounds based on blizzard category
    private static void playWindSounds(net.minecraft.client.Minecraft mc, long gameTime, boolean forcePlay) {
        // Get sound interval (how often to play) based on category
        int soundInterval = getCategorySoundInterval(currentBlizzardCategory);

        // Add random variation to interval (±20%)
        int randomVariation = (int)(soundInterval * 0.2f * (mc.level.random.nextFloat() - 0.5f) * 2);
        soundInterval += randomVariation;

        // Check if enough time has passed since last sound (skip if forced)
        if (!forcePlay && gameTime - lastWindSoundTime < soundInterval) {
            return; // Too soon, don't play yet
        }

        // Update last sound time
        lastWindSoundTime = gameTime;

        // Get base volume and pitch based on category (NO random variation here to keep categories distinct)
        float volume = getCategorySoundVolume(currentBlizzardCategory);
        float pitch = getCategorySoundPitch(currentBlizzardCategory);

        // Add SMALL random variation ONLY to pitch for naturalness (not volume!)
        float pitchVariation = (mc.level.random.nextFloat() - 0.5f) * 0.2f; // ±10% pitch only
        pitch = Math.max(0.3f, Math.min(2.0f, pitch + pitchVariation));

        // Randomly choose between different wind-like sounds for variety
        net.minecraft.sounds.SoundEvent windSound;
        int soundChoice = mc.level.random.nextInt(3);

        switch (soundChoice) {
            case 0 -> windSound = net.minecraft.sounds.SoundEvents.ELYTRA_FLYING; // Whooshing
            case 1 -> windSound = net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP; // Deep wind gust
            case 2 -> windSound = net.minecraft.sounds.SoundEvents.PHANTOM_FLAP; // Ethereal wind
            default -> windSound = net.minecraft.sounds.SoundEvents.ELYTRA_FLYING;
        }

        // Play wind sound at player position
        mc.level.playLocalSound(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            windSound,
            net.minecraft.sounds.SoundSource.WEATHER,
            volume,
            pitch,
            false // Not distanced (plays at full volume around player)
        );
    }

    // Helper method: Play dampened wind sounds when indoors
    private static void playIndoorWindSounds(net.minecraft.client.Minecraft mc, BlockPos playerPos, long gameTime, boolean forcePlay) {
        // Get sound interval (less frequent indoors)
        int soundInterval = getCategorySoundInterval(currentBlizzardCategory) * 2; // Half as frequent

        // Check if enough time has passed since last sound (skip if forced)
        if (!forcePlay && gameTime - lastIndoorSoundTime < soundInterval) {
            return; // Too soon
        }

        // Update last sound time
        lastIndoorSoundTime = gameTime;

        // Calculate distance from surface (how deep inside/underground)
        int distanceFromSky = calculateDistanceFromSky(mc.level, playerPos);

        // Calculate volume dampening based on depth (further = quieter)
        // 0 blocks = 30% volume, 10+ blocks = 5% volume
        float dampenMultiplier = Math.max(0.05f, 0.3f - (distanceFromSky * 0.025f));

        float baseVolume = getCategorySoundVolume(currentBlizzardCategory);
        float volume = baseVolume * dampenMultiplier; // Significantly quieter indoors

        float pitch = getCategorySoundPitch(currentBlizzardCategory);
        float pitchVariation = (mc.level.random.nextFloat() - 0.5f) * 0.2f;
        pitch = Math.max(0.3f, Math.min(2.0f, pitch + pitchVariation));

        // Only use ELYTRA_FLYING for indoor sounds (muffled whooshing)
        mc.level.playLocalSound(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
            net.minecraft.sounds.SoundSource.WEATHER,
            volume,
            pitch,
            false
        );
    }

    // Helper method: Calculate how many blocks the player is from open sky
    private static int calculateDistanceFromSky(net.minecraft.client.multiplayer.ClientLevel level, BlockPos playerPos) {
        int distance = 0;
        BlockPos checkPos = playerPos.above();

        // Check upward for up to 30 blocks
        for (int i = 0; i < 30; i++) {
            if (level.canSeeSky(checkPos)) {
                return distance; // Found sky
            }
            distance++;
            checkPos = checkPos.above();
        }

        return 30; // Max distance (very deep inside)
    }

    // Helper method: Get sound play interval (in ticks) for a blizzard category
    private static int getCategorySoundInterval(int category) {
        return switch (category) {
            case 1 -> 100; // Light Flurry - every 5 seconds
            case 2 -> 80;  // Light Snow - every 4 seconds
            case 3 -> 60;  // Moderate Blizzard - every 3 seconds
            case 4 -> 40;  // Heavy Blizzard - every 2 seconds
            case 5 -> 25;  // Severe Whiteout - every 1.25 seconds (frequent!)
            default -> 60; // Default to moderate
        };
    }

    // Helper method: Get sound volume for a blizzard category
    private static float getCategorySoundVolume(int category) {
        return switch (category) {
            case 1 -> 0.2f;  // Light Flurry - very quiet
            case 2 -> 0.4f;  // Light Snow - quiet
            case 3 -> 0.7f;  // Moderate Blizzard - moderate
            case 4 -> 1.2f;  // Heavy Blizzard - loud
            case 5 -> 2.0f;  // Severe Whiteout - VERY loud!
            default -> 0.7f; // Default to moderate
        };
    }

    // Helper method: Get sound pitch for a blizzard category
    private static float getCategorySoundPitch(int category) {
        return switch (category) {
            case 1 -> 1.5f;  // Light Flurry - higher pitch (softer wind)
            case 2 -> 1.3f;  // Light Snow
            case 3 -> 1.0f;  // Moderate Blizzard - normal pitch
            case 4 -> 0.8f;  // Heavy Blizzard - lower pitch (deeper wind)
            case 5 -> 0.6f;  // Severe Whiteout - very low pitch (howling wind!)
            default -> 1.0f; // Default to moderate
        };
    }

    // Public helper method for mixin - checks if player is near any BlizzAura block
    public static boolean isPlayerNearBlizzAura(BlockPos playerPos) {
        int searchRadius = Config.BLIZZAURA_RANGE.get(); // Check within BlizzAura range

        // Check if any stored BlizzAura position is near the player
        for (BlockPos blizzAuraPos : clientBlizzAuraPositions) {
            double distance = Math.sqrt(playerPos.distSqr(blizzAuraPos));
            if (distance <= searchRadius) {
                return true; // Found one nearby!
            }
        }
        return false;
    }
}