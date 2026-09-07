package com.google.ai.sample.util

/** Native fallback UI string helper. Override plumbing moved out; call sites pass defaults. */
internal object UiStringsConfig {
    fun get(id: String, default: String): String = default

    fun get(id: String, default: String, vararg args: Any?): String {
        var text = default
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }
}
