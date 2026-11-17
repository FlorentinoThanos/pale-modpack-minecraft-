package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /thepale colossus ...}: finding the Frost Colossus and, for operators, putting it somewhere.
 * <ul>
 * <li>{@code where}: where it is (anybody may ask).</li>
 * <li>{@code summon}: puts it down where the operator stands, taking away any other.</li>
 * <li>{@code home}: sends it back to the middle of the cold island.</li>
 * <li>{@code sleep}, {@code wake}: makes it sit down and sleep, or get up.</li>
 * <li>{@code status}: what it is doing and how far it could walk in each direction, for finding out why it stands.</li>
 * </ul>
 */
public final class ColossusCommands {
    private ColossusCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("thepale").then(Commands.literal("colossus")
                .then(Commands.literal("where").executes(ColossusCommands::where))
                .then(Commands.literal("summon").requires(source -> source.hasPermission(2)).executes(ColossusCommands::summon))
                .then(Commands.literal("home").requires(source -> source.hasPermission(2)).executes(ColossusCommands::home))
                .then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(context -> {
                    List<FrostColossus> found = loaded(context.getSource().getServer().overworld());
                    found.forEach(colossus -> context.getSource().sendSuccess(() -> Component.literal(colossus.status()), false));
                    return found.size();
                }))
                .then(Commands.literal("sleep").requires(source -> source.hasPermission(2)).executes(context -> rest(context, true)))
                .then(Commands.literal("wake").requires(source -> source.hasPermission(2)).executes(context -> rest(context, false)))));
    }

    /** {@return every colossus that is loaded in the Overworld} */
    static List<FrostColossus> loaded(ServerLevel overworld) {
        List<FrostColossus> found = new ArrayList<>();
        for (Entity entity : overworld.getAllEntities()) {
            if (entity instanceof FrostColossus colossus && colossus.isAlive()) {
                found.add(colossus);
            }
        }
        return found;
    }

    private static int where(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<FrostColossus> found = loaded(source.getServer().overworld());
        if (found.isEmpty()) {
            double[] heart = ColossusRange.heart();
            source.sendSuccess(() -> Component.translatable("commands.thepale.colossus.where.none", Mth.floor(heart[0]), Mth.floor(heart[1])), false);
            return 0;
        }
        for (FrostColossus colossus : found) {
            BlockPos pos = colossus.blockPosition();
            Vec3 from = source.getPosition();
            int distance = (int) Math.sqrt(Mth.square(from.x - pos.getX()) + Mth.square(from.z - pos.getZ()));
            source.sendSuccess(() -> Component.translatable(
                    "commands.thepale.colossus.where", pos.getX(), pos.getY(), pos.getZ(), distance), false);
        }
        return found.size();
    }

    private static int summon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        if (source.getLevel() != overworld) {
            source.sendFailure(Component.translatable("commands.thepale.colossus.overworld"));
            return 0;
        }
        if (!CommonConfig.get().colossus().enabled()) {
            source.sendFailure(Component.translatable("commands.thepale.colossus.disabled"));
            return 0;
        }
        // There is only ever one.
        loaded(overworld).forEach(old -> {
            old.ejectPassengers();
            old.discard();
        });
        Vec3 at = source.getPosition();
        FrostColossus colossus = ColossusRegistry.FROST_COLOSSUS.get().create(overworld);
        if (colossus == null) {
            return 0;
        }
        colossus.moveTo(at.x, at.y, at.z, source.getRotation().y, 0);
        if (!ColossusRange.home(at.x, at.z)) {
            // Not its own country: it stays around where it was put.
            colossus.setAnchor(BlockPos.containing(at));
        }
        overworld.addFreshEntity(colossus);
        source.sendSuccess(() -> Component.translatable("commands.thepale.colossus.summoned"), true);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        BlockPos home = ColossusRange.findHome(overworld, 0);
        if (home == null) {
            source.sendFailure(Component.translatable("commands.thepale.colossus.no_home"));
            return 0;
        }
        List<FrostColossus> found = loaded(overworld);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.thepale.colossus.not_loaded"));
            return 0;
        }
        FrostColossus colossus = found.get(0);
        colossus.ejectPassengers();
        colossus.setAnchor(null);
        colossus.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, colossus.getYRot(), 0);
        source.sendSuccess(() -> Component.translatable("commands.thepale.colossus.home", home.getX(), home.getY(), home.getZ()), true);
        return 1;
    }

    private static int rest(CommandContext<CommandSourceStack> context, boolean rest) {
        CommandSourceStack source = context.getSource();
        List<FrostColossus> found = loaded(source.getServer().overworld());
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.thepale.colossus.not_loaded"));
            return 0;
        }
        found.forEach(colossus -> colossus.setRest(rest));
        source.sendSuccess(() -> Component.translatable(rest ? "commands.thepale.colossus.sleep" : "commands.thepale.colossus.wake"), true);
        return found.size();
    }
}
