package carpetbotrestriction.mixin;

import carpetbotrestriction.CarpetBotRestriction;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public abstract ServerPlayerEntity getPlayer();

    /**
     * If a bot disconnects, remove from lists
     */
    @Inject(
            method = "onDisconnected",
            at = @At("HEAD")
    )
    private void logDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        UUID entity = this.getPlayer().getUuid();
        UUID player = CarpetBotRestriction.BOTS.remove(entity);
        // If disconnected player is a bot, remove it from the lists
        if (player != null) {
            ObjectOpenHashSet<UUID> bots = CarpetBotRestriction.PLAYERS.get(player);
            if (bots == null) return;
            bots.remove(entity);
            return;
        }
        // If disconnected player is a real player, then if removeOnDisconnect is true, then
        // kill all the player's bots
        if (!CarpetBotRestriction.CONFIG.get("removeOnDisconnect", false)) return;
        ObjectOpenHashSet<UUID> bots = CarpetBotRestriction.PLAYERS.get(entity);
        if (bots == null) return;
        MinecraftServer mc = this.getPlayer().getEntityWorld().getServer();
        if (mc == null) return;
        PlayerManager playerManager = mc.getPlayerManager();
        if (playerManager == null) return;
        for (UUID bot : bots) {
            if (bot == null) continue;
            ServerPlayerEntity toRemove = playerManager.getPlayer(bot);
            if (toRemove == null) continue;
            toRemove.kill(toRemove.getEntityWorld());
            bots.remove(bot);
        }
        if (bots.isEmpty()) {
            CarpetBotRestriction.PLAYERS.remove(entity);
        }
    }
}
