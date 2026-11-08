package loggamja.formationk.mixin;

import loggamja.formationk.api.API;
import loggamja.formationk.option.SettingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.PauseScreen;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void formationk$addCustomButton(CallbackInfo ci) {
        if (API.isMenuButtonHidden()) return;

        this.addRenderableWidget(
                Button.builder(Component.translatable("formationk.setting.menu_button"), button -> {
                            assert this.minecraft != null;
                            this.minecraft.gui.setScreen(new SettingScreen(this));
                        })
                        .pos(10, 35)
                        .size(100, 20)
                        .build()
        );
    }
}
