package ws.diye.statusbarplus

enum class ActionType(val type: Int) {
    LEFT(0), RIGHT(1), LEFT_BOTTOM(2), RIGHT_BOTTOM(3);

    companion object {
        fun from(type: Int): ActionType {
            return when(type) {
                0 -> LEFT
                1 -> RIGHT
                2 -> LEFT_BOTTOM
                3 -> RIGHT_BOTTOM
                else -> LEFT
            }
        }
    }
}

enum class ActionExecuteType(type: Int) {
    NONE(0), ACTION(1), APP(2)
}

data class ActionData(var type: ActionExecuteType = ActionExecuteType.NONE,
                      var actionValue: Int = 0,
                      var packageId: String = "",
                      var lastModifiedTimeMs: Long = 0
)

class CustomSwipeAction {
    companion object {
        const val VOLUME_UP = 100001
        const val VOLUME_DOWN = 100002
        const val MUTE_MUSIC_STREAM = 100003
    }
}