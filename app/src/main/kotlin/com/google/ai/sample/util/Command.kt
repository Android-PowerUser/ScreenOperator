package com.google.ai.sample.util

/**
 * Sealed class representing different types of commands.
 *
 * Commands are now fully JSON-configurable via command-builtins.json.
 * The CommandParser loads its patterns from JSON, not from hardcoded Kotlin.
 *
 * The only commands that remain as sealed subtypes are those that the native
 * AccessibilityService needs to handle with platform-specific code.
 * WebView-only actions (Retrieve, model switching, popUp) are now handled
 * exclusively through Command.WebViewCustomAction via custom-action-types.json.
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
    /** Starts any Android Activity via an explicit Intent */
    data class LaunchIntent(val action: String, val extrasJson: String, val data: String) : Command()
    /** Two-finger pinch gesture */
    data class PinchGesture(
        val centerX: String,
        val centerY: String,
        val startDistance: String,
        val endDistance: String,
        val durationMs: Long
    ) : Command()
    data class WriteText(val text: String) : Command()
    /** Copies text to system clipboard. No Android permission needed. */
    data class CopyToClipboard(val text: String) : Command()
    data class TermuxCommand(val command: String) : Command()
    // ── REMOVED: UseHighReasoningModel, UseLowReasoningModel (redundant – JS uses setSelectedModel)
    // ── REMOVED: Retrieve (now handled via WebViewCustomAction in JS)
    /**
     * A custom action defined in the remote WebView bundle (custom-action-types.json or command-builtins.json).
     * When executed, the native accessibility service calls window.onCustomAction(id, groups[]) in JS.
     */
    data class WebViewCustomAction(val id: String, val groups: List<String>) : Command()
}
