package ws.diye.statusbarplus

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.view.accessibility.AccessibilityEvent

import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.*

import android.widget.FrameLayout
import kotlin.math.*
import android.view.MotionEvent

import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import java.lang.UnsupportedOperationException


class CoreAccessibilityService : AccessibilityService() {
    var mLayout: FrameLayout? = null
    private val touchGestureDetect = TouchGestureDetect()
    private lateinit var windowManager: WindowManager
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    lateinit var toast: Toast

    @SuppressLint("ClickableViewAccessibility", "ShowToast")
    override fun onServiceConnected() {
        super.onServiceConnected()

        toast = Toast.makeText(applicationContext, "", Toast.LENGTH_SHORT)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        var statusBarHeight = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }

        val mGestureTap = GestureDetector(applicationContext, object : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
                return true
            }
        })

        mLayout = FrameLayout(this)
        mLayout!!.setOnTouchListener(onTouchHandler(mGestureTap))

        val lp = WindowManager.LayoutParams()
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        lp.width = (getWidthOfWindowOrDisplay() * sharedPreferences!!.getInt("width", 40) / 100F).toInt()
        lp.height = statusBarHeight
        when (sharedPreferences!!.getString("gravity", "top_start")) {
            "top_start" -> lp.gravity = Gravity.TOP or Gravity.START
            "top_center" -> lp.gravity = Gravity.TOP or Gravity.CENTER
            "top_end" -> lp.gravity = Gravity.TOP or Gravity.END
        }

        val inflater = LayoutInflater.from(this)
        val statusBarView = inflater.inflate(R.layout.status_bar, mLayout)

        val wm = windowManager
        wm.addView(mLayout, lp)
        if (sharedPreferences!!.getBoolean("disabled_when_fullscreen", true)) {
            setupFollowStatusBarVisibilityListener(lp)
        }

        /* listen data from activity or self to update status bar */
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
                        /* -1 means update width because of orientation changed */
                        val width = payload.value.takeIf { it != -1 } ?: sharedPreferences!!.getInt("width", 40)
                        lp.width = (getWidthOfWindowOrDisplay() * (width / 100F)).toInt()
                        wm.updateViewLayout(mLayout, lp)
                    }
                    "disabled_when_fullscreen" -> {
                        if (payload.value == 1) {
                            setupFollowStatusBarVisibilityListener(lp)
                        } else {
                            removeFollowStatusBarVisibilityListener()
                        }
                    }
                }
            }
        })
    }

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        dataFromActivityManager.unsubscribe(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        dataFromActivityManager.sendData(DataFromActivityManager.Payload("width", -1))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onTouchHandler(mGestureTap: GestureDetector): View.OnTouchListener {
        return View.OnTouchListener { _, motionEvent ->
            mGestureTap.onTouchEvent(motionEvent)
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> touchGestureDetect.reset(motionEvent.x, motionEvent.y)
                MotionEvent.ACTION_MOVE -> {
                    if (touchGestureDetect.processed) return@OnTouchListener false
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
                                    /* left-bottom */
                                    executeSwipeAction("left_bottom")
                                } else {
                                    /* right-bottom */
                                    executeSwipeAction("right_bottom")
                                }
                            }
                            /* left or right */
                            gradientAbs <= reciprocalJudgeGradient -> {
                                if (gradientSign == -1F) {
                                    /* left */
                                    executeSwipeAction("left")
                                } else {
                                    /* right */
                                    executeSwipeAction("right")
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
    }

    private fun executeSwipeAction(direction: String) {
        sharedPreferences!!.getString("action_type_$direction", null)
            .let {
                if (it != null) {
                    val actionData = gson.fromJson(it, ActionData::class.java)
                    when (actionData.type) {
                        ActionExecuteType.ACTION -> {
                            when (actionData.actionValue) {
                                CustomSwipeAction.VOLUME_UP -> updateVolumeByStep()
                                CustomSwipeAction.VOLUME_DOWN -> updateVolumeByStep(-1)
                                CustomSwipeAction.MUTE_MUSIC_STREAM -> muteStream()
                                CustomSwipeAction.TOGGLE_KEEP_SCREEN_ON -> toggleKeepScreenOn()
                                else -> performGlobalAction(actionData.actionValue)
                            }
                        }
                        ActionExecuteType.APP -> {
                            val intent =
                                applicationContext.packageManager.getLaunchIntentForPackage(actionData.packageId)
                            if (intent != null) {
                                applicationContext.startActivity(intent)
                            }
                        }
                        else -> {}
                    }
                }
            }
    }

    private fun getWidthOfWindowOrDisplay(): Int {
        val wm = windowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var width = 0
            val right = wm.currentWindowMetrics.bounds.right
            try {
                width = display?.mode?.physicalWidth ?: 0
                if (width != 0 && width != right) {
                    width = display!!.mode!!.physicalHeight
                }
            } catch (e: UnsupportedOperationException) {
            }
            if (width == 0) {
                width = right
            }
            width
        } else {
            val displayMetrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    private fun setupFollowStatusBarVisibilityListener(lp: WindowManager.LayoutParams) {
        val wm = windowManager
        val mLayout = mLayout!!

        fun updateOverlayTouchable(touchable: Boolean): Boolean {
            val hasFlagNotTouchable = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

            if (touchable && hasFlagNotTouchable) {
                wm.updateViewLayout(
                    mLayout,
                    lp.apply { flags = flags xor WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE })
            } else if (!touchable && !hasFlagNotTouchable) {
                wm.updateViewLayout(
                    mLayout,
                    lp.apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE })
            }
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLayout.setOnApplyWindowInsetsListener { _, windowInsets ->
                val isStatusBarVisible = windowInsets.isVisible(WindowInsets.Type.statusBars())
                updateOverlayTouchable(isStatusBarVisible)
                windowInsets
            }
        } else {
            mLayout.setOnSystemUiVisibilityChangeListener {
                    visibility ->
                val isFullscreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                updateOverlayTouchable(!isFullscreen)
            }
        }
    }

    private fun removeFollowStatusBarVisibilityListener() {
        val mLayout = mLayout!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLayout.setOnApplyWindowInsetsListener(null)
        } else {
            mLayout.setOnSystemUiVisibilityChangeListener(null)
        }
    }

    companion object {
        val dataFromActivityManager = DataFromActivityManager()
    }
}

private fun CoreAccessibilityService.updateVolumeByStep(upOrDownSign: Int = 1) {
    (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .apply {
            val currentVolume = getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val step =
                (maxVolume / 20f)
                    .let { if (it < 1) 1 else it }
                    .toInt()
            val target = currentVolume + step * upOrDownSign
            if (target < 0 || target > maxVolume) return@apply

            setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            val text = resources.getString(if (upOrDownSign == 1) R.string.volume_up else R.string.volume_down) + " ($target/$maxVolume)"
            toast.setText(text)
            toast.show()
        }
}

private fun CoreAccessibilityService.muteStream(stream: Int = AudioManager.STREAM_MUSIC) {
    (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            } else {
                setStreamMute(stream, true);
            }
        }

    toast.setText(R.string.muted)
    toast.show()
}

private fun CoreAccessibilityService.toggleKeepScreenOn() {
    mLayout!!.keepScreenOn = !mLayout!!.keepScreenOn
    toast.setText(if (mLayout!!.keepScreenOn) R.string.turn_on_keep_screen_on else R.string.turn_off_keep_screen_on)
    toast.show()
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