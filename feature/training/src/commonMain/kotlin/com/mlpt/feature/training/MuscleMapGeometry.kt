package com.mlpt.feature.training

import com.mlpt.shared.MuscleGroup

data class MapPoint(val x: Float, val y: Float)
data class MuscleRegion(val muscle: MuscleGroup, val points: List<MapPoint>)

private fun region(muscle: MuscleGroup, vararg coordinates: Float) = MuscleRegion(muscle,
    coordinates.toList().chunked(2).map { MapPoint(it[0], it[1]) })
private fun mirrored(region: MuscleRegion) = region.copy(points = region.points.map { MapPoint(300f - it.x, it.y) })
private fun paired(region: MuscleRegion) = listOf(region, mirrored(region))

/** Drawn polygons and hit areas use the same 300 × 460 coordinate space. */
fun muscleRegions(back: Boolean): List<MuscleRegion> = buildList {
    addAll(paired(region(MuscleGroup.Shoulders, 107f, 82f, 125f, 78f, 119f, 104f, 100f, 112f, 97f, 98f)))
    addAll(paired(region(if (back) MuscleGroup.Triceps else MuscleGroup.Biceps,
        100f, 112f, 117f, 106f, 112f, 142f, 98f, 155f, 91f, 146f)))
    if (back) {
        add(region(MuscleGroup.UpperBack, 126f, 78f, 150f, 68f, 174f, 78f, 179f, 111f, 150f, 130f, 121f, 111f))
        addAll(paired(region(MuscleGroup.Lats, 120f, 113f, 147f, 134f, 145f, 181f, 131f, 184f, 119f, 153f)))
        addAll(paired(region(MuscleGroup.Glutes, 131f, 185f, 148f, 186f, 147f, 229f, 119f, 224f, 119f, 206f)))
        addAll(paired(region(MuscleGroup.Hamstrings, 119f, 231f, 145f, 235f, 142f, 304f, 127f, 315f, 116f, 289f)))
    } else {
        addAll(paired(region(MuscleGroup.Chest, 124f, 87f, 147f, 92f, 146f, 127f, 121f, 133f, 118f, 108f)))
        add(region(MuscleGroup.Abs, 139f, 135f, 161f, 135f, 163f, 181f, 150f, 198f, 137f, 181f))
        addAll(paired(region(MuscleGroup.Obliques, 121f, 138f, 134f, 139f, 132f, 177f, 137f, 193f, 125f, 199f, 118f, 170f)))
        addAll(paired(region(MuscleGroup.Quads, 120f, 219f, 146f, 217f, 143f, 282f, 135f, 312f, 121f, 304f, 114f, 267f)))
    }
    addAll(paired(region(MuscleGroup.Calves, 124f, 326f, 141f, 326f, 142f, 359f, 131f, 395f, 120f, 372f)))
}

fun containsPoint(region: MuscleRegion, point: MapPoint): Boolean {
    var inside = false
    var previous = region.points.last()
    for (current in region.points) {
        if ((current.y > point.y) != (previous.y > point.y) &&
            point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) inside = !inside
        previous = current
    }
    return inside
}

fun muscleAt(back: Boolean, point: MapPoint) = muscleRegions(back).firstOrNull { containsPoint(it, point) }?.muscle
