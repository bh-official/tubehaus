package app.tubehaus.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference

/**
 * The few libmpv functions the player needs (client API, libmpv-2.dll).
 * See mpv's client.h; handles are opaque pointers, strings are UTF-8.
 */
@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: DoubleByReference): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_command_async(ctx: Pointer, userdata: Long, args: Array<String?>): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String

    companion object {
        const val FORMAT_DOUBLE = 5
        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_ERROR = 4

        fun load(): LibMpv? = try {
            Native.load("mpv-2", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        } catch (e: UnsatisfiedLinkError) {
            try { Native.load("mpv", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")) } catch (e2: UnsatisfiedLinkError) { null }
        }
    }
}
