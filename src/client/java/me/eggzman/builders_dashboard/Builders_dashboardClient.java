package me.eggzman.builders_dashboard;

import me.eggzman.builders_dashboard.input.Keybinds;
import me.eggzman.builders_dashboard.ui.DashboardHud;
import net.fabricmc.api.ClientModInitializer;

public class Builders_dashboardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DashboardHud.init();
		Keybinds.init();
	}
}
