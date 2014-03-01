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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.math.RoundingMode;
import java.util.Map;

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
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.input.InputSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.logic.location.LocationComponent;
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
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.loader.WorldAtlas;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.ChunkProvider;
import org.terasology.world.chunks.internal.ChunkImpl;
import org.terasology.world.selection.BlockSelectionComponent;

import com.google.common.collect.Maps;
import com.google.common.math.IntMath;

public class BlockTileWorldRenderer extends AbstractWorldRenderer {

    private static final Logger logger = LoggerFactory.getLogger(BlockTileWorldRenderer.class);

    private static final org.terasology.rendering.nui.Color WHITE = org.terasology.rendering.nui.Color.WHITE;

    private DisplayAxisType displayAxisType = DisplayAxisType.XZ_AXIS;

    private Texture textureAtlas;

    private Vector3i cameraOffset = new Vector3i();

    // TODO: better to return source rect + image?
    private Map<Block, Color> cachedColorTop = Maps.newHashMap();
    private Map<Block, Color> cachedColorLeft = Maps.newHashMap();
    private Map<Block, Color> cachedColorFront = Maps.newHashMap();

    private Map<Block, TextureRegion> cachedRegionTop = Maps.newHashMap();
    private Map<Block, TextureRegion> cachedRegionLeft = Maps.newHashMap();
    private Map<Block, TextureRegion> cachedRegionFront = Maps.newHashMap();

    // Pixels per block
    private float zoomLevel = 16;

    private int depthsOfTransparency = 16;
    private float[] darken;

    private EntityManager entityManager;

