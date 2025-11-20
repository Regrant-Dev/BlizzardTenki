package com.regrantdev.blizzardwizard;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
        public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BlizzardTenki.MODID);

        // registerSimpleBlock is deprecated but still works - suppress warning
        public static final DeferredBlock<Block> BLIZZAURA = BLOCKS.register(
                        "blizzaura",
                        registryName -> new Block(
                                        BlockBehaviour.Properties.of()
                                                        .setId(ResourceKey.create(Registries.BLOCK, registryName))
                                                        .strength(0.5f)
                                                        .sound(SoundType.GLASS)
                                                        .lightLevel(state -> 7)));
}