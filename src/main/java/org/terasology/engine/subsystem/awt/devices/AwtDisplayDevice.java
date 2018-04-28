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
import org.terasology.context.Context;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.rendering.nui.layers.mainMenu.videoSettings.DisplayModeSetting;
import org.terasology.utilities.subscribables.AbstractSubscribable;

public class AwtDisplayDevice extends AbstractSubscribable implements DisplayDevice {

    private final JFrame mainFrame;
    private Context context;
    
    private boolean isCloseRequested;

    private Graphics drawGraphics;
    private int translatedX;
    private int translatedY;
    
    private DisplayModeSetting displayModeSetting;

    public AwtDisplayDevice(JFrame window, Context context) {
        this.mainFrame = window;
        this.context = context;

        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        mainFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                isCloseRequested = true;
            }
        });
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
            this.displayModeSetting = DisplayModeSetting.FULLSCREEN;
        } else {
            Config config = context.get(Config.class);
            RenderingConfig rc = config.getRendering();

            // proceed in non-full-screen mode
            Dimension size = new Dimension(rc.getWindowWidth(), rc.getWindowHeight());
            mainFrame.getContentPane().setSize(size);
            mainFrame.getContentPane().setPreferredSize(size);

            mainFrame.setLocation(rc.getWindowPosX(), rc.getWindowPosY());
            mainFrame.pack();
            mainFrame.setVisible(true);
            this.displayModeSetting = DisplayModeSetting.WINDOWED;
        }
    }

    @Override
    public void processMessages() {
    }

    @Override
    public boolean isHeadless() {
        // TODO: Needs to be a better way to avoid starting lwjgl systems
        return false;
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
    
    public int getWidth() {
        // mainFrame.getWidth() returns the height including borders
        return mainFrame.getContentPane().getWidth();
    }

    public int getHeight() {
        // mainFrame.getHeight() returns the height including borders
        return mainFrame.getContentPane().getHeight();
    }
    
    public void show() {
        mainFrame.getBufferStrategy().show();
        drawGraphics.dispose();
        drawGraphics = null;
    }

	@Override
	public boolean isFullscreen() {
		return displayModeSetting == DisplayModeSetting.FULLSCREEN;
	}

	@Override
	public void setDisplayModeSetting(DisplayModeSetting displayModeSetting) {
		this.displayModeSetting = displayModeSetting;
	}

	@Override
	public DisplayModeSetting getDisplayModeSetting() {
		return displayModeSetting;
	}

    @Override
    public void update() {
    }

	@Override
	public boolean hasFocus() {
		return mainFrame.hasFocus();
	}
}
