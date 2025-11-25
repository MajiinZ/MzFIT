package org.mz.mzfit.ml.pose

import com.google.mlkit.vision.pose.PoseLandmark
import org.mz.mzfit.ui.componenets.LandmarkModel

/**
 * Smooths ML Kit 2D/3D pose landmarks using exponential moving average.
 *
 * alpha = 1.0 → no smoothing
 * alpha = 0.0 → frozen position
 */
class Smoother(private val alpha: Float = 0.4f) {

    private val lastX = mutableMapOf<Int, Float>()
    private val lastY = mutableMapOf<Int, Float>()
    private val lastZ = mutableMapOf<Int, Float>()

    fun smooth(landmarks: List<PoseLandmark>): List<LandmarkModel> {
        val result = mutableListOf<LandmarkModel>()

        landmarks.forEach { lm ->
            val id = lm.landmarkType

            // 2D fallback (base model) or 3D model
            val currX = try { lm.position3D.x } catch (e: Exception) { lm.position.x }
            val currY = try { lm.position3D.y } catch (e: Exception) { lm.position.y }
            val currZ = try { lm.position3D.z } catch (e: Exception) { 0f }

            val prevX = lastX[id] ?: currX
            val prevY = lastY[id] ?: currY
            val prevZ = lastZ[id] ?: currZ

            val smoothX = prevX * (1 - alpha) + currX * alpha
            val smoothY = prevY * (1 - alpha) + currY * alpha
            val smoothZ = prevZ * (1 - alpha) + currZ * alpha

            lastX[id] = smoothX
            lastY[id] = smoothY
            lastZ[id] = smoothZ

            result.add(
                LandmarkModel(
                    id = id,
                    x = smoothX,
                    y = smoothY,
                    z = smoothZ,
                    likelihood = lm.inFrameLikelihood
                )
            )
        }

        return result
    }
}
