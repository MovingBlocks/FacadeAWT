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

import javax.vecmath.Vector3f;

import org.terasology.audio.AudioManager;
import org.terasology.config.Config;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.subsystem.headless.renderer.NullCamera;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.math.AABB;
import org.terasology.math.geom.Rect2i;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.physics.bullet.BulletPhysics;
import org.terasology.physics.engine.PhysicsEngine;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.backdrop.Skysphere;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.rendering.world.viewDistance.ViewDistance;
import org.terasology.world.WorldProvider;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.ChunkProvider;
import org.terasology.world.chunks.internal.ChunkImpl;

import com.google.common.collect.Lists;

public abstract class AbstractWorldRenderer implements WorldRenderer {

    private static final int MAX_CHUNKS = ViewDistance.ULTRA.getChunkDistance() * ViewDistance.ULTRA.getChunkDistance();

    private final List<ChunkImpl> chunksInProximity = Lists.newArrayListWithCapacity(MAX_CHUNKS);
    private boolean pendingChunks;
    private int chunkPosX;
    private int chunkPosZ;

    private WorldProvider worldProvider;
    private ChunkProvider chunkProvider;

    /* PHYSICS */
    // TODO: Remove physics handling from world renderer
    private final BulletPhysics bulletPhysics;

    private Camera localPlayerCamera;
    private Camera activeViewCamera;

    private Config config;

    public AbstractWorldRenderer(WorldProvider worldProvider, ChunkProvider chunkProvider, LocalPlayerSystem localPlayerSystem) {
        this.worldProvider = worldProvider;
        this.chunkProvider = chunkProvider;
        bulletPhysics = new BulletPhysics(worldProvider);

        config = CoreRegistry.get(Config.class);
        CoreRegistry.get(ComponentSystemManager.class).register(new WorldCommands(chunkProvider));

        activeViewCamera = new NullCamera(worldProvider, renderingConfig);

        localPlayerCamera = new NullCamera(worldProvider, renderingConfig);
        localPlayerSystem.setPlayerCamera(localPlayerCamera);
    }

    @Override
    public WorldProvider getWorldProvider() {
        return worldProvider;
    }

    @Override
    public ChunkProvider getChunkProvider() {
        return chunkProvider;
    }

    @Override
    public PhysicsEngine getBulletRenderer() {
        return bulletPhysics;
    }

    @Override
    public Camera getActiveCamera() {
        return activeViewCamera;
    }

    @Override
    public Camera getLightCamera() {
        return new NullCamera(worldProvider, renderingConfig);
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

    private static float distanceToCamera(ChunkImpl chunk) {
        Vector3f result = new Vector3f((chunk.getPos().x + 0.5f) * ChunkConstants.SIZE_X, 0, (chunk.getPos().z + 0.5f) * ChunkConstants.SIZE_Z);

        Vector3f cameraPos = CoreRegistry.get(WorldRenderer.class).getActiveCamera().getPosition();
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
    public boolean updateChunksInProximity(boolean force) {
        int newChunkPosX = calcCamChunkOffsetX();
        int newChunkPosZ = calcCamChunkOffsetZ();

        // TODO: This should actually be done based on events from the ChunkProvider on new chunk availability/old chunk removal
        int viewingDistance = config.getRendering().getViewDistance().getChunkDistance();

        boolean chunksCurrentlyPending = false;
        if (chunkPosX != newChunkPosX || chunkPosZ != newChunkPosZ || force || pendingChunks) {
            if (chunksInProximity.size() == 0 || force || pendingChunks) {
                // just add all visible chunks
                chunksInProximity.clear();
                for (int x = -(viewingDistance / 2); x < viewingDistance / 2; x++) {
                    for (int z = -(viewingDistance / 2); z < viewingDistance / 2; z++) {
                        ChunkImpl c = chunkProvider.getChunk(newChunkPosX + x, 0, newChunkPosZ + z);
                        if (c != null && c.getChunkState() == ChunkImpl.State.COMPLETE && worldProvider.getLocalView(c.getPos()) != null) {
                            chunksInProximity.add(c);
                        } else {
                            chunksCurrentlyPending = true;
                        }
                    }
                }
            } else {
                // adjust proximity chunk list
                int vd2 = viewingDistance / 2;

                Rect2i oldView = Rect2i.createFromMinAndSize(chunkPosX - vd2, chunkPosZ - vd2, viewingDistance, viewingDistance);
                Rect2i newView = Rect2i.createFromMinAndSize(newChunkPosX - vd2, newChunkPosZ - vd2, viewingDistance, viewingDistance);

                // remove
                List<Rect2i> removeRects = Rect2i.difference(oldView, newView);
                for (Rect2i r : removeRects) {
                    for (int x = r.minX(); x <= r.maxX(); ++x) {
                        for (int y = r.minY(); y <= r.maxY(); ++y) {
                            ChunkImpl c = chunkProvider.getChunk(x, 0, y);
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
                            ChunkImpl c = chunkProvider.getChunk(x, 0, y);
                            if (c != null && c.getChunkState() == ChunkImpl.State.COMPLETE && worldProvider.getLocalView(c.getPos()) != null) {
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

            Collections.sort(chunksInProximity, new ChunkFrontToBackComparator());

            return true;
        }

        return false;
    }

    @Override
    public void update(float delta) {
        worldProvider.processPropagation();

        // Free unused space
        PerformanceMonitor.startActivity("Update Chunk Cache");
        chunkProvider.update();
        PerformanceMonitor.endActivity();

        PerformanceMonitor.startActivity("Update Close Chunks");
        updateChunksInProximity(false);
        PerformanceMonitor.endActivity();
    }

    @Override
    public void render(StereoRenderState mono) {

        renderWorld(getActiveCamera());
    }

    public abstract void renderWorld(Camera camera);

    @Override
    public void dispose() {
        worldProvider.dispose();
        CoreRegistry.get(AudioManager.class).stopAllSounds();
    }

    @Override
    public void setPlayer(LocalPlayer localPlayer) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean pregenerateChunks() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void changeViewDistance(ViewDistance viewDistance) {
        // TODO Auto-generated method stub

    }

    @Override
    public float getDaylight() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getSunlightValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getBlockLightValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getRenderingLightValueAt(Vector3f vector3f) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getSunlightValueAt(Vector3f worldPos) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getBlockLightValueAt(Vector3f worldPos) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getSmoothedPlayerSunlightValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isHeadUnderWater() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Vector3f getTint() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public float getTick() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Skysphere getSkysphere() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isAABBVisible(AABB aabb) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public WorldRenderingStage getCurrentRenderStage() {
        return WorldRenderingStage.DEFAULT;
    }

    private static class ChunkFrontToBackComparator implements Comparator<ChunkImpl> {

        @Override
        public int compare(ChunkImpl o1, ChunkImpl o2) {
            double distance = distanceToCamera(o1);
            double distance2 = distanceToCamera(o2);

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

}
