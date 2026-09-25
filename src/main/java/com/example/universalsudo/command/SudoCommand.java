package com.example.universalsudo.command;

import com.example.universalsudo.network.SudoKeyPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;

/**
 * Implements:
 *
 *   /sudo <player> <message_or_command>
 *   /sudo <player> press <key>
 *
 * With the Universal Sudo client installed on the target, the press command
 * sends a physical keyboard-key event to that client. Without the client
 * companion, common gameplay keys use server-side fallbacks where possible.
 */
public final class SudoCommand {
	private static final double MOVEMENT_STEP = 0.10D;

	private static final Map<String, Integer> KEY_CODES = Map.ofEntries(
			Map.entry("w", 87),
			Map.entry("a", 65),
			Map.entry("s", 83),
			Map.entry("d", 68),
			Map.entry("e", 69),
			Map.entry("q", 81),
			Map.entry("r", 82),
			Map.entry("f", 70),
			Map.entry("space", 32),
			Map.entry("shift", 340),
			Map.entry("ctrl", 341),
			Map.entry("1", 49),
			Map.entry("2", 50),
			Map.entry("3", 51),
			Map.entry("4", 52),
			Map.entry("5", 53),
			Map.entry("6", 54),
			Map.entry("7", 55),
			Map.entry("8", 56),
			Map.entry("9", 57)
	);

	private static final String PRESS_HELP =
			"Keys: w a s d e q r f space shift ctrl 1-9";

	private SudoCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sudo")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("message_or_command", StringArgumentType.greedyString())
										.executes(SudoCommand::run)))
		);
	}

	private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack issuer = context.getSource();
		ServerPlayer target = EntityArgument.getPlayer(context, "player");

		String raw = StringArgumentType.getString(context, "message_or_command");
		String trimmed = raw.trim();

		if (trimmed.isEmpty()) {
			issuer.sendFailure(Component.literal("You must provide a message or a command to run."));
			return 0;
		}

		if (looksLikePress(trimmed)) {
			return runPress(issuer, target, trimmed);
		}

		MinecraftServer server = issuer.getServer();

		if (looksLikeCommand(trimmed, server)) {
			return runAsCommand(issuer, target, server, trimmed);
		}

		return runAsChatMessage(issuer, target, server, trimmed);
	}

	private static boolean looksLikePress(String trimmed) {
		return trimmed.regionMatches(true, 0, "press", 0, 5)
				&& (trimmed.length() == 5 || Character.isWhitespace(trimmed.charAt(5)));
	}

	private static int runPress(CommandSourceStack issuer, ServerPlayer target, String trimmed) {
		String[] parts = trimmed.split("\\s+");
		if (parts.length < 2 || parts[1].isBlank()) {
			issuer.sendFailure(Component.literal("Usage: /sudo <player> press <key>"));
			issuer.sendFailure(Component.literal(PRESS_HELP));
			return 0;
		}

		String keyName = parts[1].toLowerCase(Locale.ROOT);
		Integer keyCode = KEY_CODES.get(keyName);

		if (keyCode == null) {
			issuer.sendFailure(Component.literal("Unknown key '" + parts[1] + "'. " + PRESS_HELP));
			return 0;
		}

		// Prefer the client path so E, W, GUI keys, remapped bindings, etc.
		// behave like an actual keyboard press on the target's client.
		if (ServerPlayNetworking.canSend(target, SudoKeyPayload.TYPE)) {
			ServerPlayNetworking.send(target, new SudoKeyPayload(keyCode));
			issuer.sendSuccess(() -> Component.literal(
					"Pressed " + keyName.toUpperCase(Locale.ROOT) + " for " + target.getGameProfile().name() + "."), true);
			return 1;
		}

		return runServerFallback(issuer, target, keyName);
	}

	private static int runServerFallback(CommandSourceStack issuer, ServerPlayer target, String keyName) {
		switch (keyName) {
			case "w", "a", "s", "d" -> {
				Vec3 movement = getMovementVector(target, keyName);
				target.move(MoverType.SELF, movement.scale(MOVEMENT_STEP));
				issuer.sendSuccess(() -> Component.literal(
						"Moved " + target.getGameProfile().name() + " with " + keyName.toUpperCase(Locale.ROOT) + "."), true);
				return 1;
			}
			case "space" -> {
				target.jumpFromGround();
				issuer.sendSuccess(() -> Component.literal(
						"Made " + target.getGameProfile().name() + " jump."), true);
				return 1;
			}
			case "q" -> {
				target.drop(false);
				issuer.sendSuccess(() -> Component.literal(
						"Made " + target.getGameProfile().name() + " drop their selected item."), true);
				return 1;
			}
			case "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
				int slot = Integer.parseInt(keyName) - 1;
				target.getInventory().setSelectedSlot(slot);
				target.connection.send(new ClientboundSetHeldSlotPacket(slot));
				issuer.sendSuccess(() -> Component.literal(
						"Selected hotbar slot " + (slot + 1) + " for " + target.getGameProfile().name() + "."), true);
				return 1;
			}
			case "e" -> {
				if (target.getVehicle() instanceof net.minecraft.world.entity.vehicle.HasCustomInventoryScreen) {
					ServerGamePacketListenerImpl connection = target.connection;
					connection.handlePlayerCommand(
							new ServerboundPlayerCommandPacket(
									target,
									ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
					issuer.sendSuccess(() -> Component.literal(
							"Requested inventory-open for " + target.getGameProfile().name() + "."), true);
					return 1;
				}

				issuer.sendFailure(Component.literal(
						"Press E needs Universal Sudo on " + target.getGameProfile().name()
						+ "'s client because the normal inventory screen is client-side."));
				return 0;
			}
			default -> {
				issuer.sendFailure(Component.literal(
						"Key " + keyName + " needs Universal Sudo on the target client."));
				return 0;
			}
		}
	}

	private static Vec3 getMovementVector(ServerPlayer target, String keyName) {
		float yaw = target.getYRot() * (float) (Math.PI / 180.0D);
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
		Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);

		return switch (keyName) {
			case "w" -> forward;
			case "a" -> left;
			case "s" -> forward.scale(-1.0D);
			case "d" -> left.scale(-1.0D);
			default -> Vec3.ZERO;
		};
	}

	private static boolean looksLikeCommand(String trimmed, MinecraftServer server) {
		if (trimmed.startsWith("/")) {
			return true;
		}

		String firstWord = trimmed.split(" ", 2)[0];
		CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();
		return root.getChild(firstWord) != null;
	}

	private static int runAsCommand(CommandSourceStack issuer, ServerPlayer target, MinecraftServer server, String raw) {
		CommandSourceStack targetSource = target.createCommandSourceStack();
		server.getCommands().performPrefixedCommand(targetSource, raw);

		String withoutSlash = raw.startsWith("/") ? raw.substring(1) : raw;
		issuer.sendSuccess(() -> Component.literal(
				"Made " + target.getGameProfile().name() + " run: /" + withoutSlash), true);

		return 1;
	}

	private static int runAsChatMessage(CommandSourceStack issuer, ServerPlayer target, MinecraftServer server, String raw) {
		PlayerChatMessage chatMessage = PlayerChatMessage.system(raw);
		ChatType.Bound boundChatType = ChatType.bind(ChatType.CHAT, target);

		server.getPlayerList().broadcastChatMessage(chatMessage, target, boundChatType);

		issuer.sendSuccess(() -> Component.literal(
				"Sent chat message as " + target.getGameProfile().name() + "."), true);

		return 1;
	}
}
