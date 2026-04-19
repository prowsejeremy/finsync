package com.jpd.finsync.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Sync status indicator.
 *
 * Syncing  → static wobbly blob (surface_2 fill) with a white progress stroke
 *            that traces the blob's own outline via PathMeasure. During sync the
 *            entire unit (fill + stroke) rotates continuously as one piece via
 *            canvas.rotate(), so the stroke always stays aligned to the blob edge.
 *
 * Idle     → smooth circle (surface_2 fill), no stroke, no rotation.
 *
 * Set [isSyncing] and [progress] (0f–1f) from the activity.
 */
class SyncBlobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0f–1f fraction of tracks downloaded — drives how much of the outline is stroked. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Starts/stops the rotation animator and switches between blob and circle. */
    var isSyncing: Boolean = false
        set(value) {
            if (field == value) return   // no state change — never restart a running animation
            field = value
            if (value) startRotation() else stopRotation()
            invalidate()
        }

    // ── Animation ─────────────────────────────────────────────────────────────

    private var rotationDegrees = 0f
    private var rotationAnimator: ValueAnimator? = null

    private fun startRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration     = 16000L              // one full rotation every 16 s
            repeatCount  = ValueAnimator.INFINITE
            repeatMode   = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationDegrees = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        rotationDegrees  = 0f
        invalidate()
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private var baseRadius     = 0f
    private val blobPath       = Path()
    private val progressPath   = Path()
    private val pathMeasure    = PathMeasure()
    private var blobPathLength = 0f

    // ── Paints ────────────────────────────────────────────────────────────────

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F262B.toInt()   // surface_2
        style = Paint.Style.FILL
    }

    private val strokePaintSyncing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        // strokeWidth set in onSizeChanged once density is known
    }

    private val strokePaintFinished = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color        = 0xFF00FFAA.toInt()   // accent_green
        style        = Paint.Style.STROKE
        strokeWidth  = 5f
    }

    // ── Sizing ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        strokePaintSyncing.strokeWidth = 3.5f * resources.displayMetrics.density
        baseRadius = min(w, h) / 2f * 0.7f

        buildBlobPath(w / 2f, h / 2f, baseRadius)
        // forceClosed = true so PathMeasure length includes the implied closing segment
        pathMeasure.setPath(blobPath, true)
        blobPathLength = pathMeasure.length
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force square: height always equals width
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    /**
     * Builds the static scalloped blob.
     *
     * 720 points with frequency-20 sine gives ~36 sample points per scallop —
     * smooth enough to look continuous at any display density.
     * Amplitude at 8 % of radius matches the pronounced-but-even bumps in the design.
     * Path starts at 12 o'clock (−90°) so the progress stroke sweeps clockwise from the top.
     */
    private fun buildBlobPath(cx: Float, cy: Float, baseR: Float) {
        blobPath.reset()
        val numPoints  = 720
        val amplitude  = baseR * 0.04f
        val frequency  = 20.0
        val startAngle = -Math.PI / 2.0      // 12 o'clock in Android canvas coords

        for (i in 0 until numPoints) {
            val angle = startAngle + 2.0 * Math.PI * i / numPoints
            val r     = baseR + (amplitude * sin(frequency * angle)).toFloat()
            val x     = cx + r * cos(angle).toFloat()
            val y     = cy + r * sin(angle).toFloat()
            if (i == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
        }
        blobPath.close()
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val cx = width  / 2f
        val cy = height / 2f

        if (isSyncing) {
            // Rotate the whole blob + stroke together as one unit.
            // canvas.save/restore means nothing outside this block is affected.
            canvas.save()
            canvas.rotate(rotationDegrees, cx, cy)

            canvas.drawPath(blobPath, fillPaint)

            if (progress > 0f && blobPathLength > 0f) {
                progressPath.reset()
                pathMeasure.getSegment(
                    0f,
                    blobPathLength * progress,
                    progressPath,
                    true   // startWithMoveTo
                )
                canvas.drawPath(progressPath, strokePaintSyncing)
            }

            canvas.restore()
        } else {
            canvas.drawCircle(cx, cy, baseRadius, fillPaint)

            // If we have finished syncing, then display a green circle around the main circle.
            if (progress > 0f) {
                canvas.drawCircle(cx, cy, baseRadius + 25f, strokePaintFinished)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
    }
}
