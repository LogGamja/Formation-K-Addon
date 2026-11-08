package loggamja.formationk.option;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class SettingScreen extends Screen {
    private final Screen parent;
    private int startY;

    // ESC 메뉴가 아닌 다른 곳(API 등)에서도 열릴 수 있으므로, 닫을 때 돌아갈 화면을 직접 기억한다
    public SettingScreen(Screen parent) {
        super(Component.translatable("formationk.setting.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 169;
        int buttonHeight = 20;
        int spacing = 25;
        int startX = this.width / 2 - buttonWidth / 2;

        OptionTable.clampAllToggles();
        OptionTable.clampAllSliders();

        OptionTable.Category[] categories = OptionTable.Category.values();
        int totalRows = categories.length + 2;
        this.startY = this.height / 2 - (totalRows * spacing) / 2;
        int startY = this.startY;

        for (int i = 0; i < categories.length; i++) {
            var category = categories[i];
            this.addRenderableWidget(
                Button.builder(Component.translatable(category.labelKey()),
                                button -> this.minecraft.gui.setScreen(new CategoryScreen(this, category)))
                        .pos(startX, startY + i * spacing)
                        .size(buttonWidth, buttonHeight)
                        .build()
            );
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("formationk.setting.ok"), button -> this.onClose())
                .pos(startX, startY + (categories.length + 1) * spacing)
                .size(buttonWidth, buttonHeight)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(this.font, Component.translatable("formationk.setting.title").getString(), this.width / 2, this.startY / 2, 0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.gui.setScreen(parent);
        Config.INSTANCE.save();
    }
}
