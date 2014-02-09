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
package org.terasology.engine.subsystem.awt.devices;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.registry.CoreRegistry;

public class AwtDisplayDevice implements DisplayDevice {

    public JFrame mainFrame;
    private boolean isCloseRequested;

    private Graphics drawGraphics;
    private int translatedX;
    private int translatedY;

    public AwtDisplayDevice(JFrame window) {
        this.mainFrame = window;

        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        mainFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                isCloseRequested = true;
            }
        });
    }

    @Override
    public boolean isActive() {
        return mainFrame.isActive();
    }

    @Override
    public boolean isCloseRequested() {
        return isCloseRequested;
    }

    @Override
    public void setFullscreen(boolean state) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        if (state && device.isFullScreenSupported()) {
            device.setFullScreenWindow(mainFrame);
        } else {
            Config config = CoreRegistry.get(Config.class);
            RenderingConfig rc = config.getRendering();

            // proceed in non-full-screen mode
            Dimension size = new Dimension(rc.getWindowWidth(), rc.getWindowHeight());
            mainFrame.getContentPane().setSize(size);
            mainFrame.getContentPane().setPreferredSize(size);

            mainFrame.setLocation(rc.getWindowPosX(), rc.getWindowPosY());
            mainFrame.pack();
            mainFrame.setVisible(true);
        }
    }

    @Override
    public void processMessages() {
    }

    @Override
    public boolean isHeadless() {
        // TODO: Needs to be a better way to avoid starting lwjgl systems
        return true;
    }

    @Override
    public void prepareToRender() {
        Graphics g = getDrawGraphics();
        g.clearRect(0, 0, mainFrame.getWidth(), mainFrame.getHeight());
    }

    public Graphics getDrawGraphics() {
        if (null == drawGraphics) {
            drawGraphics = mainFrame.getBufferStrategy().getDrawGraphics();
            Point frameLoc = mainFrame.getLocationOnScreen();
            Point viewLoc = mainFrame.getContentPane().getLocationOnScreen();
            translatedX = viewLoc.x - frameLoc.x;
            translatedY = viewLoc.y - frameLoc.y;
            drawGraphics.translate(translatedX, translatedY);
        }

        return drawGraphics;
    }

    public void show() {
        mainFrame.getBufferStrategy().show();
        drawGraphics.dispose();
        drawGraphics = null;
    }
}
