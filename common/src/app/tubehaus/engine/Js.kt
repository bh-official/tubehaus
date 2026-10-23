package app.tubehaus.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Small helpers for walking YouTube's JSON. Its responses are deep, change shape often and
// mix old "renderers" with newer "view models", so every read here is forgiving: a missing
// key gives null instead of an exception, and the parsers decide what is essential.

val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun parse(s: String): JsonElement = json.parseToJsonElement(s)

/** obj["a"]["b"][0]... with dots; numbers index arrays. */
fun JsonElement?.at(path: String): JsonElement? {
    var cur = this
    for (k in path.split('.')) {
        cur = when (cur) {
            is JsonObject -> cur[k]
            is JsonArray -> k.toIntOrNull()?.let { cur.getOrNull(it) }
            else -> return null
        } ?: return null
    }
    return cur
}

val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

val JsonElement?.long: Long?
    get() = str?.toLongOrNull() ?: str?.toDoubleOrNull()?.toLong()

val JsonElement?.int: Int? get() = long?.toInt()

val JsonElement?.bool: Boolean get() = str == "true"

val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()

val JsonElement?.obj: JsonObject? get() = this as? JsonObject

/** The text of a YouTube text object: {simpleText}, {runs:[{text}]} or {content}. */
val JsonElement?.text: String?
    get() {
        val o = this as? JsonObject ?: return str
        o["simpleText"].str?.let { return it }
        o["content"].str?.let { return it }
        val runs = o["runs"].arr
        if (runs.isNotEmpty()) return runs.joinToString("") { it.at("text").str ?: "" }
        return null
    }

/** Every value stored under [key], anywhere below this element (depth first, document order). */
fun JsonElement.findAll(key: String, skip: Set<String> = NOISE, limit: Int = Int.MAX_VALUE): List<JsonElement> {
    val out = ArrayList<JsonElement>()
    fun walk(e: JsonElement) {
        if (out.size >= limit) return
        when (e) {
            is JsonObject -> for ((k, v) in e) {
                if (k == key) out += v
                else if (k !in skip) walk(v)
            }
            is JsonArray -> for (x in e) walk(x)
            else -> {}
        }
    }
    walk(this)
    return out
}

fun JsonElement.findFirst(key: String, skip: Set<String> = NOISE): JsonElement? = findAll(key, skip, 1).firstOrNull()

/** Branches that only hold menus, sheets and logging - never content we show. */
val NOISE = setOf(
    "trackingParams", "loggingDirectives", "menu", "menuButton", "sheetViewModel", "topbar",
    "frameworkUpdates", "responseContext", "contextMenu", "overlay", "hotkeyDialog", "microformat",
    "playerOverlays", "onResponseReceivedEndpoints_unused",
)

/** "1.2M views" / "1,234 views" / "12K" -> a number (0 when it is not one). */
fun countOf(s: String?): Long {
    if (s.isNullOrBlank()) return 0
    val m = Regex("""([\d.,]+)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(s) ?: return 0
    val raw = m.groupValues[1]
    val mult = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
    val n = if (mult == 1.0) raw.replace(",", "").replace(".", "").toDoubleOrNull()
    else raw.replace(",", ".").toDoubleOrNull()
    return ((n ?: 0.0) * mult).toLong()
}

/** "1:02:03" -> 3723 */
fun secondsOf(s: String?): Int {
    if (s.isNullOrBlank()) return 0
    val parts = s.trim().split(':').map { it.trim().toIntOrNull() ?: return 0 }
    return parts.fold(0) { acc, p -> acc * 60 + p }
}
