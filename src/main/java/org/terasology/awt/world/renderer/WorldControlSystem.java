package org.terasology.awt.world.renderer;

import org.terasology.awt.input.binds.ScrollBackwardButton;
import org.terasology.awt.input.binds.ScrollDownButton;
import org.terasology.awt.input.binds.ScrollForwardButton;
import org.terasology.awt.input.binds.ScrollLeftButton;
import org.terasology.awt.input.binds.ScrollRightButton;
import org.terasology.awt.input.binds.ScrollUpButton;
import org.terasology.awt.input.binds.ToggleMapAxisButton;
import org.terasology.awt.input.binds.ZoomInButton;
import org.terasology.awt.input.binds.ZoomOutButton;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.network.ClientComponent;

public class WorldControlSystem extends BaseComponentSystem {

    private BlockTileWorldRenderer renderer;

    public WorldControlSystem(BlockTileWorldRenderer renderer) {
        this.renderer = renderer;
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
}
