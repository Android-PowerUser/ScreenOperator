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
    /**
     * Starts any Android Activity via an explicit Intent action, fired from the
     * AccessibilityService context (which is exempt from Android 10+ background
     * activity-start restrictions that block MainActivity-context starts).
     * @param action      Intent action, e.g. "android.settings.ACCESSIBILITY_SETTINGS"
     * @param extrasJson  JSON object of String->String extras. Pass "{}" for none.
     * @param data        Optional URI string. Pass "" to omit.
     */
    data class LaunchIntent(val action: String, val extrasJson: String, val data: String) : Command()
    /**
     * A two-finger pinch gesture, centered at (centerX, centerY), with both fingers moving
     * from startDistance apart to endDistance apart over durationMs. endDistance > startDistance
     * pinches out (zoom in); endDistance < startDistance pinches in (zoom out). Coordinates and
     * distances accept the same formats as other coordinate-based commands (pixels, or a
     * percentage string like "50%").
     */
    data class PinchGesture(
        val centerX: String,
        val centerY: String,
        val startDistance: String,
        val endDistance: String,
        val durationMs: Long
    ) : Command()
    data class Retrieve(val heading: String) : Command()
    data class WriteText(val text: String) : Command()
    /**
     * Copies [text] to the system clipboard. Requires no Android permission (clipboard access
     * is granted to every app by default), so - like the other entries in this "no extra
     * permission needed" group - it is exposed both as a native command (for the AI's own
     * text commands, via CommandParser) and as a WebView bridge method (`Android.copyToClipboard`)
     * so a custom-action-types.json entry can trigger it directly.
     */
    data class CopyToClipboard(val text: String) : Command()
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
