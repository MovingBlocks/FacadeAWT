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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;
import java.util.Queue;

import javax.swing.JFrame;

import org.lwjgl.input.Keyboard;
import org.terasology.input.ButtonState;
import org.terasology.input.InputType;
import org.terasology.input.Keyboard.Key;
import org.terasology.input.Keyboard.KeyId;
import org.terasology.input.device.InputAction;
import org.terasology.input.device.KeyboardDevice;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

public class AwtKeyboardDevice implements KeyboardDevice {

    public enum KeyLookupTable {
        NONE(KeyId.NONE, KeyEvent.VK_UNDEFINED),
        ESCAPE(KeyId.ESCAPE, KeyEvent.VK_ESCAPE),
        KEY_1(KeyId.KEY_1, KeyEvent.VK_1),
        KEY_2(KeyId.KEY_2, KeyEvent.VK_2),
        KEY_3(KeyId.KEY_3, KeyEvent.VK_3),
        KEY_4(KeyId.KEY_4, KeyEvent.VK_4),
        KEY_5(KeyId.KEY_5, KeyEvent.VK_5),
        KEY_6(KeyId.KEY_6, KeyEvent.VK_6),
        KEY_7(KeyId.KEY_7, KeyEvent.VK_7),
        KEY_8(KeyId.KEY_8, KeyEvent.VK_8),
        KEY_9(KeyId.KEY_9, KeyEvent.VK_9),
        KEY_0(KeyId.KEY_0, KeyEvent.VK_0),
        MINUS(KeyId.MINUS, KeyEvent.VK_MINUS),
        EQUALS(KeyId.EQUALS, KeyEvent.VK_EQUALS),
        BACKSPACE(KeyId.BACKSPACE, KeyEvent.VK_BACK_SPACE),
        TAB(KeyId.TAB, KeyEvent.VK_TAB),
        Q(KeyId.Q, KeyEvent.VK_Q),
        W(KeyId.W, KeyEvent.VK_W),
        E(KeyId.E, KeyEvent.VK_E),
        R(KeyId.R, KeyEvent.VK_R),
        T(KeyId.T, KeyEvent.VK_T),
        Y(KeyId.Y, KeyEvent.VK_Y),
        U(KeyId.U, KeyEvent.VK_U),
        I(KeyId.I, KeyEvent.VK_I),
        O(KeyId.O, KeyEvent.VK_O),
        P(KeyId.P, KeyEvent.VK_P),
        LEFT_BRACKET(KeyId.LEFT_BRACKET, KeyEvent.VK_OPEN_BRACKET),
        RIGHT_BRACKET(KeyId.RIGHT_BRACKET, KeyEvent.VK_CLOSE_BRACKET),
        ENTER(KeyId.ENTER, KeyEvent.VK_ENTER),
        A(KeyId.A, KeyEvent.VK_A),
        S(KeyId.S, KeyEvent.VK_S),
        D(KeyId.D, KeyEvent.VK_D),
        F(KeyId.F, KeyEvent.VK_F),
        G(KeyId.G, KeyEvent.VK_G),
        H(KeyId.H, KeyEvent.VK_H),
        J(KeyId.J, KeyEvent.VK_J),
        K(KeyId.K, KeyEvent.VK_K),
        L(KeyId.L, KeyEvent.VK_L),
        SEMICOLON(KeyId.SEMICOLON, KeyEvent.VK_SEMICOLON),
        GRAVE(KeyId.GRAVE, KeyEvent.VK_BACK_QUOTE),
        BACKSLASH(KeyId.BACKSLASH, KeyEvent.VK_BACK_SLASH),
        Z(KeyId.Z, KeyEvent.VK_Z),
        X(KeyId.X, KeyEvent.VK_X),
        C(KeyId.C, KeyEvent.VK_C),
        V(KeyId.V, KeyEvent.VK_V),
        B(KeyId.B, KeyEvent.VK_B),
        N(KeyId.N, KeyEvent.VK_N),
        M(KeyId.M, KeyEvent.VK_M),
        COMMA(KeyId.COMMA, KeyEvent.VK_COMMA),
        PERIOD(KeyId.PERIOD, KeyEvent.VK_PERIOD),
        SLASH(KeyId.SLASH, KeyEvent.VK_SLASH),
        NUMPAD_MULTIPLY(KeyId.NUMPAD_MULTIPLY, KeyEvent.VK_MULTIPLY),
        SPACE(KeyId.SPACE, KeyEvent.VK_SPACE),
        CAPS_LOCK(KeyId.CAPS_LOCK, KeyEvent.VK_CAPS_LOCK),
        F1(KeyId.F1, KeyEvent.VK_F1),
        F2(KeyId.F2, KeyEvent.VK_F2),
        F3(KeyId.F3, KeyEvent.VK_F3),
        F4(KeyId.F4, KeyEvent.VK_F4),
        F5(KeyId.F5, KeyEvent.VK_F5),
        F6(KeyId.F6, KeyEvent.VK_F6),
        F7(KeyId.F7, KeyEvent.VK_F7),
        F8(KeyId.F8, KeyEvent.VK_F8),
        F9(KeyId.F9, KeyEvent.VK_F9),
        F10(KeyId.F10, KeyEvent.VK_F10),
        NUM_LOCK(KeyId.NUM_LOCK, KeyEvent.VK_NUM_LOCK),
        SCROLL_LOCK(KeyId.SCROLL_LOCK, KeyEvent.VK_SCROLL_LOCK),
        NUMPAD_7(KeyId.NUMPAD_7, KeyEvent.VK_NUMPAD7),
        NUMPAD_8(KeyId.NUMPAD_8, KeyEvent.VK_NUMPAD8),
        NUMPAD_9(KeyId.NUMPAD_9, KeyEvent.VK_NUMPAD9),
        NUMPAD_MINUS(KeyId.NUMPAD_MINUS, KeyEvent.VK_SUBTRACT), // maybe?
        NUMPAD_4(KeyId.NUMPAD_4, KeyEvent.VK_NUMPAD4),
        NUMPAD_5(KeyId.NUMPAD_5, KeyEvent.VK_NUMPAD5),
        NUMPAD_6(KeyId.NUMPAD_6, KeyEvent.VK_NUMPAD6),
        NUMPAD_PLUS(KeyId.NUMPAD_PLUS, KeyEvent.VK_ADD), // maybe?
        NUMPAD_1(KeyId.NUMPAD_1, KeyEvent.VK_NUMPAD1),
        NUMPAD_2(KeyId.NUMPAD_2, KeyEvent.VK_NUMPAD2),
        NUMPAD_3(KeyId.NUMPAD_3, KeyEvent.VK_NUMPAD3),
        NUMPAD_0(KeyId.NUMPAD_0, KeyEvent.VK_NUMPAD0),
        NUMPAD_PERIOD(KeyId.NUMPAD_PERIOD, KeyEvent.VK_DECIMAL), // maybe?
        F11(KeyId.F11, KeyEvent.VK_F11),
        F12(KeyId.F12, KeyEvent.VK_F12),
        F13(KeyId.F13, KeyEvent.VK_F13),
        F14(KeyId.F14, KeyEvent.VK_F14),
        F15(KeyId.F15, KeyEvent.VK_F15),
        F16(KeyId.F16, KeyEvent.VK_F16),
        F17(KeyId.F17, KeyEvent.VK_F17),
        F18(KeyId.F18, KeyEvent.VK_F18),
        KANA(KeyId.KANA, KeyEvent.VK_KANA), // Japanese Keyboard key (for switching between roman and japanese characters?
        F19(KeyId.F19, KeyEvent.VK_F19),
        CONVERT(KeyId.CONVERT, KeyEvent.VK_CONVERT), // Japanese Keyboard key (for converting Hiragana characters to Kanji?)
        CIRCUMFLEX(KeyId.CIRCUMFLEX, KeyEvent.VK_CIRCUMFLEX), // Japanese keyboard
        AT(KeyId.AT, KeyEvent.VK_AT), // (NEC PC98)
        COLON(KeyId.COLON, KeyEvent.VK_COLON), // (NEC PC98)
        UNDERLINE(KeyId.UNDERLINE, KeyEvent.VK_UNDERSCORE), // (NEC PC98)
        KANJI(KeyId.KANJI, KeyEvent.VK_KANJI), // (Japanese keyboard)
        STOP(KeyId.STOP, KeyEvent.VK_STOP), // (NEC PC98)
        NUMPAD_DIVIDE(KeyId.NUMPAD_DIVIDE, KeyEvent.VK_DIVIDE), // maybe?
        PRINT_SCREEN(KeyId.PRINT_SCREEN, KeyEvent.VK_PRINTSCREEN),
        PAUSE(KeyId.PAUSE, KeyEvent.VK_PAUSE),
        HOME(KeyId.HOME, KeyEvent.VK_HOME),
        UP(KeyId.UP, KeyEvent.VK_UP),
        PAGE_UP(KeyId.PAGE_UP, KeyEvent.VK_PAGE_UP),
        LEFT(KeyId.LEFT, KeyEvent.VK_LEFT),
        RIGHT(KeyId.RIGHT, KeyEvent.VK_RIGHT),
        END(KeyId.END, KeyEvent.VK_END),
        DOWN(KeyId.DOWN, KeyEvent.VK_DOWN),
        PAGE_DOWN(KeyId.PAGE_DOWN, KeyEvent.VK_PAGE_DOWN),
        INSERT(KeyId.INSERT, KeyEvent.VK_INSERT),
        DELETE(KeyId.DELETE, KeyEvent.VK_DELETE),
        CLEAR(KeyId.CLEAR, KeyEvent.VK_CLEAR), // (Mac)

