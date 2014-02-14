package org.terasology.awt.world.renderer;

import java.awt.Color;

import javax.vecmath.Vector3f;

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
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.input.MouseInput;
import org.terasology.input.events.MouseButtonEvent;
import org.terasology.logic.selection.ApplyBlockSelectionEvent;
import org.terasology.math.Region3i;
import org.terasology.math.Vector3i;
import org.terasology.network.ClientComponent;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureUtil;
import org.terasology.world.selection.BlockSelectionComponent;

public class WorldControlSystem extends BaseComponentSystem {

    // private static final Logger logger = LoggerFactory.getLogger(WorldControlSystem.class);

    private BlockTileWorldRenderer renderer;

    private EntityRef blockSelectionEntity = EntityRef.NULL;

    public WorldControlSystem(BlockTileWorldRenderer renderer) {
        this.renderer = renderer;

    }

    @Override
    public void shutdown() {
        blockSelectionEntity.destroy();
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
