package com.example.palestore.enchant;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Smelting: blocks mined with it drop what a furnace would make of their drops, with the furnace's experience. */
public final class SmeltingModifier extends LootModifier {
    public static final MapCodec<SmeltingModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, SmeltingModifier::new));

    public SmeltingModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (context.getParamOrNull(LootContextParams.BLOCK_STATE) == null || tool == null
                || PaleEnchants.level(context.getLevel().registryAccess(), PaleEnchants.SMELTING, tool) <= 0) {
            return loot;
        }
        ServerLevel level = context.getLevel();
        ObjectArrayList<ItemStack> smelted = new ObjectArrayList<>();
        float experience = 0;
        for (ItemStack stack : loot) {
            SingleRecipeInput input = new SingleRecipeInput(stack);
            Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
            if (stack.isEmpty() || recipe.isEmpty()) {
                smelted.add(stack);
                continue;
            }
            ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
            if (result.isEmpty()) {
                smelted.add(stack);
                continue;
            }
            result.setCount(result.getCount() * stack.getCount());
            while (result.getCount() > result.getMaxStackSize()) {
                smelted.add(result.split(result.getMaxStackSize()));
            }
            smelted.add(result);
            experience += recipe.get().value().getExperience() * stack.getCount();
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (experience > 0 && origin != null) {
            int whole = Mth.floor(experience);
            if (level.random.nextFloat() < experience - whole) {
                whole++;
            }
            if (whole > 0) {
                ExperienceOrb.award(level, origin, whole);
            }
        }
        return smelted;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