        // In case this is a one-key keyboard of some kind
        CTRL(KeyId.LEFT_CTRL, KeyEvent.VK_CONTROL),
        ALT(KeyId.LEFT_ALT, KeyEvent.VK_ALT),
        META(KeyId.LEFT_META, KeyEvent.VK_META),
        WINDOWS(KeyId.LEFT_META, KeyEvent.VK_WINDOWS);

        /*
                APOSTROPHE(KeyId.APOSTROPHE, KeyEvent.VK_APOSTROPHE),
                
                // should be taken care of in left/right location
                LEFT_CTRL(KeyId.LEFT_CTRL, KeyEvent.VK_LEFT_CTRL),
                RIGHT_CTRL(KeyId.RIGHT_CTRL, KeyEvent.VK_RIGHT_CTRL),
                
                LEFT_SHIFT(KeyId.LEFT_SHIFT, KeyEvent.VK_LEFT_SHIFT),
                RIGHT_SHIFT(KeyId.RIGHT_SHIFT, KeyEvent.VK_RIGHT_SHIFT),
                
                LEFT_ALT(KeyId.LEFT_ALT, KeyEvent.VK_LEFT_ALT),
                RIGHT_ALT(KeyId.RIGHT_ALT, KeyEvent.VK_RIGHT_ALT),

                LEFT_META(KeyId.LEFT_META, KeyEvent.VK_LEFT_META), // Left Windows/Option key
                RIGHT_META(KeyId.RIGHT_META, KeyEvent.VK_RIGHT_META), // Right Windows/Option key
                
                // should be taken care of in numpad location
                NUMPAD_EQUALS(KeyId.NUMPAD_EQUALS, KeyEvent.VK_NUMPADEQUALS),
                NUMPAD_ENTER(KeyId.NUMPAD_ENTER, KeyEvent.VK_NUMPADENTER),
                NUMPAD_COMMA(KeyId.NUMPAD_COMMA, KeyEvent.VK_NUMPADCOMMA), // (NEC PC98)
                
                // unsupported
                NOCONVERT(KeyId.NOCONVERT, KeyEvent.VK_NOCONVERT), // Japanese Keyboard key
                YEN(KeyId.YEN, KeyEvent.VK_YEN), // Japanese keyboard key for yen
                AX(KeyId.AX, KeyEvent.VK_AX), // (Japan AX)
                UNLABELED(KeyId.UNLABELED, KeyEvent.VK_UNLABELED), // (J3100) (a mystery button?)
                SECTION(KeyId.SECTION, KeyEvent.VK_SECTION),
                FUNCTION(KeyId.FUNCTION, KeyEvent.VK_FUNCTION),
                APPS(KeyId.APPS, KeyEvent.VK_APPS),
                POWER(KeyId.POWER, KeyEvent.VK_POWER),
                SLEEP(KeyId.SLEEP, KeyEvent.VK_SLEEP),
             
         */

