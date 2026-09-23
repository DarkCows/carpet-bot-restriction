package carpetbotrestriction.mixin;

import carpet.patches.EntityPlayerMPFake;
import carpetbotrestriction.CarpetBotRestriction;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EntityPlayerMPFake.class)
public class EntityPlayerMPFakeMixin extends ServerPlayerEntity {

    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
    }

    /**
     * When an EntityPlayerMPFake is created, register it with the player who created it
     */
    @Inject(
            method = "<init>",
            at = @At("TAIL"),
            remap = false
    )
    private void registerBotOnCreate(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions cli, boolean shadow, CallbackInfo ci) {
        // Skip shadow players - they're clones of real players
        if (shadow) return;
        
        ServerPlayerEntity player = CarpetBotRestriction.CREATE_BOT_SOURCE.getPlayer();
        if (player == null) return;
        
        UUID playerID = player.getUuid();
        UUID botID = profile.id();
        
        ObjectOpenHashSet<UUID> players;
        if (!CarpetBotRestriction.PLAYERS.containsKey(playerID)) {
            players = new ObjectOpenHashSet<>();
            CarpetBotRestriction.PLAYERS.put(playerID, players);
        }
        else {
            players = CarpetBotRestriction.PLAYERS.get(playerID);
        }
        players.add(botID);
        CarpetBotRestriction.BOTS.put(botID, playerID);
        CarpetBotRestriction.LOGGER.debug("Assigned bot {} (UUID: {}) to player {}.", profile.name(), botID, player.getName());
    }

    /**
     * Before spawning a bot, check if the player is allowed to create it
     * (e.g., bot cannot impersonate a real player who has logged in)
     */
    @Inject(
            method = "createFake",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void checkIfBotCreateAllowed(
            String username, MinecraftServer server, Vec3d pos, double yaw, double pitch,
            RegistryKey<World> dimensionId, GameMode gamemode, boolean flying,
            @NotNull CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = CarpetBotRestriction.CREATE_BOT_SOURCE.getPlayer();
        if (player == null) return;
        
        // Check if the bot about to be spawned is a real player that has logged onto the server
        UUID offlineBotID = net.minecraft.util.Uuids.getOfflinePlayerUuid(username);
        if (!Permissions.check(CarpetBotRestriction.CREATE_BOT_SOURCE, "carpetbotrestriction.admin.create_real", 2) && CarpetBotRestriction.REAL_PLAYERS.contains(offlineBotID)) {
            CarpetBotRestriction.error(CarpetBotRestriction.CREATE_BOT_SOURCE, "You cannot create a bot with this name - it belongs to a real player.");
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}

