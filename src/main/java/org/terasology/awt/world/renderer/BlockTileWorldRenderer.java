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
import java.util.Objects;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.engine.subsystem.awt.assets.AwtTexture;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.engine.subsystem.awt.renderer.AbstractWorldRenderer;
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
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.ChunkProvider;

import com.google.common.collect.Maps;

public class BlockTileWorldRenderer extends AbstractWorldRenderer {
    private static final Logger logger = LoggerFactory.getLogger(BlockTileWorldRenderer.class);

    private static final int MINIMAP_TILE_SIZE = 16;

    private DisplayAxisType displayAxisType = DisplayAxisType.XZ_AXIS;

    private Texture textureAtlas;

    private int viewingAxisOffset;

    private Vector3i cameraOffset = new Vector3i();

    // TODO: better to return source rect + image?
    private Map<BufferedTileCacheKey, TextureRegion> cachedTiles = Maps.newHashMap();

    public BlockTileWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);

        textureAtlas = Assets.getTexture("engine:terrain");

        ComponentSystemManager componentSystemManager = CoreRegistry.get(ComponentSystemManager.class);
        componentSystemManager.register(new WorldControlSystem(this), "awt:WorldControlSystem");
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
        blockPosition.add(cameraOffset);
        
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

                Vector3i offsetPosition;
                int offset = viewingAxisOffset;
                switch (displayAxisType) {
                    case XY_AXIS:
                        offsetPosition = new Vector3i(0, 0, offset);
                        break;
                    case XZ_AXIS:
                        offsetPosition = new Vector3i(0, offset, 0);
                        break;
                    case YZ_AXIS:
                        offsetPosition = new Vector3i(offset, 0, 0);
                        break;
                    default:
                        throw new RuntimeException("illegal displayAxisType " + displayAxisType);
                }

                relativeLocation.add(blockPosition);
                relativeLocation.add(offsetPosition);
                Block block = worldProvider.getBlock(relativeLocation);
                if (null != block) {

                    BlockUri blockUri = block.getURI();
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

                    BufferedTileCacheKey key = new BufferedTileCacheKey(blockUri, blockPart);
                    TextureRegion textureRegion = cachedTiles.get(key);
                    if (null == textureRegion) {
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

    public void increaseViewingAxisOffset() {
        viewingAxisOffset += 1;
        if (viewingAxisOffset > (ChunkConstants.SIZE_Y-1)) {
            viewingAxisOffset = (ChunkConstants.SIZE_Y-1);
        }
    }

    public void decreaseViewingAxisOffset() {
        viewingAxisOffset -= 1;
        if (viewingAxisOffset < 0) {
            viewingAxisOffset = 0;
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

    public class BufferedTileCacheKey {
        private BlockUri blockUri;
        private BlockPart blockPart;
        
        public BufferedTileCacheKey(BlockUri blockUri, BlockPart blockPart) {
            super();
            this.blockUri = blockUri;
            this.blockPart = blockPart;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof BufferedTileCacheKey) {
                BufferedTileCacheKey other = (BufferedTileCacheKey) obj;
                return Objects.equals(blockUri, other.blockUri)
                       && Objects.equals(blockPart, other.blockPart);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockUri, blockPart);
        }
    }

    public void changeCameraOffsetBy(int i, int j, int k) {
        cameraOffset = new Vector3i(cameraOffset.getX() + i, cameraOffset.getY() + j, cameraOffset.getZ() + k);
    }

}
