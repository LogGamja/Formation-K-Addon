package loggamja.formationk.interfaces;

import loggamja.formationk.helper.WheelManager;

import java.util.UUID;

public interface DisplayEntityRenderStateAccessor {
    UUID formationk$getRollKey();
    void formationk$setRollKey(UUID rollKey);

    double formationk$getPivotY();
    void formationk$setPivotY(double pivotY);

    WheelManager.WheelPartInfo formationk$getWheel();
    void formationk$setWheel(WheelManager.WheelPartInfo info);
}
