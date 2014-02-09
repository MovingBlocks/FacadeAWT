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

package org.terasology.engine.subsystem.awt.cities;

/**
 * A set of constants used for city building
 * @author Martin Steiger
 */
public enum BlockTypes {

    /**
     * Air (maybe not required)
     */
    AIR,

    /**
     * Surface of a road (asphalt)
     */
    ROAD_SURFACE,

    /**
     * Empty space in a lot
     */
    LOT_EMPTY,

    /**
     * A simple building's floor
     */
    BUILDING_FLOOR,

    /**
     * A simple building's foundation
     */
    BUILDING_FOUNDATION,

    /**
     * A simple building wall
     */
    BUILDING_WALL,

    /**
     * Flat roof
     */
    ROOF_FLAT,

    /**
     * Hip roof
     */
    ROOF_HIP,

    /**
     * Dome roof
     */
    ROOF_DOME,

    /**
     * The roof gable for saddle roofs
     */
    ROOF_GABLE,

    /**
     * Saddle roof
     */
    ROOF_SADDLE,

    /**
     * Tower stone
     */
    TOWER_WALL,

    /**
     * Fence along top (east-west)
     */
    FENCE_TOP,

    /**
     * Fence along bottom (east-west)
     */
    FENCE_BOTTOM,

    /**
     * Fence along left (north-south)
     */
    FENCE_LEFT,

    /**
     * Fence along right (north-south)
     */
    FENCE_RIGHT,

    /**
     * Fence corner (south-west)
     */
    FENCE_SW,

    /**
     * Fence corner (south-east)
     */
    FENCE_SE,

    /**
     * Fence corner (north-west)
     */
    FENCE_NW,

    /**
     * Fence corner (north-east)
     */
    FENCE_NE,

    FENCE_GATE_TOP,
    FENCE_GATE_LEFT,
    FENCE_GATE_RIGHT,
    FENCE_GATE_BOTTOM;

}
