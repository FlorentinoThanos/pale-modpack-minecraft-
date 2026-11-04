package com.example.palestore.enchant;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Smashing: ores mined with it break into Create's crushed ore (raw iron becomes crushed raw iron, and so on, Oritech's
 * nickel and platinum too when Create knows them), twice as many as the ore would drop. Anything without a crushed
 * form, like coal or diamonds, simply drops twice.
 */
public final class SmashingModifier extends LootModifier {
    public static final MapCodec<SmashingModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, SmashingModifier::new));

    public SmashingModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (state == null || tool == null || !state.is(Tags.Blocks.ORES)
                || PaleEnchants.level(context.getLevel().registryAccess(), PaleEnchants.SMASHING, tool) <= 0) {
            return loot;
        }
        ObjectArrayList<ItemStack> smashed = new ObjectArrayList<>();
        for (ItemStack stack : loot) {
            if (stack.isEmpty() || stack.is(state.getBlock().asItem())) {
                smashed.add(stack); // the ore block itself (e.g. from another mod's tool) isn't doubled
                continue;
            }
            Item result = crushed(stack.getItem()).orElse(stack.getItem());
            ItemStack doubled = stack.transmuteCopy(result, stack.getCount() * 2);
            // Keep within stack limits by splitting.
            while (doubled.getCount() > doubled.getMaxStackSize()) {
                smashed.add(doubled.split(doubled.getMaxStackSize()));
            }
            smashed.add(doubled);
        }
        return smashed;
    }

    /** Create's crushed form of a raw ore: anything:raw_x becomes create:crushed_raw_x, if Create has it. */
    private static Optional<Item> crushed(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getPath().startsWith("raw_")) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "crushed_" + id.getPath()));
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
