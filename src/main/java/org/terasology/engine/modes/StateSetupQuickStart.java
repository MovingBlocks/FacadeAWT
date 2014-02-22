
package org.terasology.engine.modes;

import org.terasology.config.Config;
import org.terasology.engine.GameEngine;
import org.terasology.engine.SimpleUri;
import org.terasology.engine.TerasologyConstants;
import org.terasology.engine.module.Module;
import org.terasology.engine.module.ModuleManager;
import org.terasology.game.GameManifest;
import org.terasology.network.NetworkMode;
import org.terasology.registry.CoreRegistry;
import org.terasology.world.internal.WorldInfo;
import org.terasology.world.time.WorldTime;

public class StateSetupQuickStart extends StateMainMenu {

    @Override
    public void init(GameEngine engine) {
        super.init(engine);
        
        GameManifest gameManifest = new GameManifest();

        gameManifest.setTitle("quick-start");
        gameManifest.setSeed("quick-start");

        Config config = CoreRegistry.get(Config.class);
        ModuleManager moduleManager = CoreRegistry.get(ModuleManager.class);
        for (String moduleName : config.getDefaultModSelection().listModules()) {
            Module module = moduleManager.getLatestModuleVersion(moduleName);
            if (module != null) {
                gameManifest.addModule(module.getId(), module.getVersion());
            }
        }

        SimpleUri worldGeneratorUri = config.getWorldGeneration().getDefaultGenerator();

        WorldInfo worldInfo = new WorldInfo(TerasologyConstants.MAIN_WORLD, gameManifest.getSeed(),
                (long) (WorldTime.DAY_LENGTH * 0.025f), worldGeneratorUri);
        gameManifest.addWorld(worldInfo);
        engine.changeState(new StateLoading(gameManifest, NetworkMode.SERVER));
    }
}
