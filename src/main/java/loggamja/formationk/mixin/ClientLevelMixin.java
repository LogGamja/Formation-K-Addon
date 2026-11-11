package loggamja.formationk.mixin;

import loggamja.formationk.option.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// 탑승자가 틱 목록에 없어도 탈것이 틱 중이면 탑승자도 항상 틱한다. (카트 모델 같은 디스플레이 엔티티 탑승자용)
// ClientLevel#tickPassenger 의 EntityTickList#contains 호출을 항상 true 로 바꾼다. (26.3 기준 시그니처)
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Redirect(
        method = "tickPassenger",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/level/entity/EntityTickList;contains(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean formationk$passengersAlwaysTick(EntityTickList list, Entity passenger) {
        // 옵션이 꺼져 있으면 원래 동작(틱 목록 검사)을 그대로 따른다
        return Config.INSTANCE.teleportBugFix || list.contains(passenger);
    }
}
