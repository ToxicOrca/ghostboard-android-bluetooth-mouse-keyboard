package com.example.remoteinput.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.remoteinput.R

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface TrackpadListener {
        fun onMove(dx: Int, dy: Int)
        fun onTap()
        fun onTwoFingerTap()
        fun onScroll(amount: Int)
    }

    var listener: TrackpadListener? = null
    var sensitivity: Float = 1.5f

    private var lastX = 0f
    private var lastY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var isMoving = false
    private var pointerCount = 0
    private var lastScrollY = 0f
    private var wasTwoFinger = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.trackpad_bg)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.trackpad_border)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    private val bounds = RectF()
    private val cornerRadius = 12f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, borderPaint)

        // Draw subtle label in center
        if (!isMoving) {
            canvas.drawText("Trackpad", width / 2f, height / 2f + labelPaint.textSize / 3f, labelPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                isMoving = false
                wasTwoFinger = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                wasTwoFinger = true
                lastScrollY = event.getY(1)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 1 && !wasTwoFinger) {
                    // Single finger: mouse movement
                    val dx = ((event.x - lastX) * sensitivity).toInt()
                    val dy = ((event.y - lastY) * sensitivity).toInt()
                    if (dx != 0 || dy != 0) {
                        isMoving = true
                        listener?.onMove(dx, dy)
                    }
                    lastX = event.x
                    lastY = event.y
                } else if (pointerCount == 2) {
                    // Two fingers: scroll
                    val scrollDy = event.getY(1) - lastScrollY
                    val scrollAmount = -(scrollDy * 0.15f).toInt()
                    if (scrollAmount != 0) {
                        isMoving = true
                        listener?.onScroll(scrollAmount)
                        lastScrollY = event.getY(1)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchStartTime
                val distX = Math.abs(event.x - touchStartX)
                val distY = Math.abs(event.y - touchStartY)
                val tapThreshold = 15f

                if (elapsed < 250 && distX < tapThreshold && distY < tapThreshold) {
                    if (wasTwoFinger) {
                        listener?.onTwoFingerTap()
                    } else {
                        listener?.onTap()
                    }
                }

                isMoving = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
