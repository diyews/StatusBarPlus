package ws.diye.statusbarplus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.provider.Settings

import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.core.content.edit
import androidx.preference.PreferenceManager.getDefaultSharedPreferences


class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val intent = Intent(this, AllInstalledAppActivity::class.java)

        setupSharedPreferences()
        setupWidgetListeners()
    }

    override fun onResume() {
        super.onResume()
        updateButtonTurnOn()
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getDefaultSharedPreferences(applicationContext)

        val gravity = sharedPreferences.getString("gravity", null)
        if (gravity == null) {
            sharedPreferences.edit(true) {
                putString("gravity", "top_start")
                putInt("width", 40)
            }
        }
    }

    private fun setupWidgetListeners() {
        val checkBoxPreview = findViewById<CheckBox>(R.id.checkbox_preview)
        checkBoxPreview.setOnCheckedChangeListener { _, checked -> sendDataToService("preview", if (checked) 1 else 0) }

        val radioGroup = findViewById<RadioGroup>(R.id.radio_group)
        when (sharedPreferences.getString("gravity", "top_start")) {
            "top_start" -> radioGroup.check(R.id.radio_start)
            "top_center" -> radioGroup.check(R.id.radio_center)
            "top_end" -> radioGroup.check(R.id.radio_end)
        }
        radioGroup.setOnCheckedChangeListener { _, value ->
            run {
                val gravityValue: Int
                val preferenceValue: String
                when (value) {
                    R.id.radio_start -> {
                        gravityValue = -1
                        preferenceValue = "top_start"
                    }
                    R.id.radio_center -> {
                        gravityValue = 0
                        preferenceValue = "top_center"
                    }
                    R.id.radio_end -> {
                        gravityValue = 1
                        preferenceValue = "top_end"
                    }
                    else -> return@run
                }
                sendDataToService("gravity", gravityValue)
                sharedPreferences.edit {
                    putString("gravity", preferenceValue)
                }
            }
        }

        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progress = sharedPreferences.getInt("width", 40)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                sendDataToService("width", p1)
                sharedPreferences.edit {
                    putInt("width", p1)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })
    }

    private fun updateButtonTurnOn() {
        val isServiceOn = isAccessibilitySettingsOn(applicationContext)
        val buttonTurnOn = findViewById<Button>(R.id.button_turn_on)
        if (isServiceOn) {
            buttonTurnOn.text = resources.getString(R.string.running)
            buttonTurnOn.setOnClickListener(null)
        } else {
            buttonTurnOn.text = resources.getString(R.string.turn_on_service)
            buttonTurnOn.setOnClickListener {
                run {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }

    private fun isAccessibilitySettingsOn(mContext: Context): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + CoreAccessibilityService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: SettingNotFoundException) {
            println("Error finding setting, default accessibility to not found: "
                        + e.message
            )
        }
        val mStringColonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } else {
            println("***ACCESSIBILITY IS DISABLED***")
        }
        return false
    }

    fun getStatusBarHeight() {
        val rectangle = Rect()
        val window: Window = window
        window.decorView.getWindowVisibleDisplayFrame(rectangle)
        val statusBarHeight: Int = rectangle.top
    }

    private fun sendDataToService(type: String, value: Int) {
        CoreAccessibilityService.dataFromActivityManager.sendData(DataFromActivityManager.Payload(type, value))
    }
}