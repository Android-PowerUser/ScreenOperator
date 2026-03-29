package com.google.ai.sample.util

/**
 * Sealed class representing different types of commands.
 */
sealed class Command {
    data class ClickButton(val buttonText: String) : Command()
    data class LongClickButton(val buttonText: String) : Command()
    data class TapCoordinates(val x: String, val y: String) : Command()
    object TakeScreenshot : Command()
    object PressHomeButton : Command()
    object PressBackButton : Command()
    object ShowRecentApps : Command()
    object ScrollDown : Command()
    object ScrollUp : Command()
    object ScrollLeft : Command()
    object PressEnterKey : Command()
    object ScrollRight : Command()
    data class ScrollDownFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()
    data class ScrollUpFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()
    data class ScrollLeftFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()
    data class ScrollRightFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()
    data class OpenApp(val packageName: String) : Command()
    data class WriteText(val text: String) : Command()
    object UseHighReasoningModel : Command()
    object UseLowReasoningModel : Command()
}
