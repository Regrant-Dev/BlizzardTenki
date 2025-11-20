package com.regrantdev.blizzardwizard;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

// Config class for Blizzard Tenki settings
// Players can customize how intense blizzards are!
public class Config {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // Blizzard Intensity Settings
        public static final ModConfigSpec.IntValue PARTICLE_MULTIPLIER = BUILDER
                        .comment("How many times more snow particles during blizzards (1 = normal, 5 = very intense)")
                        .defineInRange("particleMultiplier", 3, 1, 10);

        public static final ModConfigSpec.DoubleValue WIND_STRENGTH = BUILDER
                        .comment("Strength of wind effects during blizzards (0.0 = no wind, 1.0 = strong wind)")
                        .defineInRange("windStrength", 0.5, 0.0, 1.0);

        public static final ModConfigSpec.BooleanValue ENABLE_RANDOM_BLIZZARD_INTENSITY = BUILDER
                        .comment("Whether blizzards should have random intensity categories (1-5) that vary particle and wind strength")
                        .define("enableRandomBlizzardIntensity", true);

        // Snow Accumulation Settings
        public static final ModConfigSpec.BooleanValue ENABLE_SNOW_ACCUMULATION = BUILDER
                        .comment("Whether snow should pile up over time during blizzards")
                        .define("enableSnowAccumulation", true);

        public static final ModConfigSpec.IntValue SNOW_ACCUMULATION_RATE = BUILDER
                        .comment("How many ticks between snow layer additions (lower = faster accumulation, 20 ticks = 1 second)")
                        .defineInRange("snowAccumulationRate", 200, 20, 1200);

        public static final ModConfigSpec.IntValue MAX_SNOW_LAYERS = BUILDER
                        .comment("Maximum number of snow layers that can accumulate (8, 16, or 24 recommended for 1, 2, or 3 blocks high)")
                        .defineInRange("maxSnowLayers", 16, 8, 24);

        // BlizzAura Block Settings
        public static final ModConfigSpec.IntValue BLIZZAURA_RANGE = BUILDER
                        .comment("Radius in blocks that a BlizzAura block affects (0-1000)")
                        .defineInRange("blizzauraRange", 256, 0, 1000);

        public static final ModConfigSpec.IntValue BLIZZAURA_TEMPERATURE_OFFSET = BUILDER
                        .comment("Temperature adjustment from BlizzAura blocks (higher = colder, allows snow in warmer biomes)")
                        .defineInRange("blizzauraTemperatureOffset", 10, 0, 20);

        // Debug Settings
        public static final ModConfigSpec.BooleanValue DEBUG_MODE = BUILDER
                        .comment("Enable debug logging for blizzard events")
                        .define("debugMode", false);

        static final ModConfigSpec SPEC = BUILDER.build();
}
