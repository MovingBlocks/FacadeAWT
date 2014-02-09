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
package org.terasology.engine.subsystem.awt.renderer;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JFrame;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import org.terasology.asset.AssetUri;
import org.terasology.engine.subsystem.awt.assets.AwtFont;
import org.terasology.engine.subsystem.awt.assets.AwtMaterial;
import org.terasology.engine.subsystem.awt.assets.AwtTexture;
import org.terasology.engine.subsystem.awt.assets.AwtTexture.BufferedImageCacheKey;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.math.Border;
import org.terasology.math.Rect2f;
import org.terasology.math.Rect2i;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector2i;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.font.Font;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.assets.texture.BasicTextureRegion;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureRegion;
import org.terasology.rendering.nui.Color;
import org.terasology.rendering.nui.HorizontalAlign;
import org.terasology.rendering.nui.ScaleMode;
import org.terasology.rendering.nui.VerticalAlign;
import org.terasology.rendering.nui.internal.CanvasRenderer;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.BlockUri;
import org.terasology.world.block.family.BlockFamily;
import org.terasology.world.block.loader.WorldAtlas;

/**
 * @author mkienenb
 */
public class AwtCanvasRenderer implements CanvasRenderer {

    private JFrame window;

    private Graphics drawGraphics;
    private AwtDisplayDevice awtDisplayDevice;

    public AwtCanvasRenderer(JFrame window, AwtDisplayDevice awtDisplayDevice) {
        this.window = window;
        this.awtDisplayDevice = awtDisplayDevice;
    }

    @Override
    public void preRender() {
        drawGraphics = awtDisplayDevice.getDrawGraphics();
        drawGraphics.setPaintMode();
    }

    @Override
    public void postRender() {
        awtDisplayDevice.show();
    }

    @Override
    public void drawMesh(Mesh mesh, Material material, Rect2i drawRegion, Rect2i cropRegion, Quat4f rotation, Vector3f offset, float scale, float alpha) {
        AwtMaterial awtMaterial = (AwtMaterial) material;
        Texture texture = awtMaterial.getTexture("texture");
        if (null == texture) {
            throw new RuntimeException("unsupported");
        }

        Vector2f textureAtlasPos;

        BlockManager blockManager = CoreRegistry.get(BlockManager.class);
        AssetUri meshUri = mesh.getURI();
        String assetName = meshUri.getAssetName();
        if (assetName.contains(".")) {
            String familyName = assetName.substring(0, assetName.indexOf('.'));

            BlockUri blockUri = new BlockUri(meshUri.getModuleName(), familyName);
            BlockFamily blockFamily = blockManager.getBlockFamily(blockUri); // mesh:Core:Torch.TOP
            Block archetypeBlock = blockFamily.getArchetypeBlock();
            BlockAppearance primaryAppearance = archetypeBlock.getPrimaryAppearance();

            String blockPartName = assetName.substring(assetName.indexOf('.') + 1);
            BlockPart blockPart = BlockPart.valueOf(blockPartName);

            textureAtlasPos = primaryAppearance.getTextureAtlasPos(blockPart);
        } else {
            String familyName = assetName;
            BlockUri blockUri = new BlockUri(meshUri.getModuleName(), familyName);

            BlockFamily blockFamily = blockManager.getBlockFamily(blockUri); // mesh:Core:Torch.TOP
            Block archetypeBlock = blockFamily.getArchetypeBlock();
            BlockAppearance primaryAppearance = archetypeBlock.getPrimaryAppearance();

            BlockPart blockPart = BlockPart.FRONT; // assume it doesn't matter
            textureAtlasPos = primaryAppearance.getTextureAtlasPos(blockPart);
        }

        WorldAtlas worldAtlas = CoreRegistry.get(WorldAtlas.class);
        float tileSize = worldAtlas.getRelativeTileSize();

        float ux = 0f;
        float uy = 0f;
        float uw = 1f;
        float uh = 1f;

        TextureRegion textureRegion = new BasicTextureRegion(texture, textureAtlasPos, new Vector2f(tileSize, tileSize));
        drawTexture(textureRegion, Color.WHITE, ScaleMode.STRETCH, drawRegion, ux, uy, uw, uh, alpha);
        return;
    }

