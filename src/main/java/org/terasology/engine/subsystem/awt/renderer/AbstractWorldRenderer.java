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
package org.terasology.engine.subsystem.awt.renderer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.terasology.audio.AudioManager;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.context.Context;
import org.terasology.engine.subsystem.headless.renderer.NullCamera;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Rect2i;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.network.Client;
import org.terasology.network.NetworkSystem;
import org.terasology.rendering.backdrop.BackdropProvider;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.cameras.SubmersibleCamera;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.rendering.world.viewDistance.ViewDistance;
import org.terasology.world.ChunkView;
import org.terasology.world.WorldProvider;
import org.terasology.world.chunks.Chunk;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.ChunkProvider;
import org.terasology.world.chunks.RenderableChunk;

import com.google.common.collect.Lists;

public abstract class AbstractWorldRenderer implements WorldRenderer {

	private ViewDistance viewDistance = ViewDistance.ULTRA;

	// TODO: should we resize if viewDistance changes?
    private final List<Chunk> chunksInProximity = Lists.newArrayListWithCapacity(viewDistance.getChunkDistance().x);

    private boolean pendingChunks;
    private int chunkPosX;
    private int chunkPosZ;

    private SubmersibleCamera activeViewCamera;
	private Camera lightCamera;

    private Context context;
    private float secondsSinceLastFrame;
    private float millisecondsSinceRenderingStart;

	private float timeSmoothedMainLightIntensity;

    public AbstractWorldRenderer(Context context) {
    	this.context = context;

    	// TODO: don't remember what this does
        // CoreRegistry.get(ComponentSystemManager.class).register(new WorldCommands(chunkProvider));

        WorldProvider worldProvider = context.get(WorldProvider.class);
		RenderingConfig renderingConfig = context.get(Config.class).getRendering();
        activeViewCamera = new NullCamera(worldProvider, renderingConfig);
        lightCamera = new NullCamera(worldProvider, renderingConfig);
        Camera localPlayerCamera = new NullCamera(worldProvider, renderingConfig);
        context.get(LocalPlayerSystem.class).setPlayerCamera(localPlayerCamera);
    }

    @Override
    public SubmersibleCamera getActiveCamera() {
        return activeViewCamera;
    }

    @Override
    public Camera getLightCamera() {
        return lightCamera;
    }

    /**
     * Chunk position of the player.
     *
     * @return The player offset on the x-axis
     */
    private int calcCamChunkOffsetX() {
        return (int) (getActiveCamera().getPosition().x / ChunkConstants.SIZE_X);
    }

    /**
     * Chunk position of the player.
     *
     * @return The player offset on the z-axis
     */
    private int calcCamChunkOffsetZ() {
        return (int) (getActiveCamera().getPosition().z / ChunkConstants.SIZE_Z);
    }

    private float distanceToCamera(Chunk chunk) {
        Vector3f result = new Vector3f((chunk.getPosition().x + 0.5f) * ChunkConstants.SIZE_X, 0, (chunk.getPosition().z + 0.5f) * ChunkConstants.SIZE_Z);

        Vector3f cameraPos = getActiveCamera().getPosition();
        result.x -= cameraPos.x;
        result.z -= cameraPos.z;

        return result.length();
    }

