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
package org.terasology.awt.world.renderer;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Map;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.awt.input.binds.DecreaseOffsetButton;
import org.terasology.awt.input.binds.IncreaseOffsetButton;
import org.terasology.awt.input.binds.ToggleMapAxisButton;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.engine.subsystem.awt.assets.AwtTexture;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.engine.subsystem.awt.renderer.AbstractWorldRenderer;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.ComponentSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.math.Rect2i;
import org.terasology.math.Vector2i;
import org.terasology.math.Vector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.texture.BasicTextureRegion;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureRegion;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.nui.Color;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.BlockUri;
import org.terasology.world.block.loader.WorldAtlas;
import org.terasology.world.chunks.ChunkProvider;

import com.google.common.collect.Maps;

public class BlockTileWorldRenderer extends AbstractWorldRenderer {
    private static final Logger logger = LoggerFactory.getLogger(BlockTileWorldRenderer.class);

    private static final int MINIMAP_TILE_SIZE = 16;

    private DisplayAxisType displayAxisType = DisplayAxisType.XZ_AXIS;

    private Texture textureAtlas;

    private int viewingAxisOffset;

    // TODO: also need to key on BlockPart
    // TODO: better to return source rect + image?
    private Map<BlockUri, TextureRegion> cachedTiles = Maps.newHashMap();

