package com.hbm.ntm.radiation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class RadiationCommands {
    private RadiationCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hbmrad")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .executes(context -> reportPlayer(
                                context.getSource(),
                                context.getSource().getPlayerOrException()
                        ))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> reportPlayer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")
                                ))))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0, RadiationData.MAX_RADIATION))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            float amount = FloatArgumentType.getFloat(context, "amount");
                                            RadiationSystem.data(target).setRadiation(amount);
                                            target.syncData(com.hbm.ntm.registry.ModAttachments.RADIATION);
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("Set " + target.getName().getString() + " radiation to " + amount + " RAD"),
                                                    true
                                            );
                                            return 1;
                                        }))))
                .then(Commands.literal("chunk")
                        .then(Commands.literal("get")
                                .executes(context -> {
                                    var source = context.getSource();
                                    float radiation = ChunkRadiationData.get(source.getLevel()).get(net.minecraft.core.BlockPos.containing(source.getPosition()));
                                    source.sendSuccess(() -> Component.literal("Chunk radiation: " + radiation + " RAD/s"), false);
                                    return Math.round(radiation);
                                }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0, 100_000))
                                        .executes(context -> {
                                            var source = context.getSource();
                                            float amount = FloatArgumentType.getFloat(context, "amount");
                                            ChunkRadiationData data = ChunkRadiationData.get(source.getLevel());
                                            data.set(net.minecraft.world.level.ChunkPos.asLong(
                                                    net.minecraft.util.Mth.floor(source.getPosition().x) >> 4,
                                                    net.minecraft.util.Mth.floor(source.getPosition().z) >> 4
                                            ), amount);
                                            source.sendSuccess(() -> Component.literal("Set chunk radiation to " + amount + " RAD/s"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ChunkRadiationData.get(context.getSource().getLevel()).clear();
                                    context.getSource().sendSuccess(() -> Component.literal("Cleared dimension radiation"), true);
                                    return 1;
                                }))));
    }

    private static int reportPlayer(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
        RadiationData data = RadiationSystem.data(player);
        source.sendSuccess(
                () -> Component.literal(player.getName().getString()
                        + ": " + data.radiation() + " RAD, exposure " + data.radBuf() + " RAD/s"),
                false
        );
        return Math.round(data.radiation());
    }
}
