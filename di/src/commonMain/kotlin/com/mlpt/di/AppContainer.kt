package com.mlpt.di

import com.mlpt.data.InMemoryTrainingRepository
import com.mlpt.shared.TrainingRepository

/** Explicit composition root, with no platform or cloud initialization required. */
class AppContainer(val trainingRepository: TrainingRepository = InMemoryTrainingRepository())
