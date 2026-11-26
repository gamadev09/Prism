package com.gamadev.prism.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A frame-clock animation with synchronous pointer input. All mutations run on the UI thread. */
@Stable
internal class InteractiveFloat(initial: Float = 0f) {
    var value by mutableFloatStateOf(initial)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var velocity: Float = 0f
        private set
    private var active: Deferred<Unit>? = null

    fun stop() {
        val outgoing = active
        active = null
        isRunning = false
        velocity = 0f
        outgoing?.cancel()
    }

    fun snapTo(position: Float) {
        stop()
        value = position
    }

    suspend fun animateTo(
        targetValue: Float,
        animationSpec: AnimationSpec<Float>,
        initialVelocity: Float = velocity,
    ) = coroutineScope {
        stop()
        val animation = async(start = CoroutineStart.LAZY) {
            animate(value, targetValue, initialVelocity, animationSpec) { position, speed ->
                value = position
                velocity = speed
            }
            value = targetValue
        }
        active = animation
        velocity = initialVelocity
        isRunning = true
        try {
            animation.await()
        } finally {
            if (active === animation) stop()
        }
    }
}
