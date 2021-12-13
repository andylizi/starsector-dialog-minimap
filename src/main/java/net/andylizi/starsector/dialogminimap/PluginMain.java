package net.andylizi.starsector.dialogminimap;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public final class PluginMain extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().addTransientListener(new DialogListener());
    }
}