    /**
     * Updates the list of chunks around the player.
     *
     * @param force Forces the update
     * @return True if the list was changed
     */
    private boolean updateChunksInProximity(boolean force) {
        WorldProvider worldProvider = context.get(WorldProvider.class);
        LocalPlayer localPlayer = context.get(LocalPlayer.class);
        ChunkProvider chunkProvider = context.get(ChunkProvider.class);
        NetworkSystem networkSystem = context.get(NetworkSystem.class);

        int newChunkPosX = calcCamChunkOffsetX();
        int newChunkPosZ = calcCamChunkOffsetZ();

        // TODO: This should actually be done based on events from the ChunkProvider on new chunk availability/old chunk removal
        EntityRef clientEntity = localPlayer.getClientEntity();
        Client clientListener = networkSystem.getOwner(clientEntity);
        Vector3i chunkViewDistanceVector = clientListener.getViewDistance().getChunkDistance();

        boolean chunksCurrentlyPending = false;
        if (chunkPosX != newChunkPosX || chunkPosZ != newChunkPosZ || force || pendingChunks) {
            if (chunksInProximity.size() == 0 || force || pendingChunks) {
                // just add all visible chunks
                chunksInProximity.clear();
                for (int x = -(chunkViewDistanceVector.x / 2); x < chunkViewDistanceVector.x / 2; x++) {
                    for (int z = -(chunkViewDistanceVector.z / 2); z < chunkViewDistanceVector.z / 2; z++) {
                        Chunk c = chunkProvider.getChunk(newChunkPosX + x, 0, newChunkPosZ + z);
                        if (c != null && c.isReady() && worldProvider.getLocalView(c.getPosition()) != null) {
                            chunksInProximity.add(c);
                        } else {
                            chunksCurrentlyPending = true;
                        }
                    }
                }
            } else {
                // adjust proximity chunk list
            	// TOTAL GUESSWORK on these view distance conversions.
                int vd2x = chunkViewDistanceVector.x / 2;
                int vd2z = chunkViewDistanceVector.z / 2;
                Rect2i oldView = Rect2i.createFromMinAndSize(chunkPosX - vd2x, chunkPosZ - vd2z, chunkViewDistanceVector.x, chunkViewDistanceVector.z);
                Rect2i newView = Rect2i.createFromMinAndSize(newChunkPosX - vd2x, newChunkPosZ - vd2z, chunkViewDistanceVector.x, chunkViewDistanceVector.z);

                // remove
                List<Rect2i> removeRects = Rect2i.difference(oldView, newView);
                for (Rect2i r : removeRects) {
                    for (int x = r.minX(); x <= r.maxX(); ++x) {
                        for (int y = r.minY(); y <= r.maxY(); ++y) {
                            Chunk c = chunkProvider.getChunk(x, 0, y);
                            if (c != null) {
                                chunksInProximity.remove(c);
                                c.disposeMesh();
                            }
                        }
                    }
                }

                // add
                List<Rect2i> addRects = Rect2i.difference(newView, oldView);
                for (Rect2i r : addRects) {
                    for (int x = r.minX(); x <= r.maxX(); ++x) {
                        for (int y = r.minY(); y <= r.maxY(); ++y) {
                            Chunk c = chunkProvider.getChunk(x, 0, y);
                            if (c != null && c.isReady() && worldProvider.getLocalView(c.getPosition()) != null) {
                                chunksInProximity.add(c);
                            } else {
                                chunksCurrentlyPending = true;
                            }
                        }
                    }
                }
            }

            chunkPosX = newChunkPosX;
            chunkPosZ = newChunkPosZ;
            pendingChunks = chunksCurrentlyPending;

            Collections.sort(chunksInProximity, new ChunkFrontToBackComparator(this));

            return true;
        }

        return false;
    }

    @Override
    public void update(float deltaInSeconds) {
        secondsSinceLastFrame += deltaInSeconds;

        WorldProvider worldProvider = context.get(WorldProvider.class);
        ChunkProvider chunkProvider = context.get(ChunkProvider.class);
        worldProvider.processPropagation();

        // Free unused space
        PerformanceMonitor.startActivity("Update Chunk Cache");
        chunkProvider.beginUpdate();
        chunkProvider.completeUpdate();
        PerformanceMonitor.endActivity();

        PerformanceMonitor.startActivity("Update Close Chunks");
        updateChunksInProximity(false);
        PerformanceMonitor.endActivity();
    }

    @Override
    public void render(RenderingStage renderingStage) {
        millisecondsSinceRenderingStart += secondsSinceLastFrame * 1000;  // updates the variable animations are based on.
        SubmersibleCamera playerCamera = getActiveCamera();
        timeSmoothedMainLightIntensity = TeraMath.lerp(timeSmoothedMainLightIntensity, getMainLightIntensityAt(playerCamera.getPosition()), secondsSinceLastFrame);
        renderWorld(getActiveCamera());
    }

    public abstract void renderWorld(Camera camera);

    @Override
    public void dispose() {
        WorldProvider worldProvider = context.get(WorldProvider.class);
        worldProvider.dispose();
        context.get(AudioManager.class).stopAllSounds();
    }

    @Override
    /**
     * @return true if pregeneration is complete
     */
    public boolean pregenerateChunks() {
        boolean pregenerationIsComplete = true;

        WorldProvider worldProvider = context.get(WorldProvider.class);
        ChunkProvider chunkProvider = context.get(ChunkProvider.class);
		RenderingConfig renderingConfig = context.get(Config.class).getRendering();
        chunkProvider.completeUpdate();
        chunkProvider.beginUpdate();

        RenderableChunk chunk;
        ChunkView localView;
        for (Vector3i chunkCoordinates : calculateRenderableRegion(renderingConfig.getViewDistance())) {
            chunk = chunkProvider.getChunk(chunkCoordinates);
            if (chunk == null) {
                pregenerationIsComplete = false;
            } else if (chunk.isDirty()) {
                localView = worldProvider.getLocalView(chunkCoordinates);
                if (localView == null) {
                    continue;
                }
                chunk.setDirty(false);

                pregenerationIsComplete = false;
                break;
            }
        }
        return pregenerationIsComplete;
    }

