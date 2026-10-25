package dev.combatdata.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.combatdata.client.QuickSettingsScreen;

/** Mod Menu entry point: the "Configure" button opens the quick settings. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return QuickSettingsScreen::new;
    }
}
