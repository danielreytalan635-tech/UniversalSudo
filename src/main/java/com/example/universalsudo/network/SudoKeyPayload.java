package com.example.universalsudo.network;

import com.example.universalsudo.UniversalSudo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SudoKeyPayload(int keyCode) implements CustomPacketPayload {
	public static final Type<SudoKeyPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(UniversalSudo.MOD_ID, "press_key"));

	public static final StreamCodec<ByteBuf, SudoKeyPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, SudoKeyPayload::keyCode, SudoKeyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
