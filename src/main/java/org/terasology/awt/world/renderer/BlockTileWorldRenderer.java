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

import javax.vecmath.Point2i;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.engine.subsystem.awt.assets.AwtTexture;
import org.terasology.engine.subsystem.awt.cities.Sector;
import org.terasology.engine.subsystem.awt.cities.Sectors;
import org.terasology.engine.subsystem.awt.cities.SwingRasterizer;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.engine.subsystem.awt.renderer.AbstractWorldRenderer;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.math.Rect2i;
import org.terasology.math.TeraMath;
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
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.BlockUri;
import org.terasology.world.block.loader.WorldAtlas;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.ChunkProvider;
import org.terasology.world.chunks.internal.ChunkImpl;

import com.google.common.collect.Maps;

public class BlockTileWorldRenderer extends AbstractWorldRenderer {
    private static final Logger logger = LoggerFactory.getLogger(BlockTileWorldRenderer.class);

    private DisplayAxisType displayAxisType = DisplayAxisType.XZ_AXIS;

    private Texture textureAtlas;

    private int viewingAxisOffset;

    private Vector3i cameraOffset = new Vector3i();

    // TODO: better to return source rect + image?
    private Map<BufferedTileCacheKey, TextureRegion> cachedTiles = Maps.newHashMap();

    // Pixels per block
    private float zoomLevel = 16;

    int depthsOfTransparency = 8;
    private float[] darken;

