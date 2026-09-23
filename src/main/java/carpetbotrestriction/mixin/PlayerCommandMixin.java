package carpetbotrestriction.mixin;

import carpet.commands.PlayerCommand;
import carpetbotrestriction.CarpetBotRestriction;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(PlayerCommand.class)
public class PlayerCommandMixin {

    /**
     * Before controlling/removing a bot, checks if the player originally spawned the bot.
     */
    @Inject(
            method = "cantManipulate",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContext;getSource()Ljava/lang/Object;"),
            cancellable = true,
            remap = false
    )
    private static void checkIfOwnsBot(@NotNull CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) PlayerEntity bot) {
        ServerCommandSource source = context.getSource();
        if (Permissions.check(source, "carpetbotrestriction.admin.manipulate_all", 2)) return;
        if (!Permissions.check(source, "carpetbotrestriction.user.manipulate_own", true)) {
            CarpetBotRestriction.error(source, "You are not allowed to manipulate bots; contact the server administrator for permission.");
            cir.setReturnValue(true);
            cir.cancel();
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return;
        UUID botID = bot.getUuid();
        UUID playerID = player.getUuid();
        if (botID.equals(playerID)) return;
        ObjectOpenHashSet<UUID> botList = CarpetBotRestriction.PLAYERS.get(playerID);
        if (botList == null || !botList.contains(botID)) {
            CarpetBotRestriction.error(source, "You cannot manipulate this bot, it belongs to another player.");
            CarpetBotRestriction.LOGGER.debug("Prevented {} from manipulating {}.", player.getName(), bot.getName());
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    /**
     * Before spawning a new bot, check if the player will have more bots than the limited amount
     */
    @Inject(
            method = "cantSpawn",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void checkExistingBot(CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Boolean> cir) {
        ServerCommandSource source = context.getSource();
        if (Permissions.check(source, "carpetbotrestriction.admin.create_unlimited", 2)) return;
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return;
        UUID playerID = player.getUuid();
        ObjectOpenHashSet<UUID> botList = CarpetBotRestriction.PLAYERS.get(playerID);
        int playerBotLimit = CarpetBotRestriction.CONFIG.get(String.format("%s.maxBots", playerID),
                CarpetBotRestriction.CONFIG.get("defaultMaxBots", 2));
        if (!Permissions.check(source, "carpetbotrestriction.user.create_own", true)) {
            CarpetBotRestriction.error(source, "You are not allowed to create a new bot; contact the server administrator for permission.");
            cir.setReturnValue(true);
            cir.cancel();
        }
        if (botList != null && botList.size() >= playerBotLimit) {
            CarpetBotRestriction.error(source, String.format("You cannot have more than %d bots.", playerBotLimit));
            CarpetBotRestriction.LOGGER.debug("Prevented {} from spawning new bot: Limit is {} bots.", player.getGameProfile().name(), playerBotLimit);
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    /**
     * Before shadowing, check if the player already is at the bot limit (shadowing creates another bot)
     */
    @Inject(
            method = "shadow",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void canShadow(@NotNull CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Integer> cir) {
        ServerCommandSource source = context.getSource();
        if (Permissions.check(source, "carpetbotrestriction.admin.create_unlimited", 2)) return;
        if (!Permissions.check(source, "carpetbotrestriction.user.shadow", true)) {
            CarpetBotRestriction.error(source, "You are not allowed to shadow; contact the server administrator for permission.");
            cir.setReturnValue(0);
            cir.cancel();
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return;
        if (CarpetBotRestriction.CONFIG.get("removeOnDisconnect", false)) {
            CarpetBotRestriction.error(source, "You cannot shadow: this server is configured so your bots will be removed on disconnect");
            CarpetBotRestriction.LOGGER.debug("Prevented {} from shadowing: removeOnDisconnect is true", player.getGameProfile().name());
            cir.setReturnValue(0);
            cir.cancel();
        }
        UUID playerID = player.getUuid();
        ObjectOpenHashSet<UUID> botList = CarpetBotRestriction.PLAYERS.get(playerID);
        int playerBotLimit = CarpetBotRestriction.CONFIG.get(String.format("%s.maxBots", playerID.toString()),
                CarpetBotRestriction.CONFIG.get("defaultMaxBots", 2));
        if ((botList != null && botList.size() >= playerBotLimit)) {
            CarpetBotRestriction.error(source, String.format("You cannot have more than %d bots. Shadowing will create another bot.", playerBotLimit));
            CarpetBotRestriction.LOGGER.debug("Prevented {} from shadowing: Limit is {} bots.", player.getGameProfile().name(), playerBotLimit);
            cir.setReturnValue(0);
            cir.cancel();
        }
    }

    /**
     * Add a new bot to the list to keep track of which player owns which bot
     */
    @Inject(
            method = "spawn",
            at = @At("HEAD"),
            remap = false
    )
    private static void trackSpawnSource(@NotNull CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Integer> cir) {
        // Track which player tried to spawn bot
        CarpetBotRestriction.CREATE_BOT_SOURCE = context.getSource();
    }
}
