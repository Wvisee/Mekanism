package mekanism.client.jei;

import mekanism.client.gui.GuiEnergizedSmelter;
import mekanism.client.gui.GuiRotaryCondensentrator;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlock;
import mekanism.common.integration.crafttweaker.handlers.EnergizedSmelter;
import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.util.ResourceLocation;

public class GuiHandlerRegistryHelper {

    public static <T extends ContainerScreen<?>> void register(IGuiHandlerRegistration registry, MekanismBlock mekanismBlock, Class<? extends T> guiContainerClass,
          int xPos, int yPos, int width, int height) {
        if (mekanismBlock.isEnabled()) {
            registry.addRecipeClickArea(guiContainerClass, xPos, yPos, width, height, mekanismBlock.getRegistryName());
        }
    }

    public static void registerCondensentrator(IGuiHandlerRegistration registry) {
        MekanismBlock mekanismBlock = MekanismBlock.ROTARY_CONDENSENTRATOR;
        if (mekanismBlock.isEnabled()) {
            ResourceLocation condensentrating = new ResourceLocation(Mekanism.MODID, "rotary_condensentrator_condensentrating");
            ResourceLocation decondensentrating = new ResourceLocation(Mekanism.MODID, "rotary_condensentrator_decondensentrating");
            registry.addRecipeClickArea(GuiRotaryCondensentrator.class, 64, 39, 48, 8, condensentrating, decondensentrating);
        }
    }

    public static void registerSmelter(IGuiHandlerRegistration registry) {
        MekanismBlock mekanismBlock = MekanismBlock.ENERGIZED_SMELTER;
        if (mekanismBlock.isEnabled()) {
            if (Mekanism.hooks.CraftTweakerLoaded && EnergizedSmelter.hasRemovedRecipe()) {// Removed / Removed + Added
                registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7, mekanismBlock.getRegistryName());
            } else if (Mekanism.hooks.CraftTweakerLoaded && EnergizedSmelter.hasAddedRecipe()) {// Added but not removed
                registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7, VanillaRecipeCategoryUid.FURNACE, mekanismBlock.getRegistryName());
            } else {
                //Only use furnace list, so no extra registration.
                registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7, VanillaRecipeCategoryUid.FURNACE);
            }
        }
    }
}