    public BlockTileWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);

        textureAtlas = Assets.getTexture("engine:terrain");

        ComponentSystemManager componentSystemManager = CoreRegistry.get(ComponentSystemManager.class);
        componentSystemManager.register(new WorldControlSystem(), "awt:WorldControlSystem");
    }

    public void renderWorld(Camera camera) {
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        int width = displayDevice.mainFrame.getWidth();
        int height = displayDevice.mainFrame.getHeight();

        int blockTileWidth = MINIMAP_TILE_SIZE;
        int blockTileHeight = MINIMAP_TILE_SIZE;

        int blocksWide = width / blockTileWidth;
        if (blocksWide * blockTileWidth < width) {
            blocksWide++;
        }

        int blocksHigh = height / blockTileHeight;
        if (blocksHigh * blockTileHeight < height) {
            blocksHigh++;
        }

        int mapCenterX = (int) ((blocksWide + 0.5f) / 2f);
        int mapCenterY = (int) ((blocksHigh + 0.5f) / 2f);

        LocalPlayer localPlayer = CoreRegistry.get(LocalPlayer.class);
        Vector3f worldPosition = localPlayer.getPosition();
        Vector3i blockPosition = new Vector3i(Math.round(worldPosition.x), Math.round(worldPosition.y), Math.round(worldPosition.z));

        for (int i = 0; i < blocksWide; i++) {
            for (int j = 0; j < blocksHigh; j++) {

                Vector2i relativeCellLocation = new Vector2i((i - mapCenterX), (j - mapCenterY));

                WorldProvider worldProvider = CoreRegistry.get(WorldProvider.class);

                Vector3i relativeLocation;
                switch (displayAxisType) {
                    case XZ_AXIS: // top down view
                        relativeLocation = new Vector3i(-relativeCellLocation.x, 0, relativeCellLocation.y);
                        break;
                    case XY_AXIS:
                        relativeLocation = new Vector3i(-relativeCellLocation.y, -relativeCellLocation.x, 0);
                        break;
                    case YZ_AXIS:
                        relativeLocation = new Vector3i(0, -relativeCellLocation.x, -relativeCellLocation.y);
                        break;
                    default:
                        throw new RuntimeException("displayAxisType containts invalid value");
                }

                // From top view, see what we're walking on, not what's at knee level
                blockPosition.sub(0, 0, 0);

                int offset = viewingAxisOffset;
                switch (displayAxisType) {
                    case XY_AXIS:
                        blockPosition.add(0, 0, offset);
                        break;
                    case XZ_AXIS:
                        blockPosition.add(0, offset, 0);
                        break;
                    case YZ_AXIS:
                        blockPosition.add(offset, 0, 0);
                        break;
                }

                relativeLocation.add(blockPosition);
                Block block = worldProvider.getBlock(relativeLocation);
                if (null != block) {

                    BlockUri key = block.getURI();
                    TextureRegion textureRegion = cachedTiles.get(key);
                    if (null == textureRegion) {
                        BlockAppearance primaryAppearance = block.getPrimaryAppearance();

                        BlockPart blockPart;
                        switch (displayAxisType) {
                            case XZ_AXIS: // top down view
                                blockPart = BlockPart.TOP;
                                break;
                            case XY_AXIS:
                                blockPart = BlockPart.FRONT; // todo: front/left/right/back needs to be picked base on viewpoint
                                break;
                            case YZ_AXIS:
                                blockPart = BlockPart.LEFT; // todo: front/left/right/back needs to be picked base on viewpoint
                                break;
                            default:
                                throw new RuntimeException("displayAxisType containts invalid value");
                        }

                        WorldAtlas worldAtlas = CoreRegistry.get(WorldAtlas.class);
                        float tileSize = worldAtlas.getRelativeTileSize();

                        Vector2f textureAtlasPos = primaryAppearance.getTextureAtlasPos(blockPart);

                        textureRegion = new BasicTextureRegion(textureAtlas, textureAtlasPos, new Vector2f(tileSize, tileSize));
                        cachedTiles.put(key, textureRegion);
                    }

                    Texture texture = textureRegion.getTexture();
                    AwtTexture awtTexture = (AwtTexture) texture;

                    BufferedImage bufferedImage = awtTexture.getBufferedImage(texture.getWidth(), texture.getHeight(), 1f, Color.WHITE);

                    Rect2i pixelRegion = textureRegion.getPixelRegion();

                    int sx1 = pixelRegion.minX();
                    int sy1 = pixelRegion.minY();
                    int sx2 = pixelRegion.maxX();
                    int sy2 = pixelRegion.maxY();

                    Rect2i destinationArea = Rect2i.createFromMinAndSize(
                            i * blockTileWidth,
                            j * blockTileHeight,
                            blockTileWidth,
                            blockTileHeight);

                    int dx1 = destinationArea.minX();
                    int dy1 = destinationArea.minY();
                    int dx2 = destinationArea.maxX();
                    int dy2 = destinationArea.maxY();

                    ImageObserver observer = null;

                    drawGraphics.drawImage(bufferedImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
                }
            }
        }
    }

    public void toggleAxis() {
        switch (displayAxisType) {
            case XY_AXIS:
                displayAxisType = DisplayAxisType.XZ_AXIS;
                break;
            case XZ_AXIS:
                displayAxisType = DisplayAxisType.YZ_AXIS;
                break;
            case YZ_AXIS:
                displayAxisType = DisplayAxisType.XY_AXIS;
                break;
        }
    }

    public class WorldControlSystem implements ComponentSystem {

        public WorldControlSystem() {
        }

        @Override
        public void initialise() {
        }

        @Override
        public void shutdown() {
        }

        @ReceiveEvent(components = {CharacterComponent.class})
        public void onIncreaseOffsetButton(IncreaseOffsetButton event, EntityRef entity) {
            if (event.isDown()) {
                viewingAxisOffset += 1;

                event.consume();
            }
        }

        @ReceiveEvent(components = {CharacterComponent.class})
        public void onDecreaseOffsetButton(DecreaseOffsetButton event, EntityRef entity) {
            if (event.isDown()) {
                viewingAxisOffset -= 1;

                event.consume();
            }
        }

        @ReceiveEvent(components = {CharacterComponent.class})
        public void onToggleMinimapAxisButton(ToggleMapAxisButton event, EntityRef entity) {
            if (event.isDown()) {
                toggleAxis();

                event.consume();
            }
        }
    }

    public class BufferedTileCacheKey {

    }

}
