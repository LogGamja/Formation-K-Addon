package loggamja.formationk.mixin.server;

import loggamja.formationk.option.Config;
import loggamja.formationk.server.AutoSaveDelayState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@Unique private static final TagKey<EntityType<?>> KART_ENTITY_TYPE_TAG =
		TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("kartmobil", "kartmobil"));
	@Unique private static final TagKey<EntityType<?>> SADDLE_ENTITY_TYPE_TAG =
		TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("kartmobil", "kartsaddle"));

	@Inject(method = "autoSave", at = @At("HEAD"), cancellable = true)
	private void formationk$onRunAutosave(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;

		if (formationk$shouldSkipDelay(server)) {
			AutoSaveDelayState.reset();
			return;
		}

		if (AutoSaveDelayState.tryDelayAutosave(() -> formationk$isRaceActive(server), server.getTickCount(),
			server.tickRateManager().tickrate())) {
			ci.cancel();
		}
	}

	@Unique
	private static boolean formationk$shouldSkipDelay(MinecraftServer server) {
		return !Config.INSTANCE.autoSaveDelayEnabled
			|| server.getPlayerList().getPlayerCount() == 0
			|| !BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(KART_ENTITY_TYPE_TAG).iterator().hasNext()
			|| !BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(SADDLE_ENTITY_TYPE_TAG).iterator().hasNext();
	}

	@Unique
	private static boolean formationk$isRaceActive(MinecraftServer server) {
		Objective sidebar = server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null || !"timerdisplay".equals(sidebar.getName())) {
			return false;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (formationk$isRidingKart(player)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private static boolean formationk$isRidingKart(ServerPlayer player) {
		Entity saddle = player.getVehicle();
		if (saddle == null || !(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(saddle.getType()).is(SADDLE_ENTITY_TYPE_TAG) && saddle.entityTags().contains("kartsaddle"))) {
			return false;
		}
        // 태그가 사라지는 루프 구간에서도 탑승으로 처리하기 위해 type만 봄.
		return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(player.getRootVehicle().getType()).is(KART_ENTITY_TYPE_TAG);
	}
}
