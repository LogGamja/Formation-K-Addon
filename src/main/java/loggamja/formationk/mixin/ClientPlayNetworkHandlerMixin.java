package loggamja.formationk.mixin;

import loggamja.formationk.Camera;
import loggamja.formationk.Main;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Unique
    private long formationk$lastSpeedSuccessTime = Long.MIN_VALUE;

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void formationk$onHandleGameMessage(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (!Main.isRidingKart || !Main.isPlayingInGame()) return;

        Component message = packet.text();
        var speed = formationk$extractSpeed(message.getString());
        boolean matched = !Float.isNaN(speed);

        if (matched && Main.kartEngine == 3) {
            speed *= 2.59065f;
        }

        var world = Minecraft.getInstance().level;
        long worldTime = (world != null) ? world.getGameTime() : formationk$lastSpeedSuccessTime;

        if (matched) {
            Camera.actionbarSpeed = speed;
            Camera.timeAfterLastActionbar = 0;
            formationk$lastSpeedSuccessTime = worldTime;
        } else if (worldTime != formationk$lastSpeedSuccessTime) {
            Camera.timeAfterLastActionbar = 20;
        }
    }
    @Unique
    private static final Pattern formationk$SPEED_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)km/h");

    @Unique
    float formationk$extractSpeed(String text) {
        if (!text.contains("km/h")) return Float.NaN; // 정규식 엔진을 돌리기 전 저렴한 1차 필터링
        Matcher m = formationk$SPEED_PATTERN.matcher(text);
        if (m.find()) {
            return Math.max(Float.parseFloat(m.group(1)), 0);
        }
        return Float.NaN;
    }
}