    private Region3i calculateRenderableRegion(ViewDistance newViewDistance) {
        Vector3i cameraCoordinates = calcCameraCoordinatesInChunkUnits();
        Vector3i renderableRegionSize = newViewDistance.getChunkDistance();
        Vector3i renderableRegionExtents = new Vector3i(renderableRegionSize.x / 2, renderableRegionSize.y / 2, renderableRegionSize.z / 2);
        return Region3i.createFromCenterExtents(cameraCoordinates, renderableRegionExtents);
    }

    /**
     * Chunk position of the player.
     *
     * @return The player offset chunk
     */
    private Vector3i calcCameraCoordinatesInChunkUnits() {
        SubmersibleCamera playerCamera = getActiveCamera();
        Vector3f cameraCoordinates = playerCamera.getPosition();
        return new Vector3i((int) (cameraCoordinates.x / ChunkConstants.SIZE_X),
                (int) (cameraCoordinates.y / ChunkConstants.SIZE_Y),
                (int) (cameraCoordinates.z / ChunkConstants.SIZE_Z));
    }


    @Override
    public RenderingStage getCurrentRenderStage() {
        return RenderingStage.MONO;
    }

    private static class ChunkFrontToBackComparator implements Comparator<Chunk> {

    	private AbstractWorldRenderer worldRenderer;
    	public ChunkFrontToBackComparator(AbstractWorldRenderer worldRenderer) {
    		this.worldRenderer = worldRenderer;
    	}
    	
        @Override
        public int compare(Chunk o1, Chunk o2) {
            double distance = worldRenderer.distanceToCamera(o1);
            double distance2 = worldRenderer.distanceToCamera(o2);

            if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            if (distance == distance2) {
                return 0;
            }

            return distance2 > distance ? -1 : 1;
        }
    }

	@Override
	public float getSecondsSinceLastFrame() {
        return secondsSinceLastFrame;
	}

    @Override
    public float getMillisecondsSinceRenderingStart() {
        return millisecondsSinceRenderingStart;
    }

	@Override
	public void setViewDistance(ViewDistance viewDistance) {
		this.viewDistance = viewDistance;
	}

	@Override
	public float getRenderingLightIntensityAt(Vector3f pos) {
		WorldProvider worldProvider = context.get(WorldProvider.class);
		BackdropProvider backdropProvider = context.get(BackdropProvider.class);
        float rawLightValueSun = worldProvider.getSunlight(pos) / 15.0f;
        float rawLightValueBlock = worldProvider.getLight(pos) / 15.0f;

        float lightValueSun = (float) Math.pow(BLOCK_LIGHT_SUN_POW, (1.0f - rawLightValueSun) * 16.0f) * rawLightValueSun;
        lightValueSun *= backdropProvider.getDaylight();
        // TODO: Hardcoded factor and value to compensate for daylight tint and night brightness
        lightValueSun *= 0.9f;
        lightValueSun += 0.05f;

        float lightValueBlock = (float) Math.pow(BLOCK_LIGHT_POW, (1.0f - rawLightValueBlock) * 16.0f) * rawLightValueBlock * BLOCK_INTENSITY_FACTOR;

        return Math.max(lightValueBlock, lightValueSun);
	}

	@Override
	public float getMainLightIntensityAt(Vector3f position) {
		WorldProvider worldProvider = context.get(WorldProvider.class);
		BackdropProvider backdropProvider = context.get(BackdropProvider.class);
        return backdropProvider.getDaylight() * worldProvider.getSunlight(position) / 15.0f;
	}

	@Override
	public float getBlockLightIntensityAt(Vector3f position) {
		WorldProvider worldProvider = context.get(WorldProvider.class);
        return worldProvider.getLight(position) / 15.0f;
	}

	@Override
	public float getTimeSmoothedMainLightIntensity() {
		return timeSmoothedMainLightIntensity;
	}


	@Override
	public String getMetrics() {
		return "No metrics yet";
	}

}
