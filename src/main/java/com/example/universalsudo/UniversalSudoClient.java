package com.example.universalsudo;

import com.example.universalsudo.network.SudoKeyPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;

import java.util.HashSet;
import java.util.Set;

public final class UniversalSudoClient implements ClientModInitializer {
	private static final Set<Integer> KEYS_TO_RELEASE = new HashSet<>();

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SudoKeyPayload.TYPE, (payload, context) -> {
			InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(payload.keyCode());

			// Treat the packet as a real one-tick key press. click() is important
			// for keys such as E that use a press/release action instead of a
			// continuously-held input.
			KeyMapping.click(key);
			KeyMapping.set(key, true);
			KEYS_TO_RELEASE.add(payload.keyCode());
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (KEYS_TO_RELEASE.isEmpty()) {
				return;
			}

			for (int keyCode : KEYS_TO_RELEASE) {
				KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(keyCode), false);
			}

			KEYS_TO_RELEASE.clear();
		});
	}
}
