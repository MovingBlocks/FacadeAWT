/*
 * Copyright 2013 MovingBlocks
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

package org.terasology.engine.subsystem.awt.cities;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.terasology.math.TeraMath;
import org.terasology.world.chunks.ChunkConstants;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Uses world generation code to draw on a swing canvas
 * @author Martin Steiger
 */
public class SwingRasterizer {
    private final NoiseHeightMap heightMap;
    private final Map<BlockTypes, Color> themeMap = Maps.newConcurrentMap();

    public SwingRasterizer(String seed) {
        heightMap = new NoiseHeightMap();
        heightMap.setSeed(seed);

        themeMap.put(BlockTypes.AIR, new Color(0, 0, 0, 0));
    }

    public void drawAccurately(Graphics g, Sector sector) {
        int chunkSizeX = ChunkConstants.SIZE_X * 4;
        int chunkSizeZ = ChunkConstants.SIZE_Z * 4;

        int chunksX = Sector.SIZE / chunkSizeX;
        int chunksZ = Sector.SIZE / chunkSizeZ;

        Function<BlockTypes, Color> colorFunc = new Function<BlockTypes, Color>() {

            @Override
            public Color apply(BlockTypes input) {
                Color color = themeMap.get(input);

                if (color == null) {
                    color = Color.GRAY;
                }

                return color;
            }
        };

        for (int cz = 0; cz < chunksZ; cz++) {
            for (int cx = 0; cx < chunksX; cx++) {
                int wx = sector.getCoords().x * Sector.SIZE + cx * chunkSizeX;
                int wz = sector.getCoords().y * Sector.SIZE + cz * chunkSizeZ;

                if (g.hitClip(wx, wz, chunkSizeX, chunkSizeZ)) {

                    BufferedImage image = new BufferedImage(chunkSizeX, chunkSizeZ, BufferedImage.TYPE_INT_ARGB);
                    Brush brush = new SwingBrush(wx, wz, image, colorFunc);

                    HeightMap cachedHm = HeightMaps.caching(heightMap, brush.getAffectedArea(), 8);
                    TerrainInfo ti = new TerrainInfo(cachedHm);

                    drawBackground(image, wx, wz, ti);

                    int ix = wx;
                    int iy = wz;
                    g.drawImage(image, ix, iy, null);
                }
            }
        }
    }

    //    private void drawNoiseBackgroundFast(Graphics g, Sector sector) {
    //        int scale = 4;
    //        int maxHeight = 20;
    //        int size = Sector.SIZE / scale;
    //        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    //
    //        for (int y = 0; y < size; y++) {
    //            for (int x = 0; x < size; x++) {
    //                int gx = sector.getCoords().x * Sector.SIZE + x * scale;
    //                int gz = sector.getCoords().y * Sector.SIZE + y * scale;
    //                int height = heightMap.apply(gx, gz);
    //                int b = TeraMath.clamp(255 - (maxHeight - height) * 5, 0, 255);
    //
    //                Color c;
    //                if (height <= 2) {
    //                    c = Color.BLUE; 
    //                } else {
    //                    c = new Color(b, b, b);
    //                }
    //                
    //                img.setRGB(x, y, c.getRGB());
    //            }
    //        }
    //
    //        int offX = Sector.SIZE * sector.getCoords().x;
    //        int offZ = Sector.SIZE * sector.getCoords().y;
    //
    //        g.drawImage(img, offX, offZ, Sector.SIZE, Sector.SIZE, null);
    //    }

    private void drawBackground(BufferedImage image, int wx, int wz, TerrainInfo ti) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxHeight = 20;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int gx = wx + x;
                int gz = wz + z;
                int y = ti.getHeightMap().apply(gx, gz);
                int b = TeraMath.clamp(255 - (maxHeight - y) * 5, 0, 255);

                Color c;
                if (y <= 2) {
                    c = Color.BLUE;
                } else {
                    c = new Color(b, b, b);
                }

                image.setRGB(x, z, c.getRGB());
            }
        }
    }

}
