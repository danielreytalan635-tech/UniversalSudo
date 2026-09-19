package com.example.universalsudo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Implements:
 *
 *   /sudo <player> <message_or_command>
 *
 * Behavior:
 *  - If the second argument starts with '/', it is executed as a command,
 *    run through the target player's own CommandSourceStack (their real
 *    permission level, position, world and rotation - never OP'd, never
 *    run as console).
 *  - If it does NOT start with '/', but its first word is nonetheless the
 *    name of a command that is actually registered on this server, it is
 *    still treated as a command. This is what makes the leading slash
 *    "optional where practical", e.g. "/sudo Steve gamemode creative".
 *  - Otherwise, the whole string is sent through the normal chat system,
 *    attributed to the target player, exactly as if they had typed it.
 *
 * Only one command is registered ("sudo") - there is deliberately no
 * separate "/sayas" or similar command.
 */
public final class SudoCommand {
	/**
	 * Permission level required to use /sudo. Level 2 is the same level
	 * vanilla requires for commands like /gamemode, /give and /tp - i.e.
	 * a normal server operator. This relies entirely on vanilla's built-in
	 * OP system; no external permissions mod is required or supported.
	 */
	private static final int REQUIRED_PERMISSION_LEVEL = 2;

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

		// EntityArgument.player() + getPlayer() already produce the vanilla
		// "No player was found" error automatically if the name/selector
		// doesn't resolve to exactly one online player - nothing extra needed.
		ServerPlayer target = EntityArgument.getPlayer(context, "player");

		String raw = StringArgumentType.getString(context, "message_or_command");
		String trimmed = raw.trim();

		if (trimmed.isEmpty()) {
			// Brigadier's greedy string requires at least one character to be
			// typed, but guard anyway in case it was e.g. just whitespace.
			issuer.sendFailure(Component.literal("You must provide a message or a command to run."));
			return 0;
		}

		MinecraftServer server = issuer.getServer();

		if (looksLikeCommand(trimmed, server)) {
			return runAsCommand(issuer, target, server, trimmed);
		} else {
			return runAsChatMessage(issuer, target, server, trimmed);
		}
	}

	/**
	 * A leading '/' always means "this is a command". Without one, we still
	 * treat it as a command if its first word matches something actually
	 * registered in the dispatcher - so "/sudo Steve gamemode creative"
	 * works the same as "/sudo Steve /gamemode creative", while an ordinary
	 * sentence like "/sudo Steve Hello everyone!" still falls through to
	 * chat, since "Hello" is not a registered command.
	 */
	private static boolean looksLikeCommand(String trimmed, MinecraftServer server) {
		if (trimmed.startsWith("/")) {
			return true;
		}

		String firstWord = trimmed.split(" ", 2)[0];
		CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();
		return root.getChild(firstWord) != null;
	}

	/**
	 * Runs {@code raw} as a command using a CommandSourceStack that
	 * represents the target player: same entity, same world/position/
	 * rotation, and a permission level taken from their real, current OP
	 * status. The player is never temporarily or permanently OP'd, and the
	 * command is never run as the server/console.
	 */
	private static int runAsCommand(CommandSourceStack issuer, ServerPlayer target, MinecraftServer server, String raw) {
		CommandSourceStack targetSource = target.createCommandSourceStack();

		// performPrefixedCommand accepts the command with or without a
		// leading '/' and parses + dispatches it against targetSource.
		// Any failure (unknown command, bad syntax, insufficient
		// permission on the target's own account, etc.) is reported back
		// through targetSource using normal Minecraft-style error text,
		// exactly as if the target player had typed it themselves.
		server.getCommands().performPrefixedCommand(targetSource, raw);

		String withoutSlash = raw.startsWith("/") ? raw.substring(1) : raw;
		issuer.sendSuccess(() -> Component.literal(
				"Made " + target.getGameProfile().name() + " run: /" + withoutSlash), true);

		return 1;
	}

	/**
	 * Broadcasts {@code raw} as a genuine chat message attributed to the
	 * target player: it goes through PlayerList#broadcastChatMessage bound
	 * to the target's own display name and GameProfile, the same broadcast
	 * path used for real chat, rather than a plain "Name: message" system
	 * broadcast. The message is unsigned (it did not come from the
	 * player's own client), so it will show as ordinary chat but will not
	 * carry the target player's chat signature.
	 */
	private static int runAsChatMessage(CommandSourceStack issuer, ServerPlayer target, MinecraftServer server, String raw) {
		PlayerChatMessage chatMessage = PlayerChatMessage.system(raw);
		ChatType.Bound boundChatType = ChatType.bind(ChatType.CHAT, target);

		server.getPlayerList().broadcastChatMessage(chatMessage, target, boundChatType);

		issuer.sendSuccess(() -> Component.literal(
				"Sent chat message as " + target.getGameProfile().getName() + "."), true);

		return 1;
	}
}
