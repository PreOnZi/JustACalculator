package com.fictioncutshort.justacalculator.util

import androidx.compose.ui.geometry.Offset

/**
 * Math3D.kt
 *
 * 3D math utilities for the floating keyboard chaos mini-game.
 * Handles projection from 3D space to 2D screen coordinates,
 * and rotation around axes.
 *
 * COORDINATE SYSTEM:
 * - X: left/right
 * - Y: up/down
 * - Z: depth (into/out of screen)
 *
 * The 3D effect is achieved using perspective projection - objects
 * further away (higher Z) appear smaller.
 */

/**
 * A point in 3D space.
 */
data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Projects a 3D point onto 2D screen coordinates.
 *
 * Uses perspective projection: objects with higher Z (further away)
 * are drawn smaller and closer to the center.
 *
 * @param point The 3D point to project
 * @param centerX Screen center X coordinate
 * @param centerY Screen center Y coordinate
 * @param scale Zoom level multiplier
 * @param fov Field of view - higher = less perspective distortion
 * @return 2D screen position as Offset
 */
fun project(
    point: Point3D,
    centerX: Float,
    centerY: Float,
    scale: Float,
    fov: Float = 500f
): Offset {
    // Perspective scale: objects at z=0 have scale 1.0
    // Objects further away (positive z) appear smaller
    val projectionScale = fov / (fov + point.z)

    return Offset(
        centerX + point.x * projectionScale * scale,
        centerY + point.y * projectionScale * scale
    )
}

/**
 * Rotates a point around the Y axis (horizontal spin).
 *
 * Positive angle = clockwise when viewed from above
 *
 * @param point The point to rotate
 * @param angle Rotation angle in degrees
 * @return Rotated point
 */
fun rotateY(point: Point3D, angle: Float): Point3D {
    val rad = Math.toRadians(angle.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()

    return Point3D(
        x = point.x * cos - point.z * sin,
        y = point.y,  // Y unchanged
        z = point.x * sin + point.z * cos
    )
}

/**
 * Rotates a point around the X axis (vertical tilt).
 *
 * Positive angle = tilt backward (top goes away from viewer)
 *
 * @param point The point to rotate
 * @param angle Rotation angle in degrees
 * @return Rotated point
 */
fun rotateX(point: Point3D, angle: Float): Point3D {
    val rad = Math.toRadians(angle.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()

    return Point3D(
        x = point.x,  // X unchanged
        y = point.y * cos - point.z * sin,
        z = point.y * sin + point.z * cos
    )
}

/**
 * Calculates the screen position of a chaos letter based on current view rotation.
 *
 * Used for hit detection when tapping floating letters.
 *
 * @param chaosKeyX Letter's base X position
 * @param chaosKeyY Letter's base Y position
 * @param chaosKeyZ Letter's base Z position
 * @param rotationX Current view X rotation (tilt)
 * @param rotationY Current view Y rotation (spin)
 * @param scale Current zoom level
 * @param centerX Screen center X
 * @param centerY Screen center Y
 * @return Screen position as Offset
 */
fun getLetterScreenPosition(
    chaosKeyX: Float,
    chaosKeyY: Float,
    chaosKeyZ: Float,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    centerX: Float,
    centerY: Float
): Offset {
    // Scale down the position (letters are spread out in 3D space)
    val lx = chaosKeyX * 0.6f
    val ly = chaosKeyY * 0.6f
    val lz = chaosKeyZ * 0.6f

    // Apply rotations (Y first, then X - order matters!)
    var point = Point3D(lx, ly, lz)
    point = rotateY(point, rotationY)
    point = rotateX(point, rotationX)

    // Project to screen
    return project(point, centerX, centerY, scale)
}