package org.terasology.awt.world.renderer;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.context.Context;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.input.MouseInput;
import org.terasology.logic.selection.ApplyBlockSelectionEvent;
import org.terasology.math.Region3i;
import org.terasology.math.geom.Vector2i;
import org.terasology.math.geom.Vector3i;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureUtil;
import org.terasology.rendering.nui.BaseInteractionListener;
import org.terasology.rendering.nui.Canvas;
import org.terasology.rendering.nui.Color;
import org.terasology.rendering.nui.InteractionListener;
import org.terasology.rendering.nui.events.NUIMouseClickEvent;
import org.terasology.rendering.nui.events.NUIMouseDragEvent;
import org.terasology.rendering.nui.events.NUIMouseOverEvent;
import org.terasology.rendering.nui.events.NUIMouseReleaseEvent;
import org.terasology.rendering.nui.events.NUIMouseWheelEvent;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;
import org.terasology.utilities.Assets;
import org.terasology.world.selection.BlockSelectionComponent;

public class WorldSelectionScreen extends CoreHudWidget {

    private static final Logger logger = LoggerFactory.getLogger(WorldSelectionScreen.class);

    private Context context;
    
    public void setContext(Context context) {
		this.context = context;
	}

	private BlockTileWorldRenderer renderer;

    private EntityRef blockSelectionEntity;

    public WorldSelectionScreen() {
    }

    @Override
    public void initialise() {
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
        public void onMouseOver(NUIMouseOverEvent event) {
        	Vector2i pos = event.getRelativeMousePosition();
            if (true) logger.debug("MouseOver pos=" + pos);
        }

        @Override
        public boolean onMouseClick(NUIMouseClickEvent event) {
        	MouseInput button = event.getMouseButton();
        	Vector2i mousePosition = event.getRelativeMousePosition();
            if (MouseInput.MOUSE_LEFT == button) {
                Vector3i worldPosition = new Vector3i(renderer.getWorldLocation(mousePosition));

                BlockSelectionComponent blockSelectionComponent;
                if (EntityRef.NULL == blockSelectionEntity) {
                    EntityManager entityManager = context.get(EntityManager.class);
                    blockSelectionComponent = new BlockSelectionComponent();
                    blockSelectionComponent.shouldRender = true;

                    Color selectionColor = new Color(100, 100, 150, 100);
                    Optional<Texture> textureAsset = Assets.get(TextureUtil.getTextureUriForColor(selectionColor), Texture.class);
                    if (textureAsset.isPresent()) {
                        blockSelectionComponent.texture = textureAsset.get();
                    } else {
                        blockSelectionComponent.texture = null;
                    }

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
        public void onMouseDrag(NUIMouseDragEvent event) {
        	Vector2i mousePosition = event.getRelativeMousePosition();
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
        public void onMouseRelease(NUIMouseReleaseEvent event) {
        	Vector2i mousePosition = event.getRelativeMousePosition();
        	
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
        public boolean onMouseWheel(NUIMouseWheelEvent event) {
            return false;
        }
    };
}
