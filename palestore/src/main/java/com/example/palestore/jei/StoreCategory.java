package com.example.palestore.jei;

import com.example.palestore.PaleStore;
import com.example.palestore.ShopOffers.Offer;
import com.example.palestore.client.ClientUnlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The "Recipe Store" tab: shows the item and its advancement requirement. */
public class StoreCategory extends AbstractRecipeCategory<Offer> {
    public static final RecipeType<Offer> TYPE = RecipeType.create(PaleStore.MOD_ID, "recipe_store", Offer.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 44;

    public StoreCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("palestore.category.title"),
                gui.createDrawableItemLike(Items.EMERALD), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Offer offer, IFocusGroup focuses) {
        builder.addOutputSlot(134, 8)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(offer.icon()));
    }

    @Override
    public void draw(Offer offer, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("palestore.requirement"), 8, 5, 0xFF555555, false);
        graphics.drawString(font, Component.translatable(offer.requirementKey()), 8, 17, 0xFF555555, false);
        Component status = Component.translatable(ClientUnlocks.has(offer.id())
                ? "palestore.status.unlocked" : "palestore.status.locked");
        int color = ClientUnlocks.has(offer.id()) ? 0xFF3B9A2A : 0xFFB02020;
        graphics.drawString(font, status, 8, 30, color, false);
    }

    @Override
    public ResourceLocation getRegistryName(Offer offer) {
        return offer.id();
    }
}
