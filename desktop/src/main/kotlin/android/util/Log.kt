package android.util

import java.io.PrintWriter
import java.io.StringWriter

/** Android Log, printed to stderr. */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @JvmStatic fun v(tag: String?, msg: String?): Int = println(VERBOSE, tag, msg)

    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = println(VERBOSE, tag, join(msg, tr))

    @JvmStatic fun d(tag: String?, msg: String?): Int = println(DEBUG, tag, msg)

    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = println(DEBUG, tag, join(msg, tr))

    @JvmStatic fun i(tag: String?, msg: String?): Int = println(INFO, tag, msg)

    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = println(INFO, tag, join(msg, tr))

    @JvmStatic fun w(tag: String?, msg: String?): Int = println(WARN, tag, msg)

    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = println(WARN, tag, join(msg, tr))

    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = println(WARN, tag, getStackTraceString(tr))

    @JvmStatic fun e(tag: String?, msg: String?): Int = println(ERROR, tag, msg)

    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = println(ERROR, tag, join(msg, tr))

    @JvmStatic fun wtf(tag: String?, msg: String?): Int = println(ASSERT, tag, msg)

    @JvmStatic fun wtf(tag: String?, msg: String?, tr: Throwable?): Int = println(ASSERT, tag, join(msg, tr))

    @JvmStatic fun wtf(tag: String?, tr: Throwable?): Int = println(ASSERT, tag, getStackTraceString(tr))

    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = level >= INFO

    @JvmStatic fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) return ""
        val writer = StringWriter()
        PrintWriter(writer).use { tr.printStackTrace(it) }
        return writer.toString().trimEnd()
    }

    /** Returns characters written. */
    @JvmStatic fun println(priority: Int, tag: String?, msg: String?): Int {
        val line = "${letter(priority)}/${tag ?: "null"}: ${msg ?: "null"}"
        System.err.println(line)
        return line.length + 1
    }

    private fun join(msg: String?, tr: Throwable?): String {
        val trace = getStackTraceString(tr)
        return if (trace.isEmpty()) msg ?: "null" else "${msg ?: "null"}\n$trace"
    }

    private fun letter(priority: Int): Char = when (priority) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        else -> 'A'
    }
}
