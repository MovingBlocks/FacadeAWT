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

import java.awt.Color;
import java.awt.Graphics;

import javax.vecmath.Point2i;

import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.engine.subsystem.awt.cities.Sector;
import org.terasology.engine.subsystem.awt.cities.Sectors;
import org.terasology.engine.subsystem.awt.cities.SwingRasterizer;
import org.terasology.engine.subsystem.awt.devices.AwtDisplayDevice;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.math.Vector2i;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.cameras.Camera;
import org.terasology.world.WorldProvider;
import org.terasology.world.chunks.ChunkProvider;

public class CityWorldRenderer extends AbstractWorldRenderer {

    public CityWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        super(worldProvider, chunkProvider, localPlayerSystem);
    }

    public void renderWorld(Camera camera) {
        AwtDisplayDevice displayDevice = (AwtDisplayDevice) CoreRegistry.get(DisplayDevice.class);
        Graphics drawGraphics = displayDevice.getDrawGraphics();
        drawGraphics.setColor(Color.LIGHT_GRAY);
        int width = displayDevice.mainFrame.getWidth();
        int height = displayDevice.mainFrame.getHeight();
        drawGraphics.fillRect(0, 0, width, height);

        int scale = 2;

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
}
