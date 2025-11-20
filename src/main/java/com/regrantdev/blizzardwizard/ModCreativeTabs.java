package com.regrantdev.blizzardwizard;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    // Create a DeferredRegister for CREATIVE_MODE_TABS
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, BlizzardTenki.MODID);

    // Register our custom "Blizzard Tenki" tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLIZZARD_TENKI_TAB = CREATIVE_MODE_TABS
            .register("blizzard_tenki_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blizzardtenki"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.BLIZZAURA.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Add all our items to the tab
                        output.accept(ModItems.BLIZZAURA.get());
                    })
                    .build());
}