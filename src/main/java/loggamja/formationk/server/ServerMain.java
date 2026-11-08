package loggamja.formationk.server;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class ServerMain implements ModInitializer {

	@Override
	public void onInitialize() {
		// 클라이언트 설정도 여기서 로드된다 (main 엔트리포인트는 client보다 먼저 실행됨)
		Config.INSTANCE.load();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (server.isDedicatedServer()) {
				Config.INSTANCE.load();
			}
			AutoSaveDelayState.reset();
		});
	}
}
