package loggamja.formationk.option;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public class CategoryScreen extends Screen {
    private final Screen parent;
    private final OptionTable.Category category;
    private int startY;

    public CategoryScreen(Screen parent, OptionTable.Category category) {
        super(Component.translatable(category.labelKey()));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 169;
        int buttonHeight = 20;
        int spacing = 25;
        int startX = this.width / 2 - buttonWidth / 2;

        var toggles = OptionTable.togglesIn(category);
        int buttonsPerRow = 2;
        int gap = 8;
        int centerX = this.width / 2;

        int toggleRowCount = (toggles.length + buttonsPerRow - 1) / buttonsPerRow;
        var sliders = OptionTable.slidersIn(category);
        int sliderRowCount = (sliders.length + buttonsPerRow - 1) / buttonsPerRow;
        int totalRows = toggleRowCount + sliderRowCount + 2;
        this.startY = this.height / 2 - (totalRows * spacing) / 2;
        int startY = this.startY;

        for (int i = 0; i < toggles.length; i++) {
            var def = toggles[i];

            int row = i / buttonsPerRow;
            int col = i % buttonsPerRow;

            int buttonY = startY + row * spacing;
            int leftX  = centerX - (gap / 2) - buttonWidth;
            int rightX = centerX + (gap / 2);

            int buttonX = (col == 0) ? leftX : rightX;

            int current = def.getter().getAsInt();

            Button toggleButton = Button.builder(
                            Component.translatable(def.labelKeys()[current]),
                            button -> {
                                int next = (def.getter().getAsInt() + 1) % def.stateCount();
                                def.setter().accept(next);
                                button.setMessage(Component.translatable(def.labelKeys()[next]));
                                Config.INSTANCE.save();
                            })
                    .pos(buttonX, buttonY)
                    .size(buttonWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable(def.tooltipKey())))
                    .build();

            this.addRenderableWidget(toggleButton);
        }

        for (int i = 0; i < sliders.length; i++) {
            var def = sliders[i];
            double min = def.min();
            double max = def.max();
            double value = def.getter().getAsDouble();

            double normalized = (value - min) / (max - min);
            String labelText = Component.translatable(def.labelKey()).getString();

            int row = i / buttonsPerRow;
            int col = i % buttonsPerRow;
            int sliderY = startY + (toggleRowCount + row) * spacing;
            int leftX  = centerX - (gap / 2) - buttonWidth;
            int rightX = centerX + (gap / 2);
            int sliderX = (col == 0) ? leftX : rightX;

            AbstractSliderButton slider = new AbstractSliderButton(sliderX, sliderY,
                    buttonWidth, buttonHeight, Component.literal(labelText + ": " + (int) value), normalized) {
                @Override
                protected void updateMessage() {
                    double actual = min + this.value * (max - min);
                    this.setMessage(Component.literal(labelText + ": " + (int) actual));
                }

                @Override
                protected void applyValue() {
                    double actual = min + this.value * (max - min);
                    def.setter().accept((float) actual);
                }

                @Override
                public void onRelease(MouseButtonEvent event) {
                    super.onRelease(event);
                    Config.INSTANCE.save();
                }
            };
            slider.setTooltip(Tooltip.create(Component.translatable(def.tooltipKey())));
            this.addRenderableWidget(slider);
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("formationk.setting.ok"), button -> this.onClose())
                .pos(startX, startY + (toggleRowCount + sliderRowCount + 1) * spacing)
                .size(buttonWidth, buttonHeight)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(this.font, Component.translatable(category.labelKey()).getString(), this.width / 2, this.startY / 2, 0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.gui.setScreen(parent);
        Config.INSTANCE.save();
    }
}