        private static Map<Integer, Integer> lookupByJavaKeyEventCode;
        private static Map<Integer, Integer> lookupById;

        private int id;
        private int javaKeyEventCode;

        static {
            lookupByJavaKeyEventCode = Maps.newHashMapWithExpectedSize(Key.values().length);
            lookupById = Maps.newHashMapWithExpectedSize(Key.values().length);
            for (KeyLookupTable key : KeyLookupTable.values()) {
                lookupByJavaKeyEventCode.put(key.getJavaKeyEventCode(), key.getId());
                lookupById.put(key.getId(), key.getJavaKeyEventCode());
            }
        }

        private KeyLookupTable(int id, int javaKeyEventCode) {
            this.id = id;
            this.javaKeyEventCode = javaKeyEventCode;
        }

        public int getId() {
            return id;
        }

        public int getJavaKeyEventCode() {
            return javaKeyEventCode;
        }

        public static Integer findById(int id) {
            Integer result = lookupById.get(id);
            if (result == null) {
                return null;
            }
            return result;
        }

        public static Integer findByJavaKeyEventCode(int code) {
            Integer result = lookupByJavaKeyEventCode.get(code);
            if (result == null) {
                return null;
            }
            return result;
        }

        public static Integer findByJavaKeyEvent(KeyEvent e) {
            int keyCode = e.getKeyCode();
            int location = e.getKeyLocation();
            switch (location) {
                case KeyEvent.KEY_LOCATION_LEFT:
                    switch (keyCode) {
                        case KeyEvent.VK_SHIFT:
                            return KeyId.LEFT_SHIFT;
                        case KeyEvent.VK_CONTROL:
                            return KeyId.LEFT_CTRL;
                        case KeyEvent.VK_ALT:
                            return KeyId.LEFT_ALT;
                        case KeyEvent.VK_WINDOWS:
                        case KeyEvent.VK_META:
                            return KeyId.LEFT_META;
                        default:
                            return KeyId.NONE;
                    }
                case KeyEvent.KEY_LOCATION_RIGHT:
                    switch (keyCode) {
                        case KeyEvent.VK_SHIFT:
                            return KeyId.RIGHT_SHIFT;
                        case KeyEvent.VK_CONTROL:
                            return KeyId.RIGHT_CTRL;
                        case KeyEvent.VK_ALT:
                            return KeyId.RIGHT_ALT;
                        case KeyEvent.VK_META:
                        case KeyEvent.VK_WINDOWS:
                            return KeyId.RIGHT_META;
                        default:
                            return KeyId.NONE;
                    }
                case KeyEvent.KEY_LOCATION_NUMPAD:
                    switch (keyCode) {
                        case KeyEvent.VK_0:
                        case KeyEvent.VK_NUMPAD0:
                            return KeyId.NUMPAD_0;
                        case KeyEvent.VK_1:
                        case KeyEvent.VK_NUMPAD1:
                            return KeyId.NUMPAD_1;
                        case KeyEvent.VK_2:
                        case KeyEvent.VK_NUMPAD2:
                            return KeyId.NUMPAD_2;
                        case KeyEvent.VK_3:
                        case KeyEvent.VK_NUMPAD3:
                            return KeyId.NUMPAD_3;
                        case KeyEvent.VK_4:
                        case KeyEvent.VK_NUMPAD4:
                            return KeyId.NUMPAD_4;
                        case KeyEvent.VK_5:
                        case KeyEvent.VK_NUMPAD5:
                            return KeyId.NUMPAD_5;
                        case KeyEvent.VK_6:
                        case KeyEvent.VK_NUMPAD6:
                            return KeyId.NUMPAD_6;
                        case KeyEvent.VK_7:
                        case KeyEvent.VK_NUMPAD7:
                            return KeyId.NUMPAD_7;
                        case KeyEvent.VK_8:
                        case KeyEvent.VK_NUMPAD8:
                            return KeyId.NUMPAD_8;
                        case KeyEvent.VK_9:
                        case KeyEvent.VK_NUMPAD9:
                            return KeyId.NUMPAD_9;
                        case KeyEvent.VK_COMMA:
                            return KeyId.NUMPAD_COMMA;
                        case KeyEvent.VK_DIVIDE:
                        case KeyEvent.VK_SLASH:
                            return KeyId.NUMPAD_DIVIDE;
                        case KeyEvent.VK_ENTER:
                            return KeyId.NUMPAD_ENTER;
                        case KeyEvent.VK_EQUALS:
                            return KeyId.NUMPAD_EQUALS;
                        case KeyEvent.VK_MINUS:
                        case KeyEvent.VK_SUBTRACT:
                            return KeyId.NUMPAD_MINUS;
                        case KeyEvent.VK_MULTIPLY:
                        case KeyEvent.VK_ASTERISK:
                            return KeyId.NUMPAD_MULTIPLY;
                        case KeyEvent.VK_PERIOD:
                        case KeyEvent.VK_DECIMAL:
                            return KeyId.NUMPAD_PERIOD;
                        case KeyEvent.VK_ADD:
                        case KeyEvent.VK_PLUS:
                            return KeyId.NUMPAD_PLUS;
                        default:
                            return KeyId.NONE;
                    }
                case KeyEvent.KEY_LOCATION_UNKNOWN:
                    return KeyId.NONE;
                case KeyEvent.KEY_LOCATION_STANDARD:
                    Integer keyId = findByJavaKeyEventCode(keyCode);
                    if (null == keyId) {
                        return KeyId.NONE;
                    }
                    return keyId;
                default:
                    return KeyId.NONE;
            }
        }
    };

