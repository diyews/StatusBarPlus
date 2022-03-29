package ws.diye.statusbarplus

import android.accessibilityservice.AccessibilityService
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.gson.Gson


class ActionCardView(context: Context, attrs: AttributeSet): FrameLayout(context) {
    private val actionType: ActionType
    private val actionTypeString: String
    private val actionTypePreferenceKey: String get() = "action_type_${actionTypePreferenceKeyArray[actionType.type]}"
    private val sharedPreferences: SharedPreferences
    private var actionData: ActionData
    private var actionLastModifiedMs: Long = 0
    private val gson = Gson()

    private fun initView() {
        inflate(context, R.layout.action_card_view, this)

        findViewById<LinearLayout>(R.id.layoutCardView)
            .setOnClickListener {
                val bottomSheetDialog = Dialog(context)
                bottomSheetDialog.setContentView(R.layout.bottom_sheet_dialog_gridview)
                val gridView = bottomSheetDialog.findViewById<GridView>(R.id.grid_view)!!
                val typeArray = arrayOf("Action", "App", "None")
                var dialogType = "choose_type"
                val actionArray = actionMap.toList().map { it.first }
                gridView.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, typeArray)
                gridView.setOnItemClickListener { _, _, i, _ ->
                    when(dialogType) {
                        "choose_type" ->
                            when(i) {
                                0 -> {
                                    gridView.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, actionArray)
                                    dialogType = "choose_action"
                                }
                                1 -> {
                                    val intent =
                                        Intent(context, AllInstalledAppActivity::class.java)
                                    intent.putExtra(
                                        "action_type_preference_key",
                                        actionTypePreferenceKey
                                    )
                                    context.startActivity(intent)
                                    bottomSheetDialog.dismiss()
                                }
                                2 -> {
                                    actionData.apply {
                                        type = ActionExecuteType.NONE
                                        lastModifiedTimeMs = System.currentTimeMillis()
                                    }

                                    sharedPreferences.edit {
                                        putString(actionTypePreferenceKey, gson.toJson(actionData))
                                    }
                                    bottomSheetDialog.dismiss()
                                }
                            }
                        "choose_action" -> {
                            actionData.apply {
                                type = ActionExecuteType.ACTION
                                actionValue = actionMap[actionArray[i]]!!
                                lastModifiedTimeMs = System.currentTimeMillis()
                            }
                            sharedPreferences.edit {
                                putString(actionTypePreferenceKey, gson.toJson(actionData))
                            }
                            bottomSheetDialog.dismiss()
                        }
                    }
                }
                bottomSheetDialog.show()
            }

        val textActionType = findViewById<TextView>(R.id.textActionType)
        textActionType.text = actionTypeString

        refreshView(true)
    }

    private fun refreshView(first: Boolean = false) {
        val textViewActionDisplay = findViewById<TextView>(R.id.textViewActionDisplay)
        val imageViewActionLeft = findViewById<ImageView>(R.id.imageViewActionLeft)
        if (!first) {
            sharedPreferences.getString(actionTypePreferenceKey, null)
                .also {
                    if (it == null) {
                        return
                    }

                    actionData = gson.fromJson(it, ActionData::class.java)
                        .apply {
                            if (lastModifiedTimeMs == actionLastModifiedMs) {
                                return
                            } else {
                                actionLastModifiedMs = lastModifiedTimeMs
                            }
                        }
                }
        }

        when(actionData.type) {
            ActionExecuteType.NONE -> {
                textViewActionDisplay.visibility = View.GONE
                imageViewActionLeft.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_add))
                imageViewActionLeft.visibility = View.VISIBLE
            }
            ActionExecuteType.ACTION -> {
                textViewActionDisplay.visibility = View.VISIBLE
                textViewActionDisplay.text = actionMap.filterValues { it == actionData.actionValue }.keys.first()
                imageViewActionLeft.visibility = View.GONE
            }
            ActionExecuteType.APP -> {
                val icon = context.packageManager.getApplicationIcon(actionData.packageId)
                imageViewActionLeft.setImageDrawable(icon)
            }
        }
    }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ActionCardView,
            0, 0).apply {

            try {
                actionType = ActionType.from(getInt(R.styleable.ActionCardView_actionType, 0))
                actionTypeString = actionTypeStringArray[actionType.type]
            } finally {
                recycle()
            }
        }

        sharedPreferences = getDefaultSharedPreferences(context)
        sharedPreferences.getString(actionTypePreferenceKey, null)
            .also {
                if (it == null) {
                    actionData = ActionData()
                    return@also
                }

                actionData = gson.fromJson(it, ActionData::class.java)
                actionLastModifiedMs = actionData.lastModifiedTimeMs
            }

        initView()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            refreshView()
        }
    }

    companion object {
        val actionTypeStringArray = arrayOf("Left", "Right", "Left-Bottom", "Right-Bottom")
        val actionTypePreferenceKeyArray = arrayOf("left", "right", "left_bottom", "right_bottom")
        private val actionMap = mutableMapOf(
            "Power" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            "Quick setting" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put("Split screen", AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                put("Screenshot", AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            putAll(
                mapOf(
                    "Volume Up" to CustomSwipeAction.VOLUME_UP,
                    "Volume Down" to CustomSwipeAction.VOLUME_DOWN,
                    "Mute Music" to CustomSwipeAction.MUTE_MUSIC_STREAM,
                )
            )
        }
    }
}


