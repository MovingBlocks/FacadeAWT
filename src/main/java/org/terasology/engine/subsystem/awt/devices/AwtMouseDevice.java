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

import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.input.ButtonState;
import org.terasology.input.InputType;
import org.terasology.input.device.InputAction;
import org.terasology.input.device.MouseDevice;
import org.terasology.math.Vector2i;

import com.google.common.collect.Queues;

public class AwtMouseDevice implements MouseDevice {
    private static final Logger logger = LoggerFactory.getLogger(AwtMouseDevice.class);

    private int lastMouseX;
    private int lastMouseY;
    private int deltaMouseX;
    private int deltaMouseY;
    private JFrame mainWindow;

    private Map<Integer, Boolean> isDownByMouseButtonNumber = new HashMap<Integer, Boolean>(16);
    private Queue<InputAction> inputQueue = Queues.newArrayDeque();
    private Object inputQueueLock = new Object();

    public AwtMouseDevice(JFrame mainWindow) {
        this.mainWindow = mainWindow;
        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
        lastMouseX = (int) mouseLocation.getX();
        lastMouseY = (int) mouseLocation.getY();

        mainWindow.addMouseWheelListener(new MouseWheelListener() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int wheelRotation = e.getWheelRotation();
                //                if (Mouse.getEventDWheel() != 0) {
                //                    int id = (Mouse.getEventDWheel() > 0) ? 1 : -1;

                // TODO:  need to determine if wheel button is pressed
                int id = 1;
                InputAction event = new InputAction(InputType.MOUSE_WHEEL.getInput(id), id * wheelRotation / 120);
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
                //                }
            }
        });

        mainWindow.addMouseListener(new MouseListener() {

            private int getMouseButtonNumberForMouseEvent(MouseEvent e) {
                int buttonNumber = -1;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    buttonNumber = 0;
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    buttonNumber = 1;
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    buttonNumber = 2;
                } else {
                    logger.warn("Unsupported mouse button pressed.");
                }
                return buttonNumber;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int buttonNumber = getMouseButtonNumberForMouseEvent(e);
                InputAction event = new InputAction(InputType.MOUSE_BUTTON.getInput(buttonNumber), ButtonState.UP);
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
                isDownByMouseButtonNumber.put(buttonNumber, false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int buttonNumber = getMouseButtonNumberForMouseEvent(e);
                InputAction event = new InputAction(InputType.MOUSE_BUTTON.getInput(buttonNumber), ButtonState.DOWN);
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
                isDownByMouseButtonNumber.put(buttonNumber, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseClicked(MouseEvent e) {
            }
        });
    }

    @Override
    public Vector2i getPosition() {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        Point mouseLocation = pointerInfo.getLocation();
        //        logger.info("locationBefore=" + mouseLocation);
        SwingUtilities.convertPointFromScreen(mouseLocation, mainWindow);
        Insets insets = mainWindow.getInsets();
        Vector2i v = new Vector2i((int) mouseLocation.getX() - (insets.left + insets.right),
                (int) mouseLocation.getY() - (insets.top + insets.bottom));
        //        logger.info(String.valueOf(v));
        return v;
    }

    public void update(float delta) {
        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
        int newMouseX = (int) mouseLocation.getX();
        int newMouseY = (int) mouseLocation.getY();
        deltaMouseX = newMouseX - lastMouseX;
        deltaMouseY = newMouseY - lastMouseY;
    }

    @Override
    public Vector2i getDelta() {
        return new Vector2i(deltaMouseX, deltaMouseY);
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
    public Queue<InputAction> getInputQueue() {
        Queue<InputAction> oldInputQueue;
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

}
