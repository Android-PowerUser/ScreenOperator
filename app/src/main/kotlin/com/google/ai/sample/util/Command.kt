package com.google.ai.sample.util

/**
 * Sealed class representing different types of commands.
 */
sealed class Command {
    data class ClickButton(val buttonText: String) : Command()
    data class LongClickButton(val buttonText: String) : Command()
    data class TapCoordinates(val x: String, val y: String) : Command()
    object TakeScreenshot : Command()
    object Completed : Command()
    data class Wait(val seconds: Long) : Command()
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
    data class Retrieve(val heading: String) : Command()
    data class WriteText(val text: String) : Command()
    data class TermuxCommand(val command: String) : Command()
    object UseHighReasoningModel : Command()
    object UseLowReasoningModel : Command()
    /**
     * A custom action type defined entirely in the remote WebView bundle
     * (`custom-action-types.json`). When executed, the native accessibility service calls
     * `window.onCustomAction(id, groups[])` in JavaScript so the WebView handler can carry
     * out the actual work using existing `Android.*` bridge methods.
     *
     * @param id     The `id` field from the matching custom-action-types entry.
     * @param groups The regex capture groups (index 0 = first capture group).
     */
    data class WebViewCustomAction(val id: String, val groups: List<String>) : Command()
}
