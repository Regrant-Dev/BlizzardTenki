package com.regrantdev.blizzardwizard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.regrantdev.blizzardwizard.BlizzardTenkiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.injection.Inject;

// This mixin targets WeatherEffectRenderer to cancel vanilla rain/snow particle rendering
// when the player is near a BlizzAura block (while keeping dark storm clouds)
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    // Cancel vanilla weather particle rendering in cold biomes and near BlizzAura
    // This allows our custom particles to show instead
    // Method: public void render(MultiBufferSource, Vec3, WeatherRenderState, LevelRenderState)
    @Inject(method = "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;Lnet/minecraft/client/renderer/state/LevelRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cancelWeatherRendering(MultiBufferSource bufferSource, Vec3 cameraPosition,
            net.minecraft.client.renderer.state.WeatherRenderState renderState,
            net.minecraft.client.renderer.state.LevelRenderState levelRenderState,
            CallbackInfo ci) {

        // Get the Minecraft client instance
        Minecraft mc = Minecraft.getInstance();

        // Safety check - make sure player and level exist
        if (mc.player == null || mc.level == null) {
            return; // Don't cancel, render normally
        }

        // Get player position and biome
        BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.biome.Biome biome = mc.level.getBiome(playerPos).value();

        // Check if player is near a BlizzAura block
        boolean nearBlizzAura = BlizzardTenkiClient.isPlayerNearBlizzAura(playerPos);

        // Check if it's a cold biome (where it would naturally snow)
        boolean isColdBiome = biome.coldEnoughToSnow(playerPos, mc.level.getSeaLevel());

        // Cancel vanilla weather particles if: (cold biome) OR (near BlizzAura)
        // This lets our custom particles take over completely
        if (isColdBiome || nearBlizzAura) {
            ci.cancel(); // Cancel the method - don't render vanilla weather particles
        }
        // Otherwise, let vanilla rendering proceed normally (warm biomes keep rain)
    }

    // Cancel rain splash particles and rain sounds in cold biomes and near BlizzAura
    // (Cold biomes should have snow, not rain splashes!)
    // Method: public void tickRainParticles(ClientLevel, Camera, int, ParticleStatus)
    @Inject(method = "tickRainParticles(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/Camera;ILnet/minecraft/server/level/ParticleStatus;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cancelRainSplashParticles(net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.client.Camera camera, int ticks,
            net.minecraft.server.level.ParticleStatus particleStatus,
            CallbackInfo ci) {

        // Get the Minecraft client instance
        Minecraft mc = Minecraft.getInstance();

        // Safety check - make sure player and level exist
        if (mc.player == null || mc.level == null) {
            return; // Don't cancel, spawn normally
        }

        // Get player position and biome
        BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.biome.Biome biome = mc.level.getBiome(playerPos).value();

        // Check if player is near a BlizzAura block
        boolean nearBlizzAura = BlizzardTenkiClient.isPlayerNearBlizzAura(playerPos);

        // Check if it's a cold biome
        boolean isColdBiome = biome.coldEnoughToSnow(playerPos, mc.level.getSeaLevel());

        // Cancel rain splash particles and sounds if: (cold biome) OR (near BlizzAura)
        if (isColdBiome || nearBlizzAura) {
            ci.cancel(); // Cancel the method - don't spawn splash particles or play rain sounds
        }
        // Otherwise, let splash particles and sounds play normally
    }
}
