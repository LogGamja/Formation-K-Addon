
package loggamja.formationk.mixin;

import loggamja.formationk.interfaces.DisplayEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

// 부모 클래스 믹스인으로 모든 디스플레이 타입 커버
@Mixin(DisplayEntityRenderState.class)
public class DisplayEntityRenderStateMixin implements DisplayEntityRenderStateAccessor {
    @Unique
    private UUID formationk$uuid;

    @Unique
    private double formationk$pivotY;

    @Override
    public UUID formationk$getUuid() { return formationk$uuid; }

    @Override
    public void formationk$setUuid(UUID uuid) { this.formationk$uuid = uuid; }

    @Override
    public double formationk$getPivotY() { return formationk$pivotY; }

    @Override
    public void formationk$setPivotY(double pivotY) { this.formationk$pivotY = pivotY; }
}