/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.engine.subsystem.awt;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.AssetFactory;
import org.terasology.assets.AssetType;
import org.terasology.assets.management.AssetManager;
import org.terasology.assets.module.ModuleAwareAssetTypeManager;
import org.terasology.config.Config;
import org.terasology.context.Context;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.TerasologyConstants;
import org.terasology.engine.modes.GameState;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.engine.subsystem.EngineSubsystem;
import org.terasology.engine.subsystem.RenderingSubsystemFactory;
import org.terasology.engine.subsystem.awt.assets.AwtFont;
import org.terasology.engine.subsystem.awt.assets.AwtMaterial;
import org.terasology.engine.subsystem.awt.assets.AwtTexture;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.engine.subsystem.awt.devices.AwtKeyboardDevice;
import org.terasology.engine.subsystem.awt.devices.AwtMouseDevice;
import org.terasology.engine.subsystem.awt.renderer.AwtCanvasRenderer;
import org.terasology.engine.subsystem.awt.renderer.AwtRenderingSubsystemFactory;
import org.terasology.engine.subsystem.config.BindsManager;
import org.terasology.engine.subsystem.headless.assets.HeadlessMaterial;
import org.terasology.engine.subsystem.headless.assets.HeadlessMesh;
import org.terasology.engine.subsystem.headless.assets.HeadlessShader;
import org.terasology.engine.subsystem.headless.assets.HeadlessSkeletalMesh;
import org.terasology.engine.subsystem.headless.assets.HeadlessTexture;
import org.terasology.engine.subsystem.headless.renderer.ShaderManagerHeadless;
import org.terasology.input.InputSystem;
import org.terasology.logic.players.DebugControlSystem;
import org.terasology.logic.players.MenuControlSystem;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.ShaderManager;
import org.terasology.rendering.assets.animation.MeshAnimation;
import org.terasology.rendering.assets.animation.MeshAnimationData;
import org.terasology.rendering.assets.animation.MeshAnimationImpl;
import org.terasology.rendering.assets.atlas.Atlas;
import org.terasology.rendering.assets.atlas.AtlasData;
import org.terasology.rendering.assets.font.Font;
import org.terasology.rendering.assets.font.FontData;
import org.terasology.rendering.assets.font.FontImpl;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.material.MaterialData;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.assets.mesh.MeshData;
import org.terasology.rendering.assets.shader.Shader;
import org.terasology.rendering.assets.shader.ShaderData;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMesh;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMeshData;
import org.terasology.rendering.assets.texture.PNGTextureFormat;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureData;
import org.terasology.rendering.assets.texture.subtexture.Subtexture;
import org.terasology.rendering.assets.texture.subtexture.SubtextureData;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.internal.NUIManagerInternal;

import com.google.common.collect.ImmutableList;

public class AwtGraphics implements EngineSubsystem {

    private static final Logger log = LoggerFactory.getLogger(AwtGraphics.class);
    
    private JFrame mainFrame;
    private AwtMouseDevice awtMouseDevice;

    @Override
    public void preInitialise(Context rootContext) {
    }

    @Override
    public void postInitialise(Context rootContext) {

        // TODO: if needed, maybe handled with ModuleAwareAssetTypeManager.registerCoreAssetType()?
//        AssetManager assetManager = CoreRegistry.get(AssetManager.class);
//        ClasspathSource sourceFacade = new ClasspathSource(
//                TerasologyConstants.ENGINE_MODULE,
//                getClass().getProtectionDomain().getCodeSource(), 
//                TerasologyConstants.ASSETS_SUBDIRECTORY, 
//                TerasologyConstants.OVERRIDES_SUBDIRECTORY,
//                TerasologyConstants.DELTAS_SUBDIRECTORY);
//
//        assetManager.addAssetSource(sourceFacade);

        CoreRegistry.put(RenderingSubsystemFactory.class, new AwtRenderingSubsystemFactory());

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        GraphicsConfiguration gc = device.getDefaultConfiguration();
        mainFrame = new JFrame(gc);

        mainFrame.setUndecorated(false);
        mainFrame.setIgnoreRepaint(true);

        try
        {
            String root = "org/terasology/icons/";
            ClassLoader classLoader = getClass().getClassLoader();

            BufferedImage icon16 = ImageIO.read(classLoader.getResourceAsStream(root + "bluegoo_16.png"));
            BufferedImage icon32 = ImageIO.read(classLoader.getResourceAsStream(root + "bluegoo_32.png"));
            BufferedImage icon64 = ImageIO.read(classLoader.getResourceAsStream(root + "bluegoo_64.png"));
            BufferedImage icon128 = ImageIO.read(classLoader.getResourceAsStream(root + "bluegoo_128.png"));
            mainFrame.setIconImages(ImmutableList.of(icon16, icon32, icon64, icon128));
        }
        catch (Exception e) {
            log.warn("Error loading icon", e);
        }
        
        mainFrame.setTitle("Terasology" + " | " + "Pre Alpha");

        AwtDisplayDevice awtDisplay = new AwtDisplayDevice(mainFrame);

        CoreRegistry.put(DisplayDevice.class, awtDisplay);

        // TODO: read from config?
        awtDisplay.setFullscreen(false);

        // Frame has to be visible by the time we set a buffer strategy, which seems really stupid
        mainFrame.setVisible(true);
        mainFrame.createBufferStrategy(2);

        AwtCanvasRenderer canvasRenderer = new AwtCanvasRenderer(mainFrame, awtDisplay);

        NUIManagerInternal nuiManager = new NUIManagerInternal(canvasRenderer, rootContext);
        CoreRegistry.put(NUIManager.class, nuiManager);

        //        CoreRegistry.putPermanently(DefaultRenderingProcess.class, new AwtRenderingProcess());

        // Input
        InputSystem inputSystem = new InputSystem();
        CoreRegistry.put(InputSystem.class, inputSystem);

        awtMouseDevice = new AwtMouseDevice(mainFrame);
        inputSystem.setMouseDevice(awtMouseDevice);

        AwtKeyboardDevice awtKeyboardDevice = new AwtKeyboardDevice(mainFrame);
        inputSystem.setKeyboardDevice(awtKeyboardDevice);

        updateInputConfig(rootContext);
    }