    @Override
    public Vector2i getTargetSize() {
        return new Vector2i(window.getContentPane().getWidth(), window.getContentPane().getHeight());
    }

    @Override
    public void drawMaterialAt(Material material, Rect2i drawRegion) {
        throw new RuntimeException("unsupported");
    }

    @Override
    public void drawLine(int sx, int sy, int ex, int ey, Color color) {
        drawGraphics.setColor(getAwtColor(color));
        Graphics2D g2 = (Graphics2D) drawGraphics;
        Stroke originalStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2));
        drawGraphics.drawLine(sx, sy, ex, ey);
        g2.setStroke(originalStroke);
    }

    private java.awt.Color getAwtColor(Color color) {
        if (null == color) {
            return null;
        }
        return new java.awt.Color(color.r(), color.g(), color.b(), color.a());
    }

    private java.awt.Color getAwtColor(Color color, float alpha) {
        if (null == color) {
            return null;
        }
        return new java.awt.Color(color.r(), color.g(), color.b(), Math.round(color.a() * alpha));
    }

    /**
     * This content is from Stack Overflow.
     * http://stackoverflow.com/questions/13323701/align-text-to-the-right-in-a-textlayout-using-java-graphics2d-api/13325210#13325210
     * http://creativecommons.org/licenses/by-sa/3.0/
     * by giorgiline
     * http://stackoverflow.com/users/1136158/giorgiline
     * Note that this code has been modified from the original.
     * 
     * Apparently, it's ok to use this code without doing anything further than the above:
     * http://meta.stackoverflow.com/questions/139698/re-using-ideas-or-small-pieces-of-code-from-stackoverflow-com#139701
     * Ideally we would contact giorgiline and request an ASF-compatible license, 
     * but StackOverflow makes it impossible for me to communicate with giorgiline directly.
     * 
     * Draw paragraph.
     * Pinta un parrafo segun las localizaciones pasadas como parametros.
     *
     * @param g2 Drawing graphic.
     * @param text String to draw.
     * @param vAlign 
     * @param width Paragraph's desired width.
     * @param x Start paragraph's X-Position.
     * @param y Start paragraph's Y-Position.
     * @param dir Paragraph's alignment.
     * @return Next line Y-position to write to.
     */
    private float drawParagraph(Graphics2D g2, java.awt.Font font, String text,
                                Rect2i absoluteRegion,
                                HorizontalAlign alignment, VerticalAlign vAlign) {
        boolean isCalculating = true;
        float newY = drawParagraph(g2, font, text, absoluteRegion.width(), absoluteRegion.minX(), absoluteRegion.minY(), alignment, isCalculating);
        int vOffset = vAlign.getOffset(((int) newY) - absoluteRegion.minY(), absoluteRegion.height());
        isCalculating = false;
        return drawParagraph(g2, font, text, absoluteRegion.width(), absoluteRegion.minX(), absoluteRegion.minY() + vOffset, alignment, isCalculating);
    }

    private float drawParagraph(Graphics2D g2, java.awt.Font font, String originalText,
                                int widthInt, int xInt, int yInt,
                                HorizontalAlign alignment, boolean isCalculating) {

        float width = (float) widthInt;
        float x = (float) xInt;
        float y = (float) yInt;

        float drawPosY = y;

        String[] textArray = originalText.split("\n");
        for (int index = 0; index < textArray.length; index++) {
            String text = textArray[index];

            AttributedString attstring;
            if (text.length() == 0) {
                attstring = new AttributedString(" ");
            } else {
                attstring = new AttributedString(text);
            }

            attstring.addAttribute(TextAttribute.FONT, font);
            AttributedCharacterIterator paragraph = attstring.getIterator();
            int paragraphStart = paragraph.getBeginIndex();
            int paragraphEnd = paragraph.getEndIndex();
            FontRenderContext frc = g2.getFontRenderContext();
            LineBreakMeasurer lineMeasurer = new LineBreakMeasurer(paragraph, frc);

            // Set break width to width of Component.
            float breakWidth = width;
            // Set position to the index of the first character in the paragraph.
            lineMeasurer.setPosition(paragraphStart);

            // Get lines until the entire paragraph has been displayed.
            while (lineMeasurer.getPosition() < paragraphEnd) {
                // Retrieve next layout. A cleverer program would also cache
                // these layouts until the component is re-sized.
                TextLayout layout = lineMeasurer.nextLayout(breakWidth);
                // Compute pen x position. 
                float drawPosX;
                switch (alignment) {
                    case RIGHT:
                        drawPosX = (float) x + breakWidth - layout.getAdvance();
                        break;
                    case CENTER:
                        drawPosX = (float) x + (breakWidth - layout.getAdvance()) / 2;
                        break;
                    default:
                        drawPosX = (float) x;
                }
                // Move y-coordinate by the ascent of the layout.
                drawPosY += layout.getAscent();

                if (!isCalculating) {
                    // Draw the TextLayout at (drawPosX, drawPosY).
                    layout.draw(g2, drawPosX, drawPosY);
                }

                // Move y-coordinate in preparation for next layout.
                drawPosY += layout.getDescent() + layout.getLeading();
            }
        }
        return drawPosY;
    }

    @Override
    public void drawText(String text, Font font,
                         HorizontalAlign hAlign, VerticalAlign vAlign,
                         Rect2i absoluteRegion,
                         Color color, Color shadowColor, float alpha) {
        java.awt.Font javaAwtFont = ((AwtFont) font).getAwtFont();

        if (shadowColor.a() != 0) {
            drawGraphics.setColor(getAwtColor(shadowColor, alpha));
            Rect2i shadowRegion = Rect2i.createFromMinAndSize(new Vector2i(absoluteRegion.minX() + 1, absoluteRegion.minY() + 1), absoluteRegion.size());
            drawParagraph((Graphics2D) drawGraphics, javaAwtFont, text, shadowRegion, hAlign, vAlign);
        }

        drawGraphics.setColor(getAwtColor(color, alpha));
        drawParagraph((Graphics2D) drawGraphics, javaAwtFont, text, absoluteRegion, hAlign, vAlign);

    }

    @Override
    public void crop(Rect2i cropRegion) {
        drawGraphics.setClip(null);
        drawGraphics.clipRect(cropRegion.minX(), cropRegion.minY(), cropRegion.width() + 1, cropRegion.height() + 1);
    }

    @Override
    public void drawTexture(TextureRegion textureRegion, Color color, ScaleMode mode,
                            Rect2i absoluteRegion,
                            float ux, float uy, float uw, float uh, float alpha) {
        // TODO: I think we might want to crop at this point, like LwjglCanvasRender does

        //        vec4 pos = gl_Vertex;
        //        pos.xy *= scale;
        //        pos.xy += offset;
        //        relPos = pos.xy;
        //            gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * pos;
        //        gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
        //        gl_TexCoord[0].xy = texOffset + gl_TexCoord[0].xy * (texSize) ;
        //        gl_FrontColor = color;

        //      textureMat.setFloat2("scale", scale);
        //      textureMat.setFloat2("offset",
        //              absoluteRegion.minX() + 0.5f * (absoluteRegion.width() - scale.x),
        //              absoluteRegion.minY() + 0.5f * (absoluteRegion.height() - scale.y));
        //      textureMat.setFloat2("texOffset", textureArea.minX() + ux * textureArea.width(), textureArea.minY() + uy * textureArea.height());
        //      textureMat.setFloat2("texSize", uw * textureArea.width(), uh * textureArea.height());
        //      textureMat.setTexture("texture", texture.getTexture());
        //      textureMat.setFloat4("color", color.rf(), color.gf(), color.bf(), color.af() * alpha);
        //      textureMat.bindTextures();

        Texture texture = textureRegion.getTexture();
        AwtTexture awtTexture = (AwtTexture) texture;

        BufferedImage bufferedImage = awtTexture.getBufferedImage(texture.getWidth(), texture.getHeight(), alpha, color);

        Rect2i sourceRegion = getSourceRegion(textureRegion, ux, uy, uw, uh);
        Rect2i destinationRegion = getDestinationRegion(textureRegion, absoluteRegion, mode);
        switch (mode) {
            case SCALE_FILL:
                drawImageInternal(bufferedImage, destinationRegion, sourceRegion);
                break;
            case SCALE_FIT:
                drawImageInternal(bufferedImage, destinationRegion, sourceRegion);
                break;
            case STRETCH:
                drawImageInternal(bufferedImage, absoluteRegion, sourceRegion);
                break;
            case TILED:
                int xInc = absoluteRegion.width();
                int yInc = absoluteRegion.height();
                for (int x = absoluteRegion.minX(); x < xInc; x += xInc) {
                    for (int y = absoluteRegion.maxX(); y < yInc; y += yInc) {
                        Rect2i tileDestinationRegion = Rect2i.createFromMinAndSize(new Vector2i(x, y), sourceRegion.size());
                        drawImageInternal(bufferedImage, tileDestinationRegion, sourceRegion);
                    }
                }
                break;
            default:
                throw new RuntimeException("Unsupported mode: " + mode);
        }
    }

    private Rect2i getDestinationRegion(TextureRegion textureRegion,
                                        Rect2i absoluteRegion,
                                        ScaleMode mode) {

        return getDestinationRegion(textureRegion.getWidth(), textureRegion.getHeight(), absoluteRegion, mode);
    }

    private Rect2i getDestinationRegion(int width, int height,
                                        Rect2i absoluteRegion,
                                        ScaleMode mode) {
        Vector2f scale = mode.scaleForRegion(absoluteRegion, width, height);

        Vector2i scaleAdjustment = new Vector2i(Math.round(scale.x), Math.round(scale.y));
        Vector2i offsetAdjustment = new Vector2i(Math.round(absoluteRegion.minX() + 0.5f * (absoluteRegion.width() - scale.x)),
                Math.round(absoluteRegion.minY() + 0.5f * (absoluteRegion.height() - scale.y)));

        Rect2i totalAdjustment = Rect2i.createFromMinAndSize(offsetAdjustment, scaleAdjustment);

        return totalAdjustment;
    }

    private Rect2i getSourceRegion(TextureRegion textureRegion,
                                   float ux, float uy, float uw, float uh) {
        Rect2i pixelRegion = textureRegion.getPixelRegion();

        Rect2i textureArea2 = Rect2i.createFromMinAndSize(
                Math.round(pixelRegion.minX() + ux * pixelRegion.width()),
                Math.round(pixelRegion.minY() + uy * pixelRegion.height()),
                Math.round(uw * pixelRegion.width()),
                Math.round(uh * pixelRegion.height()));

        return textureArea2;
    }

    private void drawImageInternal(BufferedImage bufferedImage, Rect2i destinationRegion, Rect2i sourceRegion) {

        ImageObserver observer = null;
        drawGraphics.drawImage(bufferedImage,
                destinationRegion.minX(), destinationRegion.minY(), destinationRegion.maxX(), destinationRegion.maxY(),
                (int) sourceRegion.minX(), sourceRegion.minY(), (int) sourceRegion.maxX(), sourceRegion.maxY(),
                observer);
    }

    /**
     * This breaks the texture up into 9 pieces like a tic-tac-toe board into corners, edges, and center, and then tiles or scales appropriately only in its section
     */
    @Override
    public void drawTextureBordered(TextureRegion texture, Rect2i region, Border border, boolean tile, float ux, float uy, float uw, float uh, float alpha) {
        // TODO: I think we might want to crop at this point, like LwjglCanvasRender does
        drawTextureBorderedWithoutUxUy(texture, region, border, tile, uw, uh, alpha);
    }

    /**
     * It's unclear why I don't need to use ux/uy
     */
    private void drawTextureBorderedWithoutUxUy(TextureRegion texture, Rect2i region, Border border, boolean tile, float uw, float uh, float alpha) {
        Texture textureRegionTexture = texture.getTexture();
        AwtTexture awtTexture = (AwtTexture) textureRegionTexture;

        Vector2i textureSize = new Vector2i(TeraMath.ceilToInt(texture.getWidth() * uw), TeraMath.ceilToInt(texture.getHeight() * uh));

        BufferedImageCacheKey key = new BufferedImageCacheKey(textureSize, region.size(), border, tile, uw, uh, alpha);
        BufferedImage mesh = awtTexture.getCachedBorderTexture(key);
        if (mesh == null) {

            Color color = Color.WHITE;
            BufferedImage source = awtTexture.getBufferedImage(textureRegionTexture.getWidth(), textureRegionTexture.getHeight(), alpha, color);

            BufferedImageBuilder builder = new BufferedImageBuilder(source, textureSize, region, region.size());

            float topTex = (float) border.getTop() / textureSize.y;
            float leftTex = (float) border.getLeft() / textureSize.x;
            float bottomTex = 1f - (float) border.getBottom() / textureSize.y;
            float rightTex = 1f - (float) border.getRight() / textureSize.x;
            int centerHoriz = region.width() - border.getTotalWidth();
            int centerVert = region.height() - border.getTotalHeight();

            float top = (float) border.getTop() / region.height();
            float left = (float) border.getLeft() / region.width();
            float bottom = 1f - (float) border.getBottom() / region.height();
            float right = 1f - (float) border.getRight() / region.width();

            if (border.getTop() != 0) {
                if (border.getLeft() != 0) {
                    addRectPoly(builder, 0, 0, left, top, 0, 0, leftTex, topTex);
                }
                if (tile) {
                    addTiles(builder, Rect2i.createFromMinAndSize(border.getLeft(), 0, centerHoriz, border.getTop()), Rect2f.createFromMinAndMax(left, 0, right, top),
                            new Vector2i(textureSize.x - border.getTotalWidth(), border.getTop()),
                            Rect2f.createFromMinAndMax(leftTex, 0, rightTex, topTex));
                } else {
                    addRectPoly(builder, left, 0, right, top, leftTex, 0, rightTex, topTex);
                }
                if (border.getRight() != 0) {
                    addRectPoly(builder, right, 0, 1, top, rightTex, 0, 1, topTex);
                }
            }

            if (border.getLeft() != 0) {
                if (tile) {
                    addTiles(builder, Rect2i.createFromMinAndSize(0, border.getTop(), border.getLeft(), centerVert), Rect2f.createFromMinAndMax(0, top, left, bottom),
                            new Vector2i(border.getLeft(), textureSize.y - border.getTotalHeight()),
                            Rect2f.createFromMinAndMax(0, topTex, leftTex, bottomTex));
                } else {
                    addRectPoly(builder, 0, top, left, bottom, 0, topTex, leftTex, bottomTex);
                }
            }

            if (tile) {
                addTiles(builder, Rect2i.createFromMinAndSize(border.getLeft(), border.getTop(), centerHoriz, centerVert),
                        Rect2f.createFromMinAndMax(left, top, right, bottom),
                        new Vector2i(textureSize.x - border.getTotalWidth(), textureSize.y - border.getTotalHeight()),
                        Rect2f.createFromMinAndMax(leftTex, topTex, rightTex, bottomTex));
            } else {
                addRectPoly(builder, left, top, right, bottom, leftTex, topTex, rightTex, bottomTex);
            }

            if (border.getRight() != 0) {
                if (tile) {
                    addTiles(builder, Rect2i.createFromMinAndSize(region.width() - border.getRight(), border.getTop(), border.getRight(), centerVert),
                            Rect2f.createFromMinAndMax(right, top, 1, bottom),
                            new Vector2i(border.getRight(), textureSize.y - border.getTotalHeight()),
                            Rect2f.createFromMinAndMax(rightTex, topTex, 1, bottomTex));
                } else {
                    addRectPoly(builder, right, top, 1, bottom, rightTex, topTex, 1, bottomTex);
                }
            }

            if (border.getBottom() != 0) {
                if (border.getLeft() != 0) {
                    addRectPoly(builder, 0, bottom, left, 1, 0, bottomTex, leftTex, 1);
                }
                if (tile) {
                    addTiles(builder, Rect2i.createFromMinAndSize(border.getLeft(), region.height() - border.getBottom(), centerHoriz, border.getBottom()),
                            Rect2f.createFromMinAndMax(left, bottom, right, 1),
                            new Vector2i(textureSize.x - border.getTotalWidth(), border.getBottom()),
                            Rect2f.createFromMinAndMax(leftTex, bottomTex, rightTex, 1));
                } else {
                    addRectPoly(builder, left, bottom, right, 1, leftTex, bottomTex, rightTex, 1);
                }
                if (border.getRight() != 0) {
                    addRectPoly(builder, right, bottom, 1, 1, rightTex, bottomTex, 1, 1);
                }
            }

            mesh = builder.build();
            awtTexture.putCachedBorderTexture(key, mesh);
        }

        Rect2i sourceRegion = Rect2i.createFromMinAndSize(mesh.getMinX(), mesh.getMinY(), mesh.getWidth(), mesh.getHeight());
        drawImageInternal(mesh, region, sourceRegion);
    }

    private void addRectPoly(BufferedImageBuilder builder,
                             float minX, float minY, float maxX, float maxY,
                             float texMinX, float texMinY, float texMaxX, float texMaxY) {
        builder.addSubTextureRegion(
                minX, minY, maxX, maxY,
                texMinX, texMinY, texMaxX, texMaxY);
    }

    private void addTiles(BufferedImageBuilder builder, Rect2i drawRegion, Rect2f subDrawRegion, Vector2i textureSize, Rect2f subTextureRegion) {
        int tileW = textureSize.x;
        int tileH = textureSize.y;
        int horizTiles = TeraMath.fastAbs((drawRegion.width() - 1) / tileW) + 1;
        int vertTiles = TeraMath.fastAbs((drawRegion.height() - 1) / tileH) + 1;

        int offsetX = (drawRegion.width() - horizTiles * tileW) / 2;
        int offsetY = (drawRegion.height() - vertTiles * tileH) / 2;

        for (int tileY = 0; tileY < vertTiles; tileY++) {
            for (int tileX = 0; tileX < horizTiles; tileX++) {
                int left = offsetX + tileW * tileX;
                int top = offsetY + tileH * tileY;

                float vertLeft = subDrawRegion.minX() + subDrawRegion.width() * Math.max((float) left / drawRegion.width(), 0);
                float vertTop = subDrawRegion.minY() + subDrawRegion.height() * Math.max((float) top / drawRegion.height(), 0);
                float vertRight = subDrawRegion.minX() + subDrawRegion.width() * Math.min((float) (left + tileW) / drawRegion.width(), 1);
                float vertBottom = subDrawRegion.minY() + subDrawRegion.height() * Math.min((float) (top + tileH) / drawRegion.height(), 1);
                float texCoordLeft = subTextureRegion.minX() + subTextureRegion.width() * (Math.max(left, 0) - left) / tileW;
                float texCoordTop = subTextureRegion.minY() + subTextureRegion.height() * (Math.max(top, 0) - top) / tileH;
                float texCoordRight = subTextureRegion.minX() + subTextureRegion.width() * (Math.min(left + tileW, drawRegion.width()) - left) / tileW;
                float texCoordBottom = subTextureRegion.minY() + subTextureRegion.height() * (Math.min(top + tileH, drawRegion.height()) - top) / tileH;

                addRectPoly(builder, vertLeft, vertTop, vertRight, vertBottom, texCoordLeft, texCoordTop, texCoordRight, texCoordBottom);
            }
        }
    }
}