    public BlockTileWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);

        textureAtlas = Assets.getTexture("engine:terrain");

        ComponentSystemManager componentSystemManager = CoreRegistry.get(ComponentSystemManager.class);
        componentSystemManager.register(new WorldControlSystem(this), "awt:WorldControlSystem");

        float[] hsbvals = new float[3];
        Color.RGBtoHSB(255, 255, 255, hsbvals);

        darken = new float[depthsOfTransparency];
        darken[0] = 1f;

        for (int i = 1; i < depthsOfTransparency; i++) {
            darken[i] = (depthsOfTransparency - i) / ((float) depthsOfTransparency);
        }

        entityManager = CoreRegistry.get(EntityManager.class);
    }

    public void renderWorld(Camera camera) {
        Vector3i centerBlockPosition = getViewBlockLocation();

        if (zoomLevel > 1) {
            renderBlockTileWorld(camera, centerBlockPosition);
        } else {
            renderCityWorld(camera, centerBlockPosition);
        }
    }

    private void renderCityWorld(Camera camera, Vector3i centerBlockPosition) {
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        drawGraphics.setColor(Color.LIGHT_GRAY);
        int width = displayDevice.getWidth();
        int height = displayDevice.getHeight();
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

    int blockTileWidth;
    int blockTileHeight;
    float mapCenterXf;
    float mapCenterYf;
    Vector3f centerBlockPositionf;

    public void renderBlockTileWorld(Camera camera, Vector3i centerBlockPosition) {
        
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        Graphics2D drawGraphics2d = (Graphics2D) drawGraphics;
        int width = displayDevice.getWidth();
        int height = displayDevice.getHeight();

        InputSystem inputSystem = CoreRegistry.get(InputSystem.class);
        Vector2i mousePosition = inputSystem.getMouseDevice().getPosition();

        drawGraphics.setColor(Color.BLACK);
        drawGraphics.fillRect(0, 0, width, height);

        blockTileWidth = (int) zoomLevel;
        blockTileHeight = (int) zoomLevel;

        int blocksWide = IntMath.divide(width, blockTileWidth, RoundingMode.CEILING);
        int blocksHigh = IntMath.divide(height, blockTileHeight, RoundingMode.CEILING);

        mapCenterXf = ((blocksWide + 0.5f) / 2f);
        mapCenterYf = ((blocksHigh + 0.5f) / 2f);

        int mapCenterX = (int) ((blocksWide + 0.5f) / 2f);
        int mapCenterY = (int) ((blocksHigh + 0.5f) / 2f);

        boolean a = true;
        
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

        // TODO: If we base what block side we see on viewpoint, this probably needs to go inside the loop
        BlockPart blockPart;
        Map<Block, BufferedImage> cachedTiles;
        Map<Block, TextureRegion> cachedRegion;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                blockPart = BlockPart.TOP;
                cachedTiles = cachedColorTop;
                cachedRegion = cachedRegionTop;
                break;
            case YZ_AXIS:
                blockPart = BlockPart.LEFT; // todo: front/left/right/back needs to be picked base on viewpoint
                cachedTiles = cachedTilesLeft;
                cachedRegion = cachedRegionLeft;
                break;
            case XY_AXIS:
                blockPart = BlockPart.FRONT; // todo: front/left/right/back needs to be picked base on viewpoint
                cachedTiles = cachedTilesFront;
                cachedRegion = cachedRegionFront;
                break;
            default:
                throw new IllegalStateException("displayAxisType is invalid");
        }

        WorldProvider worldProvider = CoreRegistry.get(WorldProvider.class);

        for (int i = 0; i < blocksWide; i++) {
            for (int j = 0; j < blocksHigh; j++) {

                int dx1 = i * blockTileWidth;
                int dy1 = j * blockTileHeight;
                int dx2 = dx1 + blockTileWidth;
                int dy2 = dy1 + blockTileHeight;

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

                relativeLocation.add(centerBlockPosition);
                Block block = getBlockAtWorldPosition(worldProvider, relativeLocation);
                if (null != block) {

                    int alphaChangeCounter = 0;
                    float alpha = 1f;
                    while (BlockManager.getAir().equals(block) && (alphaChangeCounter < (depthsOfTransparency - 1))) {
                        alphaChangeCounter++;
                        alpha = darken[alphaChangeCounter];
                        relativeLocation.add(behindLocationChange);
                        block = getBlockAtWorldPosition(worldProvider, relativeLocation);
                    }

                    // let it remain black if nothing is there
                    if (block != null && !BlockManager.getAir().equals(block)) {

                        TextureRegion textureRegion = cachedRegion.get(block);
                        
                        if (null == textureRegion) {
                            WorldAtlas worldAtlas = CoreRegistry.get(WorldAtlas.class);
                            float tileSize = worldAtlas.getRelativeTileSize();

                            BlockAppearance primaryAppearance = block.getPrimaryAppearance();
                            Vector2f textureAtlasPos = primaryAppearance.getTextureAtlasPos(blockPart);

                            textureRegion = new BasicTextureRegion(textureAtlas, textureAtlasPos, new Vector2f(tileSize, tileSize));
                            cachedRegion.put(block, textureRegion);

                            
                        }

                        Rect2i pixelRegion = textureRegion.getPixelRegion();

                        int sx1 = pixelRegion.minX();
                        int sy1 = pixelRegion.minY();
                        int sx2 = pixelRegion.maxX();
                        int sy2 = pixelRegion.maxY();

                        ImageObserver observer = null;

                        Texture texture = textureRegion.getTexture();
                        AwtTexture awtTexture = (AwtTexture) texture;
                        BufferedImage bufferedImage = awtTexture.getBufferedImage(texture.getWidth(), texture.getHeight(), alpha, WHITE);
                        
//                        int sampleX = (sx1 + sx2) / 2;
//                        int sampleY = (sy1 + sy2) / 2;
//                        Color repColor = new Color(bufferedImage.getRGB(sampleX, sampleY));
//                        int red = (int) (repColor.getRed() * alpha);
//                        int green = (int) (repColor.getGreen() * alpha);
//                        int blue = (int) (repColor.getBlue() * alpha);
//                        repColor = new Color(red, green, blue);
                        
//                        drawGraphics.setColor(repColor);
//                        drawGraphics.fillRect(dx1, dy1, 4, 4);
//                        drawGraphics.drawLine(dx1, dy1, dx1, dy1);
                        drawGraphics.drawImage(bufferedImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
                    }
                }

//                if (relativeCellLocation.x == 0 && relativeCellLocation.y == 0) {
//                    drawGraphics.setColor(Color.WHITE);
//                    drawGraphics2d.setStroke(new BasicStroke(2));
//                    drawGraphics.drawRect(dx1, dy1, blockTileWidth, blockTileHeight);
//                }
            }
        }

        //      if (null != savedScreenLoc) {
        //      drawGraphics.setColor(Color.WHITE);
        //      drawGraphics2d.setStroke(new BasicStroke(2));
        //      drawGraphics.drawOval(savedScreenLoc.x - 4, savedScreenLoc.y - 4, 8, 8);
        //  }

        //        if (null != savedWorldLocation)
        //        {
        //            Vector3f relativeEntityWorldPosition = new Vector3f(savedWorldLocation);
        //            relativeEntityWorldPosition.sub(centerBlockPositionf);
        //            
        //            Vector2f screenLocation;
        //            switch (displayAxisType) {
        //                case XZ_AXIS: // top down view
        //                    screenLocation = new Vector2f(relativeEntityWorldPosition.z, -relativeEntityWorldPosition.x);
        //                    break;
        //                case YZ_AXIS:
        //                    screenLocation = new Vector2f(relativeEntityWorldPosition.z, relativeEntityWorldPosition.y);
        //                    break;
        //                case XY_AXIS:
        //                    screenLocation = new Vector2f(relativeEntityWorldPosition.x, relativeEntityWorldPosition.y);
        //                    break;
        //                default:
        //                    throw new RuntimeException("displayAxisType containts invalid value");
        //            }
        //            
        //            int drawLocationX = Math.round((screenLocation.x + mapCenterXf) * blockTileWidth);
        //            int drawLocationY = Math.round((screenLocation.y + mapCenterYf) * blockTileHeight);
        //
        //            drawGraphics.setColor(Color.RED);
        //            drawGraphics2d.setStroke(new BasicStroke(1));
        //            drawGraphics.drawOval(drawLocationX-3, drawLocationY-3, 6, 6);;
        //        }

        LocalPlayer localPlayer = CoreRegistry.get(LocalPlayer.class);
        for (EntityRef entityRef : entityManager.getEntitiesWith(CharacterComponent.class)) {
            if (entityRef.equals(localPlayer.getCharacterEntity())) {
                // Don't currently care about the idea of a local player as a character
                continue;
            }
            DisplayNameComponent displayNameComponent = entityRef.getComponent(DisplayNameComponent.class);
            String displayName = String.valueOf(entityRef);
            if (null != displayNameComponent) {
                displayName = displayNameComponent.name;
            }

            // Temporarily using item component's icon for NPCs.
            ItemComponent itemComponent = entityRef.getComponent(ItemComponent.class);
            if (null != itemComponent) {
                LocationComponent locationComponent = entityRef.getComponent(LocationComponent.class);

                if (null != locationComponent) {
                    TextureRegion textureRegion = itemComponent.icon;
                    if (null != textureRegion) {
                        AwtTexture awtTexture = (AwtTexture) textureRegion.getTexture();

                        BufferedImage bufferedImage = awtTexture.getBufferedImage(awtTexture.getWidth(), awtTexture.getHeight(), 1f, WHITE);

                        Rect2i pixelRegion = textureRegion.getPixelRegion();

                        Vector2i drawLocation = getScreenLocation(locationComponent.getWorldPosition());

                        Rect2i destRect = Rect2i.createFromMinAndSize(drawLocation.x - (pixelRegion.width() / 2), drawLocation.y - (pixelRegion.height() / 2),
                                pixelRegion.width() / 32 * blockTileWidth, pixelRegion.height() / 32 * blockTileHeight);

                        int destx1 = destRect.minX();
                        int desty1 = destRect.minY();
                        int destx2 = destRect.maxX();
                        int desty2 = destRect.maxY();

                        int sx1 = pixelRegion.minX();
                        int sy1 = pixelRegion.minY();
                        int sx2 = pixelRegion.maxX();
                        int sy2 = pixelRegion.maxY();

                        ImageObserver observer = null;

                        drawGraphics.drawImage(bufferedImage, destx1, desty1, destx2, desty2, sx1, sy1, sx2, sy2, observer);
                    } else {
                        logger.info("Need to render " + displayName + ": no itemComponent.icon");
                    }
                } else {
                    logger.info("Need to render " + displayName + ": no locationComponent");
                }
            } else {
                logger.info("Need to render " + displayName + ": no itemComponent");
            }
        }

        // TODO: block selection rendering should be done by a separate system
        for (EntityRef entityRef : entityManager.getEntitiesWith(BlockSelectionComponent.class)) {
            BlockSelectionComponent blockSelectionComponent = entityRef.getComponent(BlockSelectionComponent.class);
            if (null != blockSelectionComponent) {
                if (blockSelectionComponent.shouldRender) {
                    if (null != blockSelectionComponent.currentSelection) {
                        Vector2i drawLocation1 = getScreenLocation(blockSelectionComponent.currentSelection.min());
                        Vector2i drawLocation2 = getScreenLocation(blockSelectionComponent.currentSelection.max());
                        Rect2i rect = Rect2i.createEncompassing(drawLocation1, drawLocation2);

                        TextureRegion textureRegion = blockSelectionComponent.texture;
                        if (null == textureRegion) {
                            textureRegion = Assets.getTexture("engine:selection");
                        }

                        AwtTexture awtTexture = (AwtTexture) textureRegion.getTexture();
                        BufferedImage bufferedImage = awtTexture.getBufferedImage(awtTexture.getWidth(), awtTexture.getHeight(), 1f, WHITE);
                        Rect2i pixelRegion = textureRegion.getPixelRegion();

                        int destx1 = rect.minX();
                        int desty1 = rect.minY();
                        int destx2 = rect.maxX();
                        int desty2 = rect.maxY();

                        int sx1 = pixelRegion.minX();
                        int sy1 = pixelRegion.minY();
                        int sx2 = pixelRegion.maxX();
                        int sy2 = pixelRegion.maxY();

                        ImageObserver observer = null;

                        drawGraphics.drawImage(bufferedImage, destx1, desty1, destx2, desty2, sx1, sy1, sx2, sy2, observer);
                    } else {
                        Vector2i drawLocation1 = getScreenLocation(blockSelectionComponent.startPosition);
                        Rect2i rect = Rect2i.createEncompassing(drawLocation1, mousePosition);

                        drawGraphics2d.setStroke(new BasicStroke(1));
                        drawGraphics.setColor(Color.WHITE);
                        drawGraphics.drawRect(rect.minX(), rect.minY(), rect.width(), rect.height());
                    }
                }
            }
        }
    }

    private Vector3i getViewBlockLocation() {
        LocalPlayer localPlayer = CoreRegistry.get(LocalPlayer.class);
        Vector3f worldPosition = localPlayer.getPosition();

        centerBlockPositionf = new Vector3f(worldPosition);
        centerBlockPositionf.add(new Vector3f(cameraOffset.x, cameraOffset.y, cameraOffset.z));

        Vector3i centerBlockPosition = new Vector3i(Math.round(worldPosition.x), Math.round(worldPosition.y), Math.round(worldPosition.z));
        centerBlockPosition.add(cameraOffset);

        //        centerBlockPositionf = new Vector3f(Math.round(centerBlockPosition.x), Math.round(centerBlockPosition.y), Math.round(centerBlockPosition.z));

        return centerBlockPosition;
    }

    Vector2i savedScreenLoc;
    Vector3f savedWorldLocation;

    public Vector2i getScreenLocation(Vector3i worldLocation) {
        return getScreenLocation(new Vector3f(worldLocation.x, worldLocation.y, worldLocation.z));
    }

    public Vector2i getScreenLocation(Vector3f worldLocation) {
        Vector3f relativeEntityWorldPosition = new Vector3f(worldLocation);
        relativeEntityWorldPosition.sub(centerBlockPositionf);

        Vector2f screenLocation;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                screenLocation = new Vector2f(relativeEntityWorldPosition.z, -relativeEntityWorldPosition.x);
                break;
            case YZ_AXIS:
                screenLocation = new Vector2f(relativeEntityWorldPosition.z, relativeEntityWorldPosition.y);
                break;
            case XY_AXIS:
                screenLocation = new Vector2f(relativeEntityWorldPosition.x, relativeEntityWorldPosition.y);
                break;
            default:
                throw new RuntimeException("displayAxisType containts invalid value");
        }

        int drawLocationX = Math.round((screenLocation.x + mapCenterXf) * blockTileWidth);
        int drawLocationY = Math.round((screenLocation.y + mapCenterYf) * blockTileHeight);
        Vector2i drawLocation = new Vector2i(drawLocationX, drawLocationY);
        return drawLocation;
    }

    public Vector3f getWorldLocation(Vector2i mousePosition) {

        savedScreenLoc = mousePosition;

        Vector2f screenLocation = new Vector2f(
                ((float) mousePosition.x) / ((float) blockTileWidth) - mapCenterXf,
                ((float) mousePosition.y) / ((float) blockTileHeight) - mapCenterYf);

        Vector3f relativeEntityWorldPosition;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                relativeEntityWorldPosition = new Vector3f(-screenLocation.y, 0, screenLocation.x);
                break;
            case YZ_AXIS:
                relativeEntityWorldPosition = new Vector3f(0, screenLocation.x, screenLocation.y);
                break;
            case XY_AXIS:
                relativeEntityWorldPosition = new Vector3f(screenLocation.x, screenLocation.y, 0);
                break;
            default:
                throw new RuntimeException("displayAxisType contains invalid value");
        }

        relativeEntityWorldPosition.add(centerBlockPositionf);

        savedWorldLocation = relativeEntityWorldPosition;

        return relativeEntityWorldPosition;
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