    public BlockTileWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);

        textureAtlas = Assets.getTexture("engine:terrain");

        ComponentSystemManager componentSystemManager = CoreRegistry.get(ComponentSystemManager.class);
        componentSystemManager.register(new WorldControlSystem(this), "awt:WorldControlSystem");

        float[] hsbvals = new float[3];
        java.awt.Color.RGBtoHSB(255, 255, 255, hsbvals);

        darken = new float[depthsOfTransparency];
        darken[0] = 1f;

        for (int i = 1; i < depthsOfTransparency; i++) {
            darken[i] = (depthsOfTransparency - i) / ((float)depthsOfTransparency);
        }
    }

    public void renderWorld(Camera camera) {
        if (zoomLevel > 1) {
            renderBlockTileWorld(camera);
        } else {
            renderCityWorld(camera);
        }
    }

    private void renderCityWorld(Camera camera) {
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        drawGraphics.setColor(java.awt.Color.LIGHT_GRAY);
        int width = displayDevice.mainFrame.getWidth();
        int height = displayDevice.mainFrame.getHeight();
        drawGraphics.fillRect(0, 0, width, height);

        int scale = (int) (1f / zoomLevel);

        final Vector2i cameraPos = new Vector2i(-350, 450);

        int camOffX = (int) Math.floor(cameraPos.x / (double) Sector.SIZE);
        int camOffZ = (int) Math.floor(cameraPos.y / (double) Sector.SIZE);

        int numX = width / (Sector.SIZE * scale) + 1;
        int numZ = height / (Sector.SIZE * scale) + 1;

        String seed = "a";
        SwingRasterizer rasterizer = new SwingRasterizer(seed);

        for (int z = -1; z < numZ; z++) {
            for (int x = -1; x < numX; x++) {
                Point2i coord = new Point2i(x - camOffX, z - camOffZ);
                Sector sector = Sectors.getSector(coord);
                drawGraphics.setClip(null); // mlk added
                drawGraphics.setClip((x - camOffX) * Sector.SIZE, (z - camOffZ) * Sector.SIZE, Sector.SIZE, Sector.SIZE);
                rasterizer.drawAccurately(drawGraphics, sector);
            }
        }
    }

    public void renderBlockTileWorld(Camera camera) {
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        int width = displayDevice.mainFrame.getWidth();
        int height = displayDevice.mainFrame.getHeight();

        drawGraphics.setColor(java.awt.Color.BLACK);
        drawGraphics.fillRect(0, 0, width, height);

        int blockTileWidth = (int) zoomLevel;
        int blockTileHeight = (int) zoomLevel;

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

        WorldProvider worldProvider = CoreRegistry.get(WorldProvider.class);

        Vector3i offsetPosition;
        int offset = viewingAxisOffset;
        switch (displayAxisType) {
            case XZ_AXIS:
                offsetPosition = new Vector3i(0, offset, 0);
                break;
            case YZ_AXIS:
                offsetPosition = new Vector3i(offset, 0, 0);
                break;
            case XY_AXIS:
                offsetPosition = new Vector3i(0, 0, offset);
                break;
            default:
                throw new RuntimeException("illegal displayAxisType " + displayAxisType);
        }

        Vector3i behindLocationChange;
        switch (displayAxisType) {
            case XZ_AXIS:
                behindLocationChange = new Vector3i(0, -1, 0);
                break;
            case YZ_AXIS:
                behindLocationChange = new Vector3i(1, 0, 0);
                break;
            case XY_AXIS:
                behindLocationChange = new Vector3i(0, 0, 1);
                break;
            default:
                throw new RuntimeException("illegal displayAxisType " + displayAxisType);
        }

        // TODO: If we base it on viewpoint, this probably needs to go inside the loop
        BlockPart blockPart;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                blockPart = BlockPart.TOP;
                break;
            case YZ_AXIS:
                blockPart = BlockPart.LEFT; // todo: front/left/right/back needs to be picked base on viewpoint
                break;
            case XY_AXIS:
                blockPart = BlockPart.FRONT; // todo: front/left/right/back needs to be picked base on viewpoint
                break;
            default:
                throw new RuntimeException("displayAxisType containts invalid value");
        }

        blockPosition.add(offsetPosition);

        for (int i = 0; i < blocksWide; i++) {
            for (int j = 0; j < blocksHigh; j++) {

                Vector2i relativeCellLocation = new Vector2i((j - mapCenterY), (i - mapCenterX));

                Vector3i relativeLocation;
                switch (displayAxisType) {
                    case XZ_AXIS: // top down view
                        relativeLocation = new Vector3i(-relativeCellLocation.x, 0, relativeCellLocation.y);
                        break;
                    case YZ_AXIS:
                        relativeLocation = new Vector3i(0, -relativeCellLocation.x, relativeCellLocation.y);
                        break;
                    case XY_AXIS:
                        relativeLocation = new Vector3i(-relativeCellLocation.y, -relativeCellLocation.x, 0);
                        break;
                    default:
                        throw new RuntimeException("displayAxisType containts invalid value");
                }

                relativeLocation.add(blockPosition);
                Block block = getBlockAtWorldPosition(worldProvider, relativeLocation);
                if (null != block) {

                    int alphaChangeCounter = 0;
                    float alpha = 1f;
                    while (BlockManager.getAir().equals(block) && (alphaChangeCounter < (depthsOfTransparency-1))) {
                        alphaChangeCounter++;
                        alpha = darken[alphaChangeCounter];
                        relativeLocation.add(behindLocationChange);
                        block = getBlockAtWorldPosition(worldProvider, relativeLocation);
                    }
                    if (BlockManager.getAir().equals(block)) {
                        // let it remain black
                        continue;
                    }

                    BlockUri blockUri = block.getURI();
                    BlockAppearance primaryAppearance = block.getPrimaryAppearance();

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

                    BufferedImage bufferedImage = awtTexture.getBufferedImage(texture.getWidth(), texture.getHeight(), alpha, Color.WHITE);

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

    private Block getBlockAtWorldPosition(WorldProvider worldProvider, Vector3i worldPosition) {
        int x = worldPosition.x;
        int y = worldPosition.y;
        int z = worldPosition.z;

        if (y >= ChunkConstants.SIZE_Y || y < 0) {
            // Happens if you are moving around above the world
            return null;
        }

        Vector3i chunkPos = TeraMath.calcChunkPos(x, y, z);

        ChunkProvider chunkProvider = getChunkProvider();
        ChunkImpl chunk = chunkProvider.getChunk(chunkPos);
        if (chunk != null) {
            Vector3i blockPos = TeraMath.calcBlockPos(x, y, z);
            return chunk.getBlock(blockPos);
        }
        return null;
    }

    public void increaseViewingAxisOffset() {
        viewingAxisOffset += 1;
        if (viewingAxisOffset > (ChunkConstants.SIZE_Y - 1)) {
            viewingAxisOffset = (ChunkConstants.SIZE_Y - 1);
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

    public void zoomIn() {
        zoomLevel = zoomLevel * 2f;
    }

    public void zoomOut() {
        zoomLevel = zoomLevel / 2f;
    }

}
