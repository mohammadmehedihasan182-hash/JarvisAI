package com.doodlejoy.jarvis.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Futuristic glowing ring around the avatar.
 * States: IDLE (slow breathing), LISTENING (fast pulse), SPEAKING (wave-like), SLEEPING (dim).
 */
class GlowingRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, SPEAKING, SLEEPING }

    var state: State = State.IDLE
        set(value) {
            field = value
            animator.duration = when (value) {
                State.LISTENING -> 700L
                State.SPEAKING -> 450L
                State.IDLE -> 2200L
                State.SLEEPING -> 4000L
            }
            if (!animator.isRunning) animator.start()
        }

    private val neon = Color.parseColor("#00E5FF")
    private val dim = Color.parseColor("#00707E")
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = min(width, height) / 2f - 18f
        val pulse = when (state) {
            State.LISTENING -> kotlin.math.sin(phase * Math.PI * 2).toFloat() * 8f
            State.SPEAKING -> kotlin.math.sin(phase * Math.PI * 4).toFloat() * 5f
            State.IDLE -> kotlin.math.sin(phase * Math.PI * 2).toFloat() * 3f
            State.SLEEPING -> 0f
        }
        val alpha = if (state == State.SLEEPING) 60 else 255

        // Outer soft glows
        for (i in 3 downTo 1) {
            glowPaint.color = if (state == State.SLEEPING) dim else neon
            glowPaint.alpha = (alpha * 0.12f).toInt()
            glowPaint.strokeWidth = 10f * i
            canvas.drawCircle(cx, cy, baseR + pulse + i * 4f, glowPaint)
        }
        // Bright core ring
        corePaint.color = if (state == State.SLEEPING) dim else neon
        corePaint.alpha = alpha
        corePaint.strokeWidth = 5f
        canvas.drawCircle(cx, cy, baseR + pulse, corePaint)
        // Rotating arc accent
        corePaint.alpha = (alpha * 0.7f).toInt()
        corePaint.strokeWidth = 3f
        val start = phase * 360f
        canvas.drawArc(cx - baseR - 12f, cy - baseR - 12f, cx + baseR + 12f, cy + baseR + 12f, start, 70f, false, corePaint)
        canvas.drawArc(cx - baseR - 12f, cy - baseR - 12f, cx + baseR + 12f, cy + baseR + 12f, start + 180f, 70f, false, corePaint)
    }
}
