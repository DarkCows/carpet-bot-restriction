package carpetbotrestriction;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import it.unimi.dsi.fastutil.objects.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.*;

public class CarpetBotRestriction implements ModInitializer {
	public static final String MOD_ID = "carpetbotrestriction";
	public static CBRConfig CONFIG;
	public static final Object2ObjectOpenHashMap<UUID, ObjectOpenHashSet<UUID>> PLAYERS = new Object2ObjectOpenHashMap<>();
	public static final Object2ObjectOpenHashMap<UUID, UUID> BOTS = new Object2ObjectOpenHashMap<>();
	// Used to track player that tried to create a bot
	public static ServerCommandSource CREATE_BOT_SOURCE;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	// File to store UUIDs of real players to prevent them from being used as bots
	public static final ObjectOpenHashSet<UUID> REAL_PLAYERS = new ObjectOpenHashSet<>();
	@Override
	public void onInitialize() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("carpetbotrestriction/config.toml");
		try {
			Files.createDirectories(path.getParent());
		} catch (IOException e) {
			CarpetBotRestriction.LOGGER.error("Failed to create directory", e);
		}
		CONFIG = new CBRConfig(path.toString());
		if (Files.notExists(path)) {
			defaultConfig();
			CONFIG.save();
		}
		else {
			try {
				CONFIG.load();
			}
			catch (Exception e) {
				CONFIG.clear();
				defaultConfig();
				CONFIG.save();
			}
		}
		path = FabricLoader.getInstance().getConfigDir().resolve("carpetbotrestriction/real_player_uuids.txt");
		if (Files.exists(path)) {
			try (BufferedReader in = Files.newBufferedReader(path)) {
				String line;
				while ((line = in.readLine()) != null) {
					REAL_PLAYERS.add(UUID.fromString(line.trim()));
				}
			}
			catch (IOException e) {
				CarpetBotRestriction.LOGGER.error(e.toString());
			}
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(literal("cbr")
				.requires(Permissions.require("carpetbotrestriction.config", 3))
				.then(literal("defaultMaxBots")
					.then(argument("defaultMaxBots", IntegerArgumentType.integer())
						.executes(context -> {
							int value = IntegerArgumentType.getInteger(context, "defaultMaxBots");
							if (value < 0) {
								CarpetBotRestriction.error(context.getSource(),"Number must be >= 0.");
								return 0;
							}
							CONFIG.set("defaultMaxBots", value);
							CompletableFuture.runAsync(() -> CONFIG.save());
							CarpetBotRestriction.say(context.getSource(), "Set defaultMaxBots to " + value);
							return 1;
						})))
				.then(literal("removeOnDisconnect")
					.then(argument("removeOnDisconnect", BoolArgumentType.bool())
						.executes(context -> {
							boolean value = BoolArgumentType.getBool(context, "removeOnDisconnect");
							CONFIG.set("removeOnDisconnect", value);
							CompletableFuture.runAsync(() -> CONFIG.save());
							CarpetBotRestriction.say(context.getSource(), "Set removeOnDisconnect to " + value);
							return 1;
						})))
				.then(literal("player")
					.then(argument("player", EntityArgumentType.player())
						.then(literal("maxBots")
							.then(literal("set")
								.then(argument("maxBots", IntegerArgumentType.integer())
								.executes(context -> {
									ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
									int value = IntegerArgumentType.getInteger(context, "maxBots");
									if (value < 0) {
										CarpetBotRestriction.error(context.getSource(), "Number must be >= 0.");
										return 0;
									}
									CONFIG.set(String.format("%s.maxBots", player.getUuid().toString()), value);
									CompletableFuture.runAsync(() -> CONFIG.save());
									CarpetBotRestriction.say(context.getSource(), String.format("Set player %s's maxBots to %d", player.getGameProfile().name(), value));
									return 1;
								})))
							.then(literal("unset")
							.executes(context -> {
								ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
								CONFIG.remove(String.format("%s.maxBots", player.getUuid().toString()));
								CompletableFuture.runAsync(() -> CONFIG.save());
								CarpetBotRestriction.say(context.getSource(), String.format("Unset %s's maxBots to default value", player.getGameProfile().name()));
								return 1;
							}))))));});

	}

	private static void defaultConfig() {
		// Default maximum amount of bots per player (unless specified otherwise)
		CONFIG.set("defaultMaxBots", 2);
		// By default, when a player disconnects their bots will not be removed
		CONFIG.set("removeBotsOnDisconnect", false);
	}

	public static void say(@NotNull ServerCommandSource source, String message) {
		source.sendMessage(Text.literal("[Carpet Bot Restriction] ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF07F1D))).append(Text.literal(message)));
	}

	public static void error(@NotNull ServerCommandSource source, String message) {
		source.sendError(Text.literal("[Carpet Bot Restriction] ").append(Text.literal(message)));
	}
}