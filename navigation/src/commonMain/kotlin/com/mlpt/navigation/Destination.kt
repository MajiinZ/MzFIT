package com.mlpt.navigation

enum class Destination(val label: String, val symbol: String) {
    Home("Today", "◉"), Workouts("Workouts by body part", "◇"), MuscleMap("Muscle map & lifts", "◈"),
    Pose("ML · Pose detection", "◎"), Profile("Profile & progress", "○"), Tools("Training tools", "⚙"),
}
