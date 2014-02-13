package org.terasology.awt.world.renderer;

import java.awt.Color;
import java.lang.annotation.Annotation;

import javax.vecmath.Vector3f;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.awt.input.binds.ScrollBackwardButton;
import org.terasology.awt.input.binds.ScrollDownButton;
import org.terasology.awt.input.binds.ScrollForwardButton;
import org.terasology.awt.input.binds.ScrollLeftButton;
import org.terasology.awt.input.binds.ScrollRightButton;
import org.terasology.awt.input.binds.ScrollUpButton;
import org.terasology.awt.input.binds.ToggleMapAxisButton;
import org.terasology.awt.input.binds.ZoomInButton;
import org.terasology.awt.input.binds.ZoomOutButton;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.ComponentSystem;
import org.terasology.input.ActivateMode;
import org.terasology.input.BindButtonEvent;
import org.terasology.input.BindableButton;
import org.terasology.input.Input;
import org.terasology.input.InputSystem;
import org.terasology.input.InputType;
import org.terasology.input.Keyboard;
import org.terasology.input.MouseInput;
import org.terasology.input.RegisterBindButton;
import org.terasology.input.events.MouseButtonEvent;
import org.terasology.logic.selection.ApplyBlockSelectionEvent;
import org.terasology.math.Region3i;
import org.terasology.math.Vector3i;
import org.terasology.network.ClientComponent;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureUtil;
import org.terasology.world.selection.BlockSelectionComponent;

public class WorldControlSystem implements ComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(WorldControlSystem.class);

    private BlockTileWorldRenderer renderer;

    public WorldControlSystem(BlockTileWorldRenderer renderer) {
        this.renderer = renderer;

        InputSystem inputSystem = CoreRegistry.get(InputSystem.class);
        RegisterBindButton info = new MyRegisterBindButton();

        Input input = new MyKeyInput(Keyboard.KeyId.NUMPAD_MINUS);
        manuallyBindKey(info, input, inputSystem, new ZoomOutButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_PLUS);
        manuallyBindKey(info, input, inputSystem, new ZoomInButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_0);
        manuallyBindKey(info, input, inputSystem, new ToggleMapAxisButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_4);
        manuallyBindKey(info, input, inputSystem, new ScrollLeftButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_6);
        manuallyBindKey(info, input, inputSystem, new ScrollRightButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_8);
        manuallyBindKey(info, input, inputSystem, new ScrollForwardButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_2);
        manuallyBindKey(info, input, inputSystem, new ScrollBackwardButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_9);
        manuallyBindKey(info, input, inputSystem, new ScrollUpButton());

        input = new MyKeyInput(Keyboard.KeyId.NUMPAD_1);
        manuallyBindKey(info, input, inputSystem, new ScrollDownButton());
    }

    private void manuallyBindKey(RegisterBindButton info, Input input, InputSystem inputSystem, BindButtonEvent bindButtonEvent) {
        SimpleUri bindUri = new SimpleUri("awt", info.id());
        BindableButton bindButton = inputSystem.registerBindButton(bindUri, info.description(), bindButtonEvent);
        bindButton.setMode(info.mode());
        bindButton.setRepeating(info.repeating());

        inputSystem.linkBindButtonToInput(input, bindUri);
    }

    @Override
    public void initialise() {
    }

    @Override
    public void shutdown() {
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onIncreaseOffsetButton(ZoomInButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.zoomIn();

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onDecreaseOffsetButton(ZoomOutButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.zoomOut();

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onToggleMinimapAxisButton(ToggleMapAxisButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.toggleAxis();

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollLeftButton(ScrollLeftButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(0, 0, -1);

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollRightButton(ScrollRightButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(0, 0, 1);

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollLeftButton(ScrollUpButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(0, 1, 0);

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollLeftButton(ScrollDownButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(0, -1, 0);

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollForwardButton(ScrollForwardButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(1, 0, 0);

            event.consume();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onScrollForwardButton(ScrollBackwardButton event, EntityRef entity) {
        if (event.isDown()) {
            renderer.changeCameraOffsetBy(-1, 0, 0);

            event.consume();
        }
    }

    public final class MyRegisterBindButton implements RegisterBindButton {
        @Override
        public Class<? extends Annotation> annotationType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean repeating() {
            return false;
        }

        @Override
        public ActivateMode mode() {
            return ActivateMode.BOTH;
        }

        @Override
        public String id() {
            return "changeMapAxis";
        }

        @Override
        public String description() {
            return "Change Map Axis";
        }

        @Override
        public String category() {
            return null;
        }
    }

    public final class MyKeyInput implements Input {
        int id;

        public MyKeyInput(int id) {
            this.id = id;
        }

        @Override
        public InputType getType() {
            return InputType.KEY;
        }

        @Override
        public String getName() {
            return "don't care";
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return "don't care";
        }
    }

    @Override
    public void preBegin() {
    }

    @Override
    public void postBegin() {
    }

    @Override
    public void preSave() {
    }

    @Override
    public void postSave() {
    }

    private EntityRef blockSelectionEntity = EntityRef.NULL;

    @ReceiveEvent(components = {ClientComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onMouseButtonEvent(MouseButtonEvent event, EntityRef entity) {
        if (event.isDown()) {
            if (MouseInput.MOUSE_LEFT == event.getButton()) {
                Vector3f worldPosition = renderer.getWorldLocation(event.getMousePosition());

                BlockSelectionComponent blockSelectionComponent;
                if (EntityRef.NULL == blockSelectionEntity) {
                    EntityManager entityManager = CoreRegistry.get(EntityManager.class);
                    blockSelectionComponent = new BlockSelectionComponent();
                    blockSelectionComponent.shouldRender = true;

                    Color transparentGreen = new Color(0, 255, 0, 100);
                    blockSelectionComponent.texture = Assets.get(TextureUtil.getTextureUriForColor(transparentGreen), Texture.class);

                    blockSelectionEntity = entityManager.create(blockSelectionComponent);
                } else {
                    blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
                }

                if (null == blockSelectionComponent.startPosition) {
                    blockSelectionComponent.startPosition = new Vector3i(worldPosition);
                } else {
                    blockSelectionComponent.currentSelection = Region3i.createBounded(blockSelectionComponent.startPosition, new Vector3i(worldPosition));
                }
            } else if (MouseInput.MOUSE_RIGHT == event.getButton()) {
                if (EntityRef.NULL != blockSelectionEntity) {
                    BlockSelectionComponent blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
                    if (null != blockSelectionComponent.currentSelection) {
                        blockSelectionEntity.send(new ApplyBlockSelectionEvent(EntityRef.NULL, blockSelectionComponent.currentSelection));
                    }

                    blockSelectionComponent.shouldRender = false;
                    blockSelectionComponent.currentSelection = null;
                    blockSelectionComponent.startPosition = null;
                }
            }

            event.consume();
        }
    }
}