    private void updateInputConfig(Context context) {
        BindsManager bindsManager = context.get(BindsManager.class);
        bindsManager.updateConfigWithDefaultBinds();
        bindsManager.saveBindsConfig();
    }

    @Override
    public void preUpdate(GameState currentState, float delta) {
    }

    @Override
    public void postUpdate(GameState currentState, float delta) {
        awtMouseDevice.update(delta);
        currentState.render();
        currentState.handleInput(delta);
    }

    @Override
    public void shutdown() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        device.setFullScreenWindow(null);

        mainFrame.dispose();
    }

    @Override
    public void registerCoreAssetTypes(ModuleAwareAssetTypeManager assetTypeManager) {
        assetTypeManager.registerCoreAssetType(Font.class, (AssetFactory<Font, FontData>) AwtFont::new, "fonts");
        assetTypeManager.registerCoreAssetType(Texture.class, (AssetFactory<Texture, TextureData>) AwtTexture::new, "textures", "fonts");
        assetTypeManager.registerCoreFormat(Texture.class, new PNGTextureFormat(Texture.FilterMode.NEAREST, path -> path.getName(2).toString().equals("textures")));
        assetTypeManager.registerCoreFormat(Texture.class, new PNGTextureFormat(Texture.FilterMode.LINEAR, path -> path.getName(2).toString().equals("fonts")));
        assetTypeManager.registerCoreAssetType(Shader.class, (AssetFactory<Shader, ShaderData>) HeadlessShader::new, "shaders");
        assetTypeManager.registerCoreAssetType(Material.class, (AssetFactory<Material, MaterialData>) AwtMaterial::new, "materials");
        assetTypeManager.registerCoreAssetType(Mesh.class, (AssetFactory<Mesh, MeshData>) HeadlessMesh::new, "mesh");
        assetTypeManager.registerCoreAssetType(SkeletalMesh.class, (AssetFactory<SkeletalMesh, SkeletalMeshData>) HeadlessSkeletalMesh::new, "skeletalMesh");
        assetTypeManager.registerCoreAssetType(MeshAnimation.class, (AssetFactory<MeshAnimation, MeshAnimationData>) MeshAnimationImpl::new, "animations");
        assetTypeManager.registerCoreAssetType(Atlas.class, (AssetFactory<Atlas, AtlasData>) Atlas::new, "atlas");
        assetTypeManager.registerCoreAssetType(Subtexture.class, (AssetFactory<Subtexture, SubtextureData>) Subtexture::new);
        
        // TODO: unclear if something along these lines is still needed
//        assetManager.addResolver(AssetType.SUBTEXTURE, new SubtextureFromAtlasResolver());
//        assetManager.addResolver(AssetType.TEXTURE, new ColorTextureAssetResolver());
    }

    @Override
    public void registerSystems(ComponentSystemManager componentSystemManager) {
        componentSystemManager.register(new MenuControlSystem(), "engine:MenuControlSystem");
        componentSystemManager.register(new DebugControlSystem(), "engine:DebugControlSystem");
    }

	@Override
	public String getName() {
        return "Graphics";
	}
}
