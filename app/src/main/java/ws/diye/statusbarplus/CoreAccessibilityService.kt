package ws.diye.statusbarplus

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.view.accessibility.AccessibilityEvent

import android.graphics.PixelFormat
import android.view.*

import android.widget.FrameLayout
import kotlin.math.*
import android.view.MotionEvent

import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager


class CoreAccessibilityService : AccessibilityService() {
    var mLayout: FrameLayout? = null
    private val touchGestureDetect = TouchGestureDetect()
    private var sharedPreferences: SharedPreferences? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        super.onServiceConnected()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var statusBarHeight = 0
        val screenWidth: Int
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        screenWidth = wm.defaultDisplay.width
        val mGestureTap = GestureDetector(applicationContext, object : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                return true
            }
        })

        mLayout = FrameLayout(this)
        mLayout!!.setOnTouchListener { _, motionEvent ->
            mGestureTap.onTouchEvent(motionEvent)
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> touchGestureDetect.reset(motionEvent.x, motionEvent.y)
                MotionEvent.ACTION_MOVE -> {
                    if (touchGestureDetect.processed) return@setOnTouchListener false
                    touchGestureDetect.updateMove(motionEvent)

                    if (abs(touchGestureDetect.offsetX) >= 68 || abs(touchGestureDetect.offsetY) >= 68) {
                        val gradient = touchGestureDetect.gradient
                        val gradientSign = sign(gradient)
                        val gradientAbs = abs(gradient)
                        val judgeGradient = 2 // about tan 64°
                        val reciprocalJudgeGradient = 1F / judgeGradient // about tan 26°
                        when (true) {
                            /* pull down */
                            gradientAbs >= judgeGradient -> {
                                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                            }
                            /* left-bottom or right-bottom */
                            gradientAbs < judgeGradient && gradientAbs > reciprocalJudgeGradient -> {
                                if (gradientSign == -1F) {
                                    /* left */
                                    println("left-bottom")
                                } else {
                                    /* right */
                                    println("right-bottom")
                                }
                            }
                            /* left or right */
                            gradientAbs <= reciprocalJudgeGradient -> {
                                if (gradientSign == -1F) {
                                    /* left */
                                    println("left")
                                } else {
                                    /* right */
                                    println("right")
                                }
                            }
                            else -> {}
                        }
                        touchGestureDetect.processed = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // maybe no need to update coordinate
                    touchGestureDetect.onActionUp(motionEvent)
                }
            }
            true
        }

        val lp = WindowManager.LayoutParams()
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        lp.width = (screenWidth * sharedPreferences!!.getInt("width", 40) / 100F).toInt()
        lp.height = statusBarHeight
        when (sharedPreferences!!.getString("gravity", "top_start")) {
            "top_start" -> lp.gravity = Gravity.TOP or Gravity.START
            "top_center" -> lp.gravity = Gravity.TOP or Gravity.CENTER
            "top_end" -> lp.gravity = Gravity.TOP or Gravity.END
        }

        val inflater = LayoutInflater.from(this)
        val statusBarView = inflater.inflate(R.layout.status_bar, mLayout)

        wm.addView(mLayout, lp)

        /* listen data from activity to update status bar */
        dataFromActivityManager.subscribe(object : DataFromActivityListener {
            override fun onUpdate(payload: DataFromActivityManager.Payload) {
                when (payload.type) {
                    "gravity" -> {
                        when (payload.value) {
                            -1 -> lp.gravity = Gravity.START or Gravity.TOP
                            0 -> lp.gravity = Gravity.CENTER or Gravity.TOP
                            1 -> lp.gravity = Gravity.END or Gravity.TOP
                        }
                        wm.updateViewLayout(mLayout, lp)
                    }
                    "preview", "background" -> {
                        val layout = statusBarView.findViewById<ConstraintLayout>(R.id.status_bar_layout)
                        if (payload.value == 1) {
                            layout.setBackgroundColor(Color.parseColor("#D37878"))
                        } else {
                            layout.setBackgroundColor(0)
                        }
                    }
                    "width" -> {
                        lp.width = (screenWidth * (payload.value) / 100F).toInt()
                        wm.updateViewLayout(mLayout, lp)
                    }
                }
            }
        })
    }

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        dataFromActivityManager.unsubscribe(null)
        sharedPreferences = null
    }

    companion object {
        val dataFromActivityManager = DataFromActivityManager()
    }
}

private class TouchGestureDetect(var x: Float = 0F, var y: Float = 0F) {
    var processed = false
    var maxDistanceX = 0F
    var maxDistanceY = 0F
    val maxDistance get() = max(maxDistanceX, maxDistanceY)
    var offsetX = 0F
    var offsetY = 0F
    var gradient = 0F
    var lastTapTimeMs: Long = 0

    fun reset(startX: Float = 0F, startY: Float = 0F) {
        x = startX
        y = startY
        maxDistanceX = 0F
        maxDistanceY = 0F

        processed = false
    }

    fun updateMove(motionEvent: MotionEvent) {
        offsetX = motionEvent.x - x
        offsetY = abs(motionEvent.y - y) // if swipe left(<-) y maybe negative then cause wrong sign

        maxDistanceX = max(abs(offsetX), maxDistanceX)
        maxDistanceY = max(abs(offsetY), maxDistanceY)

        val safeOffsetX = if (abs(offsetX) < 1) (sign(offsetX).takeUnless { it == 0F } ?: 1F) * floor(abs(offsetX) + 1) else offsetX
        val safeOffsetY = if (abs(offsetY) < 1) (sign(offsetY).takeUnless { it == 0F } ?: 1F) * floor(abs(offsetY) + 1) else offsetY
        gradient = safeOffsetY / safeOffsetX
    }

    fun onActionUp(motionEvent: MotionEvent): Boolean {
        return false
//        if (processed) return false
//        if (maxDistance > 5) return false
//        val delay = 300
//        val currentTimeMs = System.currentTimeMillis()
//        val last = lastTapTimeMs
//        lastTapTimeMs = currentTimeMs
//        if (currentTimeMs - last > delay) return false
//
//        println("Tap")
//
//        return true
    }
}

interface DataFromActivityListener {
    fun onUpdate(payload: DataFromActivityManager.Payload)
}

class DataFromActivityManager {
    private val observers: MutableList<DataFromActivityListener> = mutableListOf()

    fun subscribe(observer: DataFromActivityListener) {
        observers.add(observer)
    }

    fun unsubscribe(observer: DataFromActivityListener?) {
        observers.clear()
    }

    fun sendData(payload: Payload) {
        observers.forEach { it.onUpdate(payload) }
    }

    data class Payload(val type: String, val value: Int)
}