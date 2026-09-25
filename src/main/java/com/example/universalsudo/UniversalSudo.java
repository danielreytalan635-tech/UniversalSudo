package com.example.universalsudo;

import com.example.universalsudo.command.SudoCommand;
import com.example.universalsudo.network.SudoKeyPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniversalSudo implements ModInitializer {
	public static final String MOD_ID = "universalsudo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(
				SudoKeyPayload.TYPE,
				SudoKeyPayload.STREAM_CODEC
		);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SudoCommand.register(dispatcher));

		LOGGER.info("Universal Sudo loaded - /sudo <player> <message_or_command> and /sudo <player> press <key> are ready.");
	}
}
