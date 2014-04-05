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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Transparency;
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
import org.terasology.math.Rect2f;
import org.terasology.math.Rect2i;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector2i;
import org.terasology.math.Vector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.texture.BasicTextureRegion;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureRegion;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.nui.NUIManager;
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

    private Map<Block, Color> cachedColorTop = Maps.newHashMap();
    private Map<Block, Color> cachedColorLeft = Maps.newHashMap();
    private Map<Block, Color> cachedColorFront = Maps.newHashMap();

    private Map<Block, BufferedImage> cachedImagesTop = Maps.newHashMap();
    private Map<Block, BufferedImage> cachedImagesLeft = Maps.newHashMap();
    private Map<Block, BufferedImage> cachedImagesFront = Maps.newHashMap();

    private int zoomLevel = 6;

    private int depthsOfTransparency = 16;
    private float[] darken;

    private Vector3i centerBlockPosition;              // not sure, if these should really be class members
    private int mapCenterY;
    private int mapCenterX;

//    private Vector3f centerBlockPositionf;              // not sure, if these should really be class members
//    private float mapCenterYf;
//    private float mapCenterXf;
    private Vector2i savedScreenLoc;
    private Vector3f savedWorldLocation;

    private EntityManager entityManager;

    private LocalPlayer localPlayer;

    private enum RenderMode {
        IMAGE,
        SQUARE,
        POINT
    }

    public BlockTileWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);

        textureAtlas = Assets.getTexture("engine:terrain");

        ComponentSystemManager componentSystemManager = CoreRegistry.get(ComponentSystemManager.class);
        WorldControlSystem worldControlSystem = new WorldControlSystem(this);
        componentSystemManager.register(worldControlSystem, "awt:WorldControlSystem");
        CoreRegistry.put(WorldControlSystem.class, worldControlSystem);

        darken = new float[depthsOfTransparency];
        darken[0] = 1f;

        for (int i = 1; i < depthsOfTransparency; i++) {
            darken[i] = (depthsOfTransparency - i) / ((float) depthsOfTransparency);
        }

        entityManager = CoreRegistry.get(EntityManager.class);

        // Must assign here, so that we are the first HUD element assigned to the NUI HUD manager to assure
        // that we are the last consumer of mouse events
        WorldSelectionScreen worldSelectionScreen = CoreRegistry.get(NUIManager.class).getHUD()
                .addHUDElement("engine:worldSelectionScreen", WorldSelectionScreen.class, Rect2f.createFromMinAndSize(0, 0, 1, 1));
        worldSelectionScreen.setRenderer(this);
    }

    @Override
    public void setPlayer(LocalPlayer localPlayer) {
        super.setPlayer(localPlayer);
        this.localPlayer = localPlayer;

        // Make our active camera match the starting local player location rather than try to control local player location directly
        EntityRef entity = localPlayer.getCharacterEntity();
        CharacterComponent characterComponent = entity.getComponent(CharacterComponent.class);
        LocationComponent location = entity.getComponent(LocationComponent.class);

        Vector3f cameraPosition = new Vector3f();
        cameraPosition.add(new Vector3f(location.getWorldPosition()), new Vector3f(0, characterComponent.eyeOffset, 0));
        getActiveCamera().getPosition().set(cameraPosition);
    }

    public void renderWorld(Camera camera) {
        Vector3i centerBlockPosition = getViewBlockLocation();

        renderBlockTileWorld(camera, centerBlockPosition);
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

    public void renderBlockTileWorld(Camera camera, Vector3i centerBlockPosition) {

        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics g1 = displayDevice.getDrawGraphics();
        Graphics2D g = (Graphics2D) g1;
        int width = displayDevice.getWidth();
        int height = displayDevice.getHeight();

        InputSystem inputSystem = CoreRegistry.get(InputSystem.class);
        Vector2i mousePosition = inputSystem.getMouseDevice().getPosition();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        int blockTileSize = getBlockTileSize();

        int blocksWide = IntMath.divide(width, blockTileSize, RoundingMode.CEILING);
        int blocksHigh = IntMath.divide(height, blockTileSize, RoundingMode.CEILING);

        // update chunk production to cover the entire screen
        ChunkProvider chunkProvider = getChunkProvider();
        int chunksWide = blocksWide / ChunkConstants.SIZE_X;
        int chunksHigh = blocksHigh / ChunkConstants.SIZE_Z;
        int chunkDist = Math.max(chunksWide, chunksHigh);
        EntityRef entity = localPlayer.getClientEntity();
        chunkProvider.updateRelevanceEntity(entity, chunkDist);

        RenderMode renderMode;
        if (blockTileSize == 1) {
            renderMode = RenderMode.POINT;
        } else if (blockTileSize <= 4) {
            renderMode = RenderMode.SQUARE;
        } else {
            renderMode = RenderMode.IMAGE;
        }

        // TODO: add camera coords.
//        mapCenterXf = ((blocksWide + 0.5f) / 2f);
//        mapCenterYf = ((blocksHigh + 0.5f) / 2f);

        mapCenterX = (int) ((blocksWide + 0.5f) / 2f);
        mapCenterY = (int) ((blocksHigh + 0.5f) / 2f);

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
        Map<Block, Color> cachedColor;
        Map<Block, BufferedImage> cachedImages;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                blockPart = BlockPart.TOP;
                cachedColor = cachedColorTop;
                cachedImages = cachedImagesTop;
                break;
            case YZ_AXIS:
                blockPart = BlockPart.LEFT; // todo: front/left/right/back needs to be picked base on viewpoint
                cachedColor = cachedColorLeft;
                cachedImages = cachedImagesLeft;
                break;
            case XY_AXIS:
                blockPart = BlockPart.FRONT; // todo: front/left/right/back needs to be picked base on viewpoint
                cachedColor = cachedColorFront;
                cachedImages = cachedImagesFront;
                break;
            default:
                throw new IllegalStateException("displayAxisType is invalid");
        }

        //        cachedImages.clear();
        //        cachedColor.clear();

        WorldProvider worldProvider = CoreRegistry.get(WorldProvider.class);
        WorldAtlas worldAtlas = CoreRegistry.get(WorldAtlas.class);
        float tileSize = worldAtlas.getRelativeTileSize();
        float prevAlpha = -1;

        for (int i = 0; i < blocksWide; i++) {
            for (int j = 0; j < blocksHigh; j++) {

                int dx1 = i * blockTileSize;
                int dy1 = j * blockTileSize;

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

                        Color blockColor = null;
                        BufferedImage blockImage = null;

                        if (renderMode == RenderMode.POINT || renderMode == RenderMode.SQUARE) {
                            blockColor = cachedColor.get(block);
                        }

                        if (renderMode == RenderMode.IMAGE || blockColor == null) {
                            blockImage = cachedImages.get(block);

                            if (null == blockImage) {

                                BlockAppearance primaryAppearance = block.getPrimaryAppearance();
                                Vector2f textureAtlasPos = primaryAppearance.getTextureAtlasPos(blockPart);

                                Vector2f size = new Vector2f(tileSize, tileSize);
                                TextureRegion textureRegion = new BasicTextureRegion(textureAtlas, textureAtlasPos, size);
                                Rect2i pixelRegion = textureRegion.getPixelRegion();

                                int sx1 = pixelRegion.minX();
                                int sy1 = pixelRegion.minY();
                                int sx2 = sx1 + pixelRegion.width();    // Surprisingly, maxX() is not minX + width()
                                int sy2 = sy1 + pixelRegion.height();

                                Texture texture = textureRegion.getTexture();
                                AwtTexture awtTexture = (AwtTexture) texture;
                                BufferedImage fullImage = awtTexture.getBufferedImage(texture.getWidth(), texture.getHeight(), 1f, WHITE);

                                GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

                                int w = pixelRegion.width();
                                int h = pixelRegion.height();
                                blockImage = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
                                BufferedImage tiny = gc.createCompatibleImage(1, 1, Transparency.TRANSLUCENT);

                                ImageObserver observer = null;

                                Graphics2D bg = (Graphics2D) blockImage.getGraphics();
                                bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                bg.drawImage(fullImage, 0, 0, w, h, sx1, sy1, sx2, sy2, observer);
                                bg.dispose();

                                cachedImages.put(block, blockImage);

                                Graphics2D tg = (Graphics2D) tiny.getGraphics();
                                tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                tg.drawImage(fullImage, 0, 0, 1, 1, sx1, sy1, sx2, sy2, observer);
                                tg.dispose();

                                // I think this is correct, but the color appears to be darker than the average color of the original image
                                blockColor = new Color(tiny.getRGB(0, 0));
                                cachedColor.put(block, blockColor);
                            }

                        }

                        if (alpha != prevAlpha) {
                            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                        }

                        if (renderMode == RenderMode.POINT || renderMode == RenderMode.SQUARE) {
                            int red = (int) (blockColor.getRed() * alpha);
                            int green = (int) (blockColor.getGreen() * alpha);
                            int blue = (int) (blockColor.getBlue() * alpha);
                            blockColor = new Color(red, green, blue);

                            g.setColor(blockColor);

                            if (renderMode == RenderMode.SQUARE) {
                                g.fillRect(dx1, dy1, blockTileSize, blockTileSize);
                            } else {
                                g.drawLine(dx1, dy1, dx1, dy1);
                            }
                        }
                        else {
                            ImageObserver observer = null;
                            g.drawImage(blockImage, dx1, dy1, blockTileSize, blockTileSize, observer);
                        }

                        prevAlpha = alpha;
                    }
                }

                if (relativeCellLocation.x == 0 && relativeCellLocation.y == 0) {
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(dx1, dy1, blockTileSize, blockTileSize);
                }
            }
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        drawCharacterEntities(g, blockTileSize, centerBlockPosition);
        drawBlockSelection(g, mousePosition);
    }

    //    public void testMouseHit(Graphics2D g, int blockTileWidth, int blockTileHeight) {
    //        if (null != savedScreenLoc) {
    //            g.setColor(Color.WHITE);
    //            g.setStroke(new BasicStroke(2));
    //            g.drawOval(savedScreenLoc.x - 4, savedScreenLoc.y - 4, 8, 8);
    //        }
    //
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
    //            g.setColor(Color.RED);
    //            g.setStroke(new BasicStroke(1));
    //            g.drawOval(drawLocationX - 3, drawLocationY - 3, 6, 6);;
    //        }
    //    }

    public void drawCharacterEntities(Graphics2D g, int blockTileSize, Vector3i centerBlockPosition) {
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
                    
                    Vector3f characterWorldPosition = locationComponent.getWorldPosition();
                    boolean shouldDraw = true;
                    int alphaChangeCounter = 0;
                    switch (displayAxisType) {
                        case XZ_AXIS:
                            if (characterWorldPosition.y > centerBlockPosition.y) {
                                shouldDraw = false;
                            } else {
                                alphaChangeCounter = Math.round(centerBlockPosition.y - characterWorldPosition.y);
                                if (alphaChangeCounter > (depthsOfTransparency - 1)) {
                                    shouldDraw = false;
                                }
                            }
                            break;
                        case YZ_AXIS:
                            if (characterWorldPosition.x < centerBlockPosition.x) {
                                shouldDraw = false;
                            } else {
                                alphaChangeCounter = Math.round(characterWorldPosition.x - centerBlockPosition.x);
                                if (alphaChangeCounter > (depthsOfTransparency - 1)) {
                                    shouldDraw = false;
                                }
                            }
                            break;
                        case XY_AXIS:
                            if (characterWorldPosition.z < centerBlockPosition.z) {
                                shouldDraw = false;
                            } else {
                                alphaChangeCounter = Math.round(characterWorldPosition.z - centerBlockPosition.z);
                                if (alphaChangeCounter > (depthsOfTransparency - 1)) {
                                    shouldDraw = false;
                                }
                            }
                            break;
                        default:
                            throw new RuntimeException("illegal displayAxisType " + displayAxisType);
                    }

                    // TODO: if we are behind something non-transparent, then do not draw either

                    if (shouldDraw) {
                        float alpha = darken[alphaChangeCounter];
                        TextureRegion textureRegion = itemComponent.icon;
                        if (null != textureRegion) {
                            AwtTexture awtTexture = (AwtTexture) textureRegion.getTexture();

                            BufferedImage bufferedImage = awtTexture.getBufferedImage(
                                    awtTexture.getWidth(), awtTexture.getHeight(), alpha, WHITE);

                            Rect2i pixelRegion = textureRegion.getPixelRegion();

                            Vector2i drawLocation = getScreenLocation(characterWorldPosition);

                            Rect2i destRect = Rect2i.createFromMinAndSize(drawLocation.x - (pixelRegion.width() / 2), drawLocation.y - (pixelRegion.height() / 2),
                                    pixelRegion.width() / 32 * blockTileSize, pixelRegion.height() / 32 * blockTileSize);

                            int destx1 = destRect.minX();
                            int desty1 = destRect.minY();
                            int destx2 = destRect.maxX();
                            int desty2 = destRect.maxY();

                            int sx1 = pixelRegion.minX();
                            int sy1 = pixelRegion.minY();
                            int sx2 = pixelRegion.maxX();
                            int sy2 = pixelRegion.maxY();

                            ImageObserver observer = null;

                            g.drawImage(bufferedImage, destx1, desty1, destx2, desty2, sx1, sy1, sx2, sy2, observer);
                        } else {
                            logger.info("Need to render " + displayName + ": no itemComponent.icon");
                        }
                    }
                } else {
                    logger.info("Need to render " + displayName + ": no locationComponent");
                }
            } else {
                logger.info("Need to render " + displayName + ": no itemComponent");
            }
        }
    }

    public void drawBlockSelection(Graphics2D g, Vector2i mousePosition) {
        // TODO: block selection rendering should be done by a separate system
        for (EntityRef entityRef : entityManager.getEntitiesWith(BlockSelectionComponent.class)) {
            BlockSelectionComponent blockSelectionComponent = entityRef.getComponent(BlockSelectionComponent.class);
            if (null != blockSelectionComponent) {
                if (blockSelectionComponent.shouldRender) {
                    if (null != blockSelectionComponent.currentSelection) {
                        
                        boolean shouldDraw = true;
                        switch (displayAxisType) {
                            case XZ_AXIS:
                                if (centerBlockPosition.y < blockSelectionComponent.currentSelection.min().y ||  centerBlockPosition.y > blockSelectionComponent.currentSelection.max().y) {
                                    shouldDraw = false;
                                }
                                break;
                            case YZ_AXIS:
                                if (centerBlockPosition.x < blockSelectionComponent.currentSelection.min().x ||  centerBlockPosition.x > blockSelectionComponent.currentSelection.max().x) {
                                    shouldDraw = false;
                                }
                                break;
                            case XY_AXIS:
                                if (centerBlockPosition.z < blockSelectionComponent.currentSelection.min().z ||  centerBlockPosition.z > blockSelectionComponent.currentSelection.max().z) {
                                    shouldDraw = false;
                                }
                                break;
                            default:
                                throw new RuntimeException("illegal displayAxisType " + displayAxisType);
                        }
                        
                        if (shouldDraw) {
                            Vector2i drawLocation1 = getScreenLocation(blockSelectionComponent.currentSelection.min());
                            Vector3i max = blockSelectionComponent.currentSelection.max();
                            max.add(1, 1, 1);
                            Vector2i drawLocation2 = getScreenLocation(max);
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
                            logger.info("Drawing " + blockSelectionComponent.currentSelection + " at " + rect);
                            int sx1 = pixelRegion.minX();
                            int sy1 = pixelRegion.minY();
                            int sx2 = pixelRegion.maxX();
                            int sy2 = pixelRegion.maxY();

                            ImageObserver observer = null;

                            g.drawImage(bufferedImage, destx1, desty1, destx2, desty2, sx1, sy1, sx2, sy2, observer);
                        }
//                    } else {
//                        Vector2i drawLocation1 = getScreenLocation(blockSelectionComponent.startPosition);
//                        Rect2i rect = Rect2i.createEncompassing(drawLocation1, mousePosition);
//
//                        g.setStroke(new BasicStroke(1));
//                        g.setColor(Color.WHITE);
//                        g.drawRect(rect.minX(), rect.minY(), rect.width(), rect.height());
                    }
                }
            }
        }
    }

    /**
     * @return
     */
    private int getBlockTileSize() {
        return (int) 1 << (zoomLevel - 1);
    }

    private Vector3i getViewBlockLocation() {
        Camera camera = getActiveCamera();
        Vector3f worldPosition = camera.getPosition();

        // centerBlockPositionf = new Vector3f(worldPosition);

        centerBlockPosition = new Vector3i(Math.round(worldPosition.x), Math.round(worldPosition.y), Math.round(worldPosition.z));

        return centerBlockPosition;
    }

    public Vector2i getScreenLocation(Vector3i worldLocation) {
        return getScreenLocation(new Vector3f(worldLocation.x, worldLocation.y, worldLocation.z));
    }

