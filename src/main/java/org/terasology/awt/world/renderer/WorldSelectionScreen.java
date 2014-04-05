package org.terasology.awt.world.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.input.MouseInput;
import org.terasology.logic.selection.ApplyBlockSelectionEvent;
import org.terasology.math.Region3i;
import org.terasology.math.Vector2i;
import org.terasology.math.Vector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureUtil;
import org.terasology.rendering.nui.BaseInteractionListener;
import org.terasology.rendering.nui.Canvas;
import org.terasology.rendering.nui.Color;
import org.terasology.rendering.nui.InteractionListener;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;
import org.terasology.world.selection.BlockSelectionComponent;

public class WorldSelectionScreen extends CoreHudWidget {

    private static final Logger logger = LoggerFactory.getLogger(WorldSelectionScreen.class);

    private BlockTileWorldRenderer renderer;

    private EntityRef blockSelectionEntity;

    public WorldSelectionScreen() {
    }

    @Override
    protected void initialise() {
    }

    public void setRenderer(BlockTileWorldRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void onOpened() {
        blockSelectionEntity = EntityRef.NULL;
    }

    @Override
    public void onClosed() {
        blockSelectionEntity.destroy();
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.addInteractionRegion(screenInteractionListener);
    }

    private final InteractionListener screenInteractionListener = new BaseInteractionListener() {

        boolean isDragging = false;
        
        @Override
        public void onMouseOver(Vector2i pos, boolean topMostElement) {
            if (true) logger.debug("MouseOver pos=" + pos);
        }

        @Override
        public boolean onMouseClick(MouseInput button, Vector2i mousePosition) {
            if (MouseInput.MOUSE_LEFT == button) {
                Vector3i worldPosition = new Vector3i(renderer.getWorldLocation(mousePosition));

                BlockSelectionComponent blockSelectionComponent;
                if (EntityRef.NULL == blockSelectionEntity) {
                    EntityManager entityManager = CoreRegistry.get(EntityManager.class);
                    blockSelectionComponent = new BlockSelectionComponent();
                    blockSelectionComponent.shouldRender = true;

                    Color selectionColor = new Color(100, 100, 150, 100);
                    blockSelectionComponent.texture = Assets.get(TextureUtil.getTextureUriForColor(selectionColor), Texture.class);

                    blockSelectionEntity = entityManager.create(blockSelectionComponent);
                    logger.debug("blockSelectionEntity created as  " + blockSelectionEntity + " with " + blockSelectionComponent);

                } else {
                    blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
                    logger.debug("blockSelectionEntity fetched from  " + blockSelectionEntity + " as " + blockSelectionComponent);
                }

                blockSelectionComponent.startPosition = worldPosition;
                blockSelectionComponent.currentSelection = null;
                blockSelectionComponent.shouldRender = true;
                logger.debug("blockSelectionComponent startPosition set to " + blockSelectionComponent.startPosition);

            } else if (MouseInput.MOUSE_RIGHT == button) {
                if (EntityRef.NULL != blockSelectionEntity) {
                    BlockSelectionComponent blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
                    logger.debug("right click: blockSelectionEntity fetched from  " + blockSelectionEntity + " as " + blockSelectionComponent);
                    if (null != blockSelectionComponent.currentSelection) {
                        blockSelectionEntity.send(new ApplyBlockSelectionEvent(EntityRef.NULL, blockSelectionComponent.currentSelection));
                        logger.debug("right click: ApplyBlockSelectionEvent send for  " + blockSelectionComponent.currentSelection);
                    }

                    blockSelectionComponent.shouldRender = false;
                    blockSelectionComponent.currentSelection = null;
                    blockSelectionComponent.startPosition = null;
                    logger.debug("right click: blockSelectionComponent cleared");
                    
                    isDragging = false;
                }
            }
            
            return true;
        }
        
        @Override
        public void onMouseDrag(Vector2i mousePosition) {
            isDragging = true;
            
            Vector3i worldPosition = new Vector3i(renderer.getWorldLocation(mousePosition));

            BlockSelectionComponent blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
            if (null == blockSelectionComponent.startPosition) {
                blockSelectionComponent.startPosition = worldPosition;
                blockSelectionComponent.currentSelection = null;
                blockSelectionComponent.shouldRender = true;
                logger.debug("blockSelectionComponent startPosition set to " + blockSelectionComponent.startPosition + " in mouse dragged");
            } else {
                blockSelectionComponent.currentSelection = Region3i.createBounded(blockSelectionComponent.startPosition, worldPosition);
            }
        }

        @Override
        public void onMouseRelease(MouseInput button, Vector2i mousePosition) {
            if (isDragging) {
                Vector3i worldPosition = new Vector3i(renderer.getWorldLocation(mousePosition));

                BlockSelectionComponent blockSelectionComponent = blockSelectionEntity.getComponent(BlockSelectionComponent.class);
                blockSelectionComponent.currentSelection = Region3i.createBounded(blockSelectionComponent.startPosition, worldPosition);

                logger.debug("right click: blockSelectionEntity fetched from  " + blockSelectionEntity + " as " + blockSelectionComponent);
                if (null != blockSelectionComponent.currentSelection) {
                    blockSelectionEntity.send(new ApplyBlockSelectionEvent(EntityRef.NULL, blockSelectionComponent.currentSelection));
                    logger.debug("right click: ApplyBlockSelectionEvent send for  " + blockSelectionComponent.currentSelection);
                }

                blockSelectionComponent.shouldRender = false;
                blockSelectionComponent.currentSelection = null;
                blockSelectionComponent.startPosition = null;
                logger.debug("mouse-release: blockSelectionComponent cleared");
                
                isDragging = false;
            }
        }

        @Override
        public boolean onMouseWheel(int wheelTurns, Vector2i pos) {
            return false;
        }
    };
}
