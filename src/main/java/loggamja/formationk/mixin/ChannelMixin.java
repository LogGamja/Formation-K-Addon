package loggamja.formationk.mixin;

import com.mojang.blaze3d.audio.Channel;
import loggamja.formationk.Main;
import loggamja.formationk.helper.SoundQuantizer;
import loggamja.formationk.option.Config;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow @Final private AtomicBoolean initialized;

    // 지연되는 동안 stop()/destroy() 가 불렸으면 재생하지 않는다 (소리 스레드에서만 접근한다)
    @Unique private boolean formationk$stopped;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void formationk$playOnGrid(CallbackInfo ci) {
        if (!Config.INSTANCE.smoothEngineSound || !Main.isCameraPlayerRidingKart) return;
        if (SoundQuantizer.isBypassing()) return;

        if (SoundQuantizer.schedule(this::formationk$playDelayed)) {
            ci.cancel();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void formationk$markStopped(CallbackInfo ci) {
        formationk$stopped = true;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void formationk$markDestroyed(CallbackInfo ci) {
        formationk$stopped = true;
    }

    @Unique
    private void formationk$playDelayed() {
        if (formationk$stopped || !initialized.get()) return;

        ((Channel) (Object) this).play();
    }
}