//    public Vector2i getScreenLocation_OLDFloat(Vector3f worldLocation) {
//        Vector3f relativeEntityWorldPosition = new Vector3f(worldLocation);
//        relativeEntityWorldPosition.sub(centerBlockPositionf);
//
//        Vector2f screenLocation;
//        switch (displayAxisType) {
//            case XZ_AXIS: // top down view
//                screenLocation = new Vector2f(relativeEntityWorldPosition.z, -relativeEntityWorldPosition.x);
//                break;
//            case YZ_AXIS:
//                screenLocation = new Vector2f(relativeEntityWorldPosition.z, relativeEntityWorldPosition.y);
//                break;
//            case XY_AXIS:
//                screenLocation = new Vector2f(-relativeEntityWorldPosition.x, relativeEntityWorldPosition.y);
//                break;
//            default:
//                throw new RuntimeException("displayAxisType containts invalid value");
//        }
//
//        int blockTileSize = getBlockTileSize();
//
//        int drawLocationX = Math.round((screenLocation.x + mapCenterXf) * blockTileSize);
//        int drawLocationY = Math.round((screenLocation.y + mapCenterYf) * blockTileSize);
//        Vector2i drawLocation = new Vector2i(drawLocationX, drawLocationY);
//        return drawLocation;
//    }

    public Vector2i getScreenLocation(Vector3f worldLocation) {
        Vector3i relativeEntityWorldPosition = new Vector3i(worldLocation);
        relativeEntityWorldPosition.sub(centerBlockPosition);

        Vector2i screenLocation;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                screenLocation = new Vector2i(relativeEntityWorldPosition.z, -relativeEntityWorldPosition.x);
                break;
            case YZ_AXIS:
                screenLocation = new Vector2i(relativeEntityWorldPosition.z, relativeEntityWorldPosition.y);
                break;
            case XY_AXIS:
                screenLocation = new Vector2i(-relativeEntityWorldPosition.x, relativeEntityWorldPosition.y);
                break;
            default:
                throw new RuntimeException("displayAxisType containts invalid value");
        }

        int blockTileSize = getBlockTileSize();

        int drawLocationX = (screenLocation.x + mapCenterX) * blockTileSize;
        int drawLocationY = (screenLocation.y + mapCenterY) * blockTileSize;
        Vector2i drawLocation = new Vector2i(drawLocationX, drawLocationY);
        return drawLocation;
    }

