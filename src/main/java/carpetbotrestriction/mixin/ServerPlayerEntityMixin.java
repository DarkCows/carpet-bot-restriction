package carpetbotrestriction.mixin;

import carpetbotrestriction.CarpetBotRestriction;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity {

    public ServerPlayerEntityMixin(World world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Unique
    private static final Path path = FabricLoader.getInstance().getConfigDir().resolve("carpetbotrestriction/real_player_uuids.txt");

    // Track UUIDs of created players
    @Inject(
            method = "<init>",
            at = @At("CTOR_HEAD")
    )
    private void detectRealPlayerJoin(CallbackInfo ci) {
        if (((ServerPlayerEntity)(Object)this).getClass() == ServerPlayerEntity.class) {
            CarpetBotRestriction.REAL_PLAYERS.add(this.getUuid());
            CompletableFuture.runAsync(() -> {
                try (BufferedWriter out = Files.newBufferedWriter(path)) {
                    for (UUID uuid : CarpetBotRestriction.REAL_PLAYERS) {
                        out.write(uuid.toString());
                        out.newLine();
                    }
                }
                catch (IOException e) {
                    CarpetBotRestriction.LOGGER.error(e.toString());
                }
            });
        }
    }
}
