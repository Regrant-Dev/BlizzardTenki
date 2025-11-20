package com.regrantdev.blizzardwizard;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlizzardTenki.MODID);

    public static final DeferredItem<BlockItem> BLIZZAURA = ITEMS.registerSimpleBlockItem(ModBlocks.BLIZZAURA);
}