//    public Vector3f getWorldLocation_OLD_Float(Vector2i mousePosition) {
//
//        savedScreenLoc = mousePosition;
//
//        int blockTileSize = getBlockTileSize();
//
//        Vector2f screenLocation = new Vector2f(
//                ((float) mousePosition.x) / ((float) blockTileSize) - mapCenterXf,
//                ((float) mousePosition.y) / ((float) blockTileSize) - mapCenterYf);
//
//        Vector3f relativeEntityWorldPosition;
//        switch (displayAxisType) {
//            case XZ_AXIS: // top down view
//                relativeEntityWorldPosition = new Vector3f(-screenLocation.y, 0, screenLocation.x);
//                break;
//            case YZ_AXIS:
//                relativeEntityWorldPosition = new Vector3f(0, screenLocation.x, screenLocation.y);
//                break;
//            case XY_AXIS:
//                relativeEntityWorldPosition = new Vector3f(-screenLocation.x, screenLocation.y, 0);
//                break;
//            default:
//                throw new RuntimeException("displayAxisType contains invalid value");
//        }
//
//        relativeEntityWorldPosition.add(centerBlockPositionf);
//
//        savedWorldLocation = relativeEntityWorldPosition;
//
//        return relativeEntityWorldPosition;
//    }

    public Vector3i getWorldLocation(Vector2i mousePosition) {

        savedScreenLoc = mousePosition;

        int blockTileSize = getBlockTileSize();

        Vector2f screenLocation = new Vector2f(
                ((float) mousePosition.x) / (float)(blockTileSize) - mapCenterX,
                ((float) mousePosition.y) / (float)(blockTileSize) - mapCenterY);

        Vector3i relativeEntityWorldPosition;
        switch (displayAxisType) {
            case XZ_AXIS: // top down view
                relativeEntityWorldPosition = new Vector3i(-screenLocation.y, 0, screenLocation.x);
                break;
            case YZ_AXIS:
                relativeEntityWorldPosition = new Vector3i(0, screenLocation.x, screenLocation.y);
                break;
            case XY_AXIS:
                relativeEntityWorldPosition = new Vector3i(-screenLocation.x, screenLocation.y, 0);
                break;
            default:
                throw new RuntimeException("displayAxisType contains invalid value");
        }

        relativeEntityWorldPosition.add(centerBlockPosition);

//        savedWorldLocation = relativeEntityWorldPosition;

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
        Camera camera = getActiveCamera();
        Vector3f cameraPosition = camera.getPosition();
        cameraPosition.set(cameraPosition.getX() + i, cameraPosition.getY() + j, cameraPosition.getZ() + k);
    }

    public void zoomIn() {
        if (zoomLevel < 7) {
            zoomLevel++;
        }
    }

    public void zoomOut() {
        if (zoomLevel > 1) {
            zoomLevel--;
        }
    }

}
