/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.engine.subsystem.awt.renderer;

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import org.terasology.math.Rect2i;
import org.terasology.math.Vector2i;

/**
 * @author Immortius
 */
public class BufferedImageBuilder {
    BufferedImage sourceBufferedImage;
    BufferedImage destinationBufferedImage;
    Vector2i sourceSize;
    Vector2i drawingSize;

    public BufferedImageBuilder(BufferedImage source, Vector2i sourceSize, Rect2i drawingRegion, Vector2i drawingSize) {
        this.sourceBufferedImage = source;
        this.sourceSize = sourceSize;
        this.drawingSize = drawingSize;

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

        this.destinationBufferedImage = gc.createCompatibleImage(
                drawingRegion.width(),
                drawingRegion.height(),
                Transparency.TRANSLUCENT);
    }

    public BufferedImageBuilder addSubTextureRegion(
                                                    float minX, float minY, float maxX, float maxY,
                                                    float texMinX, float texMinY, float texMaxX, float texMaxY) {

        intDrawImage((int) (minX * drawingSize.x), (int) (minY * drawingSize.y), (int) (maxX * drawingSize.x), (int) (maxY * drawingSize.y),
                (int) (texMinX * sourceSize.x), (int) (texMinY * sourceSize.y), (int) (texMaxX * sourceSize.x), (int) (texMaxY * sourceSize.y));

        return this;
    }

    private void intDrawImage(int minX, int minY, int maxX, int maxY, int texMinX, int texMinY, int texMaxX, int texMaxY) {
        ImageObserver observer = null;

        Graphics g = destinationBufferedImage.getGraphics();
        g.drawImage(sourceBufferedImage,
                minX, minY, maxX, maxY,
                texMinX, texMinY, texMaxX, texMaxY,
                observer);
    }

    public BufferedImage build() {
        return destinationBufferedImage;
    }
}