    private JFrame mainWindow;

    private Queue<InputAction> inputQueue = Queues.newArrayDeque();
    private Object inputQueueLock = new Object();
    private int keyDown;

    public AwtKeyboardDevice(JFrame mainFrame) {
        this.mainWindow = mainFrame;
        keyDown = -1;

        mainWindow.setFocusTraversalKeysEnabled(false);
        mainWindow.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
            }

            //            // TODO Auto-generated method stub
            //            ButtonState state;
            //            if (Keyboard.isRepeatEvent()) {
            //                state = ButtonState.REPEAT;
            //            } else {
            //                state = (Keyboard.getEventKeyState()) ? ButtonState.DOWN : ButtonState.UP;
            //            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyDown = -1;
                InputAction event = new InputAction(InputType.KEY.getInput(translateVTKeyCodeToTerasologyKeyCode(e)), ButtonState.UP, e.getKeyChar());
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
            }

            private int translateVTKeyCodeToTerasologyKeyCode(KeyEvent e) {
                Integer keyId = KeyLookupTable.findByJavaKeyEvent(e);
                if (null == keyId) {
                    // TODO: handle remaining keys
                    return Keyboard.KEY_NONE;
                }
                return keyId;
            }

            @Override
            public void keyPressed(KeyEvent e) {
                keyDown = translateVTKeyCodeToTerasologyKeyCode(e);
                InputAction event = new InputAction(InputType.KEY.getInput(keyDown), ButtonState.DOWN, e.getKeyChar());
                synchronized (inputQueueLock) {
                    inputQueue.add(event);
                }
            }

        });
    }

    @Override
    public boolean isKeyDown(int key) {
        return key == keyDown;
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
}
