package loggamja.formationk.mixin;

import loggamja.formationk.interfaces.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// 렌더 상태 추출과 그리기가 분리되어 있어서 엔티티별 값은 렌더러가 아니라 상태 객체에 담아야 한다
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements LivingEntityRenderStateAccessor {
    @Unique
    private float formationk$roll;

    @Unique
    private double formationk$pivotOffset;

    @Override
    public float formationk$getRoll() { return formationk$roll; }

    @Override
    public void formationk$setRoll(float roll) { this.formationk$roll = roll; }

    @Override
    public double formationk$getPivotOffset() { return formationk$pivotOffset; }

    @Override
    public void formationk$setPivotOffset(double pivotOffset) { this.formationk$pivotOffset = pivotOffset; }
}
