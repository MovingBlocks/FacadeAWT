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

import javax.swing.JFrame;

import org.terasology.asset.AssetFactory;
import org.terasology.asset.AssetManager;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.config.Config;
import org.terasology.engine.ComponentSystemManager;
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
import org.terasology.input.InputSystem;
import org.terasology.logic.manager.GUIManager;
import org.terasology.logic.manager.GUIManagerHeadless;
import org.terasology.logic.players.DebugControlSystem;
import org.terasology.logic.players.MenuControlSystem;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.ShaderManager;
import org.terasology.rendering.ShaderManagerHeadless;
import org.terasology.rendering.assets.animation.MeshAnimation;
import org.terasology.rendering.assets.animation.MeshAnimationData;
import org.terasology.rendering.assets.animation.MeshAnimationImpl;
import org.terasology.rendering.assets.atlas.Atlas;
import org.terasology.rendering.assets.atlas.AtlasData;
import org.terasology.rendering.assets.font.Font;
import org.terasology.rendering.assets.font.FontData;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.material.MaterialData;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.assets.mesh.MeshData;
import org.terasology.rendering.assets.shader.Shader;
import org.terasology.rendering.assets.shader.ShaderData;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMesh;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMeshData;
import org.terasology.rendering.assets.texture.ColorTextureAssetResolver;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureData;
import org.terasology.rendering.assets.texture.subtexture.Subtexture;
import org.terasology.rendering.assets.texture.subtexture.SubtextureData;
import org.terasology.rendering.assets.texture.subtexture.SubtextureFromAtlasResolver;
import org.terasology.rendering.headless.HeadlessMesh;
import org.terasology.rendering.headless.HeadlessShader;
import org.terasology.rendering.headless.HeadlessSkeletalMesh;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.internal.NUIManagerInternal;

public class AwtGraphics implements EngineSubsystem {

    private JFrame mainFrame;
    private AwtMouseDevice awtMouseDevice;

    @Override
    public void preInitialise() {
    }

    @Override
    public void postInitialise(Config config) {
        CoreRegistry.putPermanently(RenderingSubsystemFactory.class, new AwtRenderingSubsystemFactory());

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        GraphicsConfiguration gc = device.getDefaultConfiguration();
        mainFrame = new JFrame(gc);

        mainFrame.setUndecorated(false);
        mainFrame.setIgnoreRepaint(true);

        mainFrame.setTitle("Terasology" + " | " + "Pre Alpha");

        AwtDisplayDevice awtDisplay = new AwtDisplayDevice(mainFrame);

        CoreRegistry.putPermanently(DisplayDevice.class, awtDisplay);

        initHeadless(awtDisplay);

        AssetManager assetManager = CoreRegistry.get(AssetManager.class);

        // TODO: read from config?
        awtDisplay.setFullscreen(false);

        // Frame has to be visible by the time we set a buffer strategy, which seems really stupid
        mainFrame.setVisible(true);
        mainFrame.createBufferStrategy(2);

        AwtCanvasRenderer canvasRenderer = new AwtCanvasRenderer(mainFrame, awtDisplay);

        NUIManagerInternal nuiManager = new NUIManagerInternal(assetManager, canvasRenderer);
        CoreRegistry.putPermanently(NUIManager.class, nuiManager);

        CoreRegistry.putPermanently(GUIManager.class, new GUIManagerHeadless());

        //        CoreRegistry.putPermanently(DefaultRenderingProcess.class, new AwtRenderingProcess());

        // Input
        InputSystem inputSystem = new InputSystem();
        CoreRegistry.putPermanently(InputSystem.class, inputSystem);

        awtMouseDevice = new AwtMouseDevice(mainFrame);
        inputSystem.setMouseDevice(awtMouseDevice);

        AwtKeyboardDevice awtKeyboardDevice = new AwtKeyboardDevice(mainFrame);
        inputSystem.setKeyboardDevice(awtKeyboardDevice);
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
    public void shutdown(Config config) {
    }

    @Override
    public void dispose() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        device.setFullScreenWindow(null);

        mainFrame.dispose();
    }

    private void initHeadless(AwtDisplayDevice awtDisplay) {
        AssetManager assetManager = CoreRegistry.get(AssetManager.class);
        assetManager.setAssetFactory(AssetType.TEXTURE, new AssetFactory<TextureData, Texture>() {
            @Override
            public Texture buildAsset(AssetUri uri, TextureData data) {
                return new AwtTexture(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.FONT, new AssetFactory<FontData, Font>() {
            @Override
            public Font buildAsset(AssetUri uri, FontData data) {
                return new AwtFont(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.SHADER, new AssetFactory<ShaderData, Shader>() {
            @Override
            public Shader buildAsset(AssetUri uri, ShaderData data) {
                return new HeadlessShader(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.MATERIAL, new AssetFactory<MaterialData, Material>() {
            @Override
            public Material buildAsset(AssetUri uri, MaterialData data) {
                return new AwtMaterial(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.MESH, new AssetFactory<MeshData, Mesh>() {
            @Override
            public Mesh buildAsset(AssetUri uri, MeshData data) {
                return new HeadlessMesh(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.SKELETON_MESH, new AssetFactory<SkeletalMeshData, SkeletalMesh>() {
            @Override
            public SkeletalMesh buildAsset(AssetUri uri, SkeletalMeshData data) {
                return new HeadlessSkeletalMesh(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.ANIMATION, new AssetFactory<MeshAnimationData, MeshAnimation>() {
            @Override
            public MeshAnimation buildAsset(AssetUri uri, MeshAnimationData data) {
                return new MeshAnimationImpl(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.ATLAS, new AssetFactory<AtlasData, Atlas>() {
            @Override
            public Atlas buildAsset(AssetUri uri, AtlasData data) {
                return new Atlas(uri, data);
            }
        });
        assetManager.setAssetFactory(AssetType.SUBTEXTURE, new AssetFactory<SubtextureData, Subtexture>() {
            @Override
            public Subtexture buildAsset(AssetUri uri, SubtextureData data) {
                return new Subtexture(uri, data);
            }
        });
        assetManager.addResolver(AssetType.SUBTEXTURE, new SubtextureFromAtlasResolver());
        assetManager.addResolver(AssetType.TEXTURE, new ColorTextureAssetResolver());
        CoreRegistry.putPermanently(ShaderManager.class, new ShaderManagerHeadless());
    }

    @Override
    public void registerSystems(ComponentSystemManager componentSystemManager) {
        componentSystemManager.register(new MenuControlSystem(), "engine:MenuControlSystem");
        componentSystemManager.register(new DebugControlSystem(), "engine:DebugControlSystem");
    }
}
