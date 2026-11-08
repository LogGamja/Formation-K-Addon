
package loggamja.formationk.mixin;

import loggamja.formationk.Main;
import loggamja.formationk.option.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;

import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.player.Input;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Unique private Input formationk$lastPlayerInput = Input.EMPTY;
    @Unique float formationk$lastYaw;

    @Inject(method = "extract", at = @At(value = "HEAD"))
    private void formationk$extractHead(net.minecraft.client.DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Main.onFrameRender(deltaTracker.getGameTimeDeltaPartialTick(false));
    }
    @Unique private long formationk$lastPacketSendNanos = 0L;
    @Unique private static final long formationk$MIN_PACKET_INTERVAL_NANOS = 1_000_000_000L / 300;

    @Inject(method = "render", at = @At(value = "TAIL"))
    private void formationk$renderTail(CallbackInfo ci) {
        if (!Main.isRidingKart || !Main.isPlayingInGame() || !Config.INSTANCE.PacketAcceleration) return;

        // 300프레임 리미트
        long now = System.nanoTime();
        if (now - formationk$lastPacketSendNanos <= formationk$MIN_PACKET_INTERVAL_NANOS) return;
        formationk$lastPacketSendNanos = now;

        LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);

        if (player == Main.getRidingPlayer()) {
            Input input = formationk$getPlayerInput(player);

            if (!this.formationk$lastPlayerInput.equals(input)) {
                player.connection.send(new ServerboundPlayerInputPacket(input));
                this.formationk$lastPlayerInput = input;
            }

            if (this.formationk$lastYaw != player.getYRot()) {
                player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
                this.formationk$lastYaw = player.getYRot();
            }

            Entity vehicle = player.getRootVehicle();
            if (vehicle != player && vehicle.isLocalInstanceAuthoritative()) {
                player.connection.send(ServerboundMoveVehiclePacket.fromEntity(vehicle));
            }
        }
    }
    @Unique
    private @NotNull Input formationk$getPlayerInput(LocalPlayer player) {
        Options options = Minecraft.getInstance().options;
        Input mcInput = player.input.keyPresses;

        return new Input(
                options.keyUp.isDown(),
                options.keyDown.isDown(),
                options.keyLeft.isDown(),
                options.keyRight.isDown(),
                mcInput.jump(),
                mcInput.shift(),
                mcInput.sprint()
        );
    }
}
