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
package org.terasology.engine.subsystem.awt.devices;

import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Map;
import java.util.Queue;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.input.ButtonState;
import org.terasology.input.MouseInput;
import org.terasology.input.device.MouseAction;
import org.terasology.input.device.MouseDevice;
import org.terasology.math.geom.Vector2i;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

public class AwtMouseDevice implements MouseDevice {
    private static final Logger logger = LoggerFactory.getLogger(AwtMouseDevice.class);

    private int mouseX;
    private int mouseY;
    private int prevMouseX;
    private int prevMouseY;

    private Map<MouseInput, Boolean> isDownByMouseButtonNumber = Maps.newHashMap();     // capacity 16
    
    private Queue<MouseAction> inputQueue = Queues.newArrayDeque();
    private Object inputQueueLock = new Object();

    public AwtMouseDevice(JFrame window) {
        
        final Container contentPane = window.getContentPane();
        contentPane.addMouseMotionListener(new MouseMotionListener() {
            
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });

        contentPane.addMouseWheelListener(new MouseWheelListener() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int wheelRotation = e.getWheelRotation();

                MouseInput input = (wheelRotation < 0) ?
                    MouseInput.WHEEL_UP :
                    MouseInput.WHEEL_DOWN;
                
                // TODO: need to determine if wheel button is pressed
                MouseAction event = new MouseAction(input, Math.abs(wheelRotation), getPosition());
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
            }
        });

        contentPane.addMouseListener(new MouseAdapter() {

            private MouseInput getMouseButtonNumberForMouseEvent(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    return MouseInput.MOUSE_LEFT;
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    return MouseInput.MOUSE_RIGHT;
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    return MouseInput.MOUSE_3;
                } else {
                    logger.warn("Unsupported mouse button pressed.");
                    return MouseInput.NONE;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                MouseInput input = getMouseButtonNumberForMouseEvent(e);
                MouseAction event = new MouseAction(input, ButtonState.UP, getPosition());
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
                isDownByMouseButtonNumber.put(input, Boolean.FALSE);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                MouseInput input = getMouseButtonNumberForMouseEvent(e);
                MouseAction event = new MouseAction(input, ButtonState.DOWN, getPosition());
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
                isDownByMouseButtonNumber.put(input, Boolean.TRUE);
            }
        });
    }

    @Override
    public Vector2i getPosition() {
        return new Vector2i(mouseX, mouseY);
    }

    public void update(float delta) {
        prevMouseX = mouseX;
        prevMouseY = mouseY;
    }

    @Override
    public Vector2i getDelta() {
        return new Vector2i(mouseX - prevMouseX, mouseY - prevMouseY);
    }

    @Override
    public boolean isButtonDown(int buttonNumber) {
        Boolean isDown = isDownByMouseButtonNumber.get(buttonNumber);
        if (null == isDown) {
            return false;
        }

        return isDown.booleanValue();
    }

    @Override
    public Queue<MouseAction> getInputQueue() {
        Queue<MouseAction> oldInputQueue;
        synchronized (inputQueueLock) {
            oldInputQueue = inputQueue;
            inputQueue = Queues.newArrayDeque();
        }
        return oldInputQueue;
    }

    @Override
    public boolean isVisible() {
        return true;
        // TODO: at some point, we probably need to do a mouse grab for awt
        // return !Mouse.isGrabbed();
    }

    /**
     * Specifies if the mouse is grabbed and there is thus no mouse cursor that can get to a border.
     */
    public void setGrabbed(boolean grabbed) {
    	// TODO: NEW FUNCTIONALITY needed
    }
}
