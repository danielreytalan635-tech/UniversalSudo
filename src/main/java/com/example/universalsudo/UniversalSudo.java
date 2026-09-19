package com.example.universalsudo;

import com.example.universalsudo.command.SudoCommand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Universal Sudo
 *
 * Adds a single command:
 *
 *   /sudo <player> <message_or_command>
 *
 * See {@link SudoCommand} for the actual implementation.
 */
public class UniversalSudo implements ModInitializer {
	public static final String MOD_ID = "universalsudo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// CommandRegistrationCallback fires whenever the server (dedicated or
		// integrated) builds its command tree. We register our one command here.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SudoCommand.register(dispatcher));

		LOGGER.info("Universal Sudo loaded - /sudo <player> <message_or_command> is ready.");
	}
}
