package com.example.palestore.jei;

import com.example.palestore.PaleStore;
import com.example.palestore.ShopOffers;
import com.example.palestore.client.ClientUnlocks;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public class PaleStoreJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return PaleStore.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new StoreCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(StoreCategory.TYPE, ShopOffers.ALL);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Press U on an emerald in JEI to open the whole store.
        registration.addRecipeCatalysts(StoreCategory.TYPE, Items.EMERALD);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        ClientUnlocks.setListener(PaleStoreJeiPlugin::applyLocks);
        applyLocks();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        ClientUnlocks.setListener(() -> {});
    }

    /** Hide the crafting recipes of locked items in JEI, show them again once bought. */
    private static void applyLocks() {
        IJeiRuntime jei = runtime;
        ClientLevel level = Minecraft.getInstance().level;
        if (jei == null || level == null) return;

        IRecipeManager recipes = jei.getRecipeManager();
        RegistryAccess access = level.registryAccess();

        for (ShopOffers.Offer offer : ShopOffers.ALL) {
            List<RecipeHolder<CraftingRecipe>> matches = recipes
                    .createRecipeLookup(RecipeTypes.CRAFTING)
                    .includeHidden()
                    .get()
                    .filter(holder -> holder.value().getResultItem(access).is(offer.result()))
                    .toList();

            if (ClientUnlocks.has(offer.id())) {
                recipes.unhideRecipes(RecipeTypes.CRAFTING, matches);
            } else {
                recipes.hideRecipes(RecipeTypes.CRAFTING, matches);
            }
        }
    }
}
