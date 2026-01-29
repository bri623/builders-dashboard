package me.eggzman.builders_dashboard;

import me.eggzman.builders_dashboard.input.Keybinds;
import me.eggzman.builders_dashboard.images.ImageLibrary;
import me.eggzman.builders_dashboard.ui.DashboardHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class Builders_dashboardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DashboardHud.init();
		Keybinds.init();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			ImageLibrary.init();
		});
	}
}
