package com.bluetooth.gamepad.layout

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

enum class ControlShape(val title: String) { CIRCLE("Circle"), ROUNDED("Rounded"), PILL("Pill"), OUTLINE("Outline") }

enum class ButtonBehavior(val title: String) { NORMAL("Normal"), TOGGLE("Toggle"), TURBO("Turbo") }

val TURBO_RATES = listOf(5, 10, 20)
const val MIN_OPACITY = 0.2f
const val MAX_LABEL_LENGTH = 10
const val MAX_CONTROLS = 64
const val MIN_CONTROL_SIZE = 0.04f
const val MAX_CONTROL_SIZE = 0.6f
const val MIN_POSITION = 0.02f
const val MAX_POSITION = 0.98f
const val MAX_UNDO_STEPS = 50

data class ButtonConfig(
    val id: String,
    val label: String,
    val xFrac: Float,
    val yFrac: Float,
    val sizeFrac: Float,
    val opacity: Float = 1f,
    // null keeps the control's own default shape.
    val shape: ControlShape? = null,
    val behavior: ButtonBehavior = ButtonBehavior.NORMAL,
    val turboHz: Int = 10,
    val macro: List<String> = emptyList(),
    val floating: Boolean = false,
    val gyroGate: Boolean = false
)

// Buttons a macro can press; also the controls that take label/shape/behaviour options.
val PRESS_IDS = listOf("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "LSB", "RSB", "SELECT", "START")

val ButtonConfig.isPressable: Boolean get() = baseId in PRESS_IDS || baseId == "MACRO"

val ButtonConfig.isStick: Boolean get() = baseId == "LSTICK" || baseId == "RSTICK"

// Duplicated buttons get an "_N" suffix (e.g. "A_2"). The control type is the part before the
// first underscore. No palette id contains an underscore, so this is unambiguous. All id-based
// dispatch (input wiring, rendering, labels) must use the base id so duplicates behave like their
// originals.
val ButtonConfig.baseId: String
    get() = id.baseButtonId()

fun String.baseButtonId(): String = substringBefore('_')

// Working state and edit rules for one layout-editing session. Held by the host so it survives the
// editor leaving composition during a Test preview, and discarded only when the editor is left.
class EditorSession(layout: ControllerLayout) {
    val buttons = mutableStateListOf<ButtonConfig>().also { it.addAll(layout.buttons) }
    val undoStack = mutableStateListOf<List<ButtonConfig>>()
    val redoStack = mutableStateListOf<List<ButtonConfig>>()
    val selectedId = mutableStateOf<String?>(null)
    val edited = mutableStateOf(false)
    // Button whose label is being typed; consecutive keystrokes on it share one undo step.
    private var labelEditId: String? = null

    fun select(id: String?) {
        selectedId.value = id
        labelEditId = null
    }

    fun endLabelEdit() {
        labelEditId = null
    }

    fun pushUndo() {
        labelEditId = null
        if (undoStack.size >= MAX_UNDO_STEPS) undoStack.removeAt(0)
        undoStack.add(buttons.toList())
        redoStack.clear()
        edited.value = true
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        labelEditId = null
        redoStack.add(buttons.toList())
        val prev = undoStack.removeAt(undoStack.lastIndex)
        buttons.clear()
        buttons.addAll(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        labelEditId = null
        if (undoStack.size >= MAX_UNDO_STEPS) undoStack.removeAt(0)
        undoStack.add(buttons.toList())
        val next = redoStack.removeAt(redoStack.lastIndex)
        buttons.clear()
        buttons.addAll(next)
    }

    fun addElement(template: ButtonConfig) {
        pushUndo()
        // The bare template id for the first instance, then "<id>_N" for duplicates. Probing for the
        // next free suffix means deleting an earlier duplicate can never produce a colliding id.
        val existingIds = buttons.mapTo(HashSet()) { it.id }
        val newId = if (template.id !in existingIds) {
            template.id
        } else {
            var n = 2
            while ("${template.id}_$n" in existingIds) n++
            "${template.id}_$n"
        }
        buttons.add(template.copy(id = newId, xFrac = 0.5f, yFrac = 0.5f))
        selectedId.value = newId
    }

    fun deleteElement(id: String) {
        pushUndo()
        buttons.removeAll { it.id == id }
        if (selectedId.value == id) selectedId.value = null
    }

    fun changeSize(id: String, delta: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i >= 0) {
            pushUndo()
            val cur = buttons[i]
            buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac + delta).coerceIn(MIN_CONTROL_SIZE, MAX_CONTROL_SIZE))
        }
    }

    fun changePosition(id: String, dx: Float, dy: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i >= 0) {
            pushUndo()
            val cur = buttons[i]
            buttons[i] = cur.copy(
                xFrac = (cur.xFrac + dx).coerceIn(MIN_POSITION, MAX_POSITION),
                yFrac = (cur.yFrac + dy).coerceIn(MIN_POSITION, MAX_POSITION)
            )
        }
    }

    fun updateButton(next: ButtonConfig) {
        val i = buttons.indexOfFirst { it.id == next.id }
        if (i < 0 || buttons[i] == next) return
        pushUndo()
        buttons[i] = next
    }

    fun changeLabel(id: String, text: String) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0) return
        if (labelEditId != id) {
            pushUndo()
            labelEditId = id
        }
        buttons[i] = buttons[i].copy(label = text)
    }

    // Moves freely during a drag; snapToGrid runs on release so small moves are not pulled back mid-drag.
    fun dragBy(id: String, dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0 || widthPx <= 0 || heightPx <= 0) return
        val cur = buttons[i]
        buttons[i] = cur.copy(
            xFrac = (cur.xFrac + dxPx / widthPx).coerceIn(MIN_POSITION, MAX_POSITION),
            yFrac = (cur.yFrac + dyPx / heightPx).coerceIn(MIN_POSITION, MAX_POSITION)
        )
    }

    fun scaleBy(id: String, zoom: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = buttons[i]
        buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac * zoom).coerceIn(MIN_CONTROL_SIZE, MAX_CONTROL_SIZE))
    }

    fun snapToGrid(id: String, widthPx: Float, heightPx: Float, stepPx: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0 || widthPx <= 0 || heightPx <= 0) return
        val cur = buttons[i]
        val cx = (cur.xFrac * widthPx / stepPx).roundToInt() * stepPx
        val cy = (cur.yFrac * heightPx / stepPx).roundToInt() * stepPx
        buttons[i] = cur.copy(
            xFrac = (cx / widthPx).coerceIn(MIN_POSITION, MAX_POSITION),
            yFrac = (cy / heightPx).coerceIn(MIN_POSITION, MAX_POSITION)
        )
    }
}

data class ControllerLayout(
    val id: String,
    val name: String,
    val buttons: List<ButtonConfig>
) {
    val isDefault get() = id == DEFAULT_ID

    companion object {
        const val DEFAULT_ID = "default"

        fun default() = ControllerLayout(DEFAULT_ID, "Standard", defaultButtons())

        fun defaultButtons() = listOf(
            ButtonConfig("A",      "A",       0.82f, 0.78f, 0.09f),
            ButtonConfig("B",      "B",       0.90f, 0.68f, 0.09f),
            ButtonConfig("X",      "X",       0.74f, 0.68f, 0.09f),
            ButtonConfig("Y",      "Y",       0.82f, 0.58f, 0.09f),
            ButtonConfig("LB",     "LB",      0.10f, 0.10f, 0.10f),
            ButtonConfig("LT",     "LT",      0.10f, 0.22f, 0.10f),
            ButtonConfig("RB",     "RB",      0.90f, 0.10f, 0.10f),
            ButtonConfig("RT",     "RT",      0.90f, 0.22f, 0.10f),
            ButtonConfig("LSB",    "LSB",     0.05f, 0.50f, 0.07f),
            ButtonConfig("RSB",    "RSB",     0.95f, 0.50f, 0.07f),
            ButtonConfig("SELECT", "SELECT",  0.42f, 0.88f, 0.08f),
            ButtonConfig("START",  "START",   0.58f, 0.88f, 0.08f),
            ButtonConfig("LSTICK", "L",       0.22f, 0.72f, 0.18f),
            ButtonConfig("RSTICK", "R",       0.62f, 0.72f, 0.18f),
            ButtonConfig("DPAD",   "D",       0.38f, 0.72f, 0.18f)
        )
    }
}


private val CONTROL_IDS = setOf(
    "A", "B", "X", "Y", "DPAD", "LSTICK", "RSTICK", "TPADL", "TPADR", "TPADD",
    "LB", "RB", "LT", "RT", "LSB", "RSB", "SELECT", "START", "MACRO"
)

private val CONTROL_ID_PATTERN = Regex("^[A-Z]+(_[0-9]+)?$")

// NaN would pass coerceIn untouched and crash layout maths later.
private fun JSONObject.finite(key: String): Float =
    getDouble(key).toFloat().also { require(it.isFinite()) }

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

// 4 decimals is sub-pixel on any screen and keeps float noise out of the JSON.
private fun Float.rounded(): Double = Math.round(this * 10000.0) / 10000.0

fun ControllerLayout.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("buttons", JSONArray().also { arr ->
        buttons.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("label", b.label)
                put("x", b.xFrac.rounded())
                put("y", b.yFrac.rounded())
                put("size", b.sizeFrac.rounded())
                if (b.opacity < 1f) put("opacity", b.opacity.rounded())
                b.shape?.let { put("shape", it.name) }
                if (b.behavior != ButtonBehavior.NORMAL) put("behavior", b.behavior.name)
                if (b.behavior == ButtonBehavior.TURBO) put("turboHz", b.turboHz)
                if (b.macro.isNotEmpty()) put("macro", JSONArray(b.macro))
                if (b.floating) put("floating", true)
                if (b.gyroGate) put("gyroGate", true)
            })
        }
    })
}

// Throws on anything malformed; imported text is untrusted, so ids and geometry are validated.
private fun layoutFromJson(json: JSONObject, id: String): ControllerLayout {
    val arr = json.getJSONArray("buttons")
    val seen = HashSet<String>()
    val buttons = (0 until arr.length()).map { i ->
        val b = arr.getJSONObject(i)
        val bid = b.getString("id")
        require(CONTROL_ID_PATTERN.matches(bid) && bid.baseButtonId() in CONTROL_IDS && seen.add(bid))
        ButtonConfig(
            id       = bid,
            label    = b.optString("label", bid.baseButtonId()).take(MAX_LABEL_LENGTH),
            xFrac    = b.finite("x").coerceIn(0f, 1f),
            yFrac    = b.finite("y").coerceIn(0f, 1f),
            sizeFrac = b.finite("size").coerceIn(0.03f, 0.8f),
            opacity  = b.optDouble("opacity", 1.0).toFloat().takeUnless { it.isNaN() }?.coerceIn(MIN_OPACITY, 1f) ?: 1f,
            shape    = enumOrNull<ControlShape>(b.optString("shape")),
            behavior = enumOrNull<ButtonBehavior>(b.optString("behavior")) ?: ButtonBehavior.NORMAL,
            turboHz  = b.optInt("turboHz", 10).takeIf { it in TURBO_RATES } ?: 10,
            macro    = b.optJSONArray("macro")?.let { m ->
                (0 until m.length()).map { m.optString(it) }.filter { it in PRESS_IDS }.distinct()
            } ?: emptyList(),
            floating = b.optBoolean("floating", false),
            gyroGate = b.optBoolean("gyroGate", false)
        )
    }
    val name = json.getString("name").trim().take(40)
    require(name.isNotEmpty())
    return ControllerLayout(id, name, buttons)
}

class LayoutRepository(private val prefs: SharedPreferences) {

    fun getAll(): List<ControllerLayout> {
        val ids = prefs.getString("layout_ids", "") ?: ""
        val custom = if (ids.isBlank()) emptyList()
        else ids.split(",").mapNotNull { load(it) }
        return listOf(load(ControllerLayout.DEFAULT_ID) ?: ControllerLayout.default()) + custom
    }

    fun save(layout: ControllerLayout) {
        val editor = prefs.edit().putString("layout_${layout.id}", layout.toJson().toString())
        if (!layout.isDefault) {
            val ids = prefs.getString("layout_ids", "") ?: ""
            val list = if (ids.isBlank()) mutableListOf() else ids.split(",").toMutableList()
            if (!list.contains(layout.id)) {
                list.add(layout.id)
                editor.putString("layout_ids", list.joinToString(","))
            }
        }
        editor.apply()
    }

    fun delete(id: String) {
        if (id == ControllerLayout.DEFAULT_ID) return
        val ids = prefs.getString("layout_ids", "") ?: ""
        val list = if (ids.isBlank()) mutableListOf() else ids.split(",").toMutableList()
        list.remove(id)
        prefs.edit()
            .remove("layout_${id}")
            .putString("layout_ids", list.joinToString(","))
            .apply()
    }

    fun load(id: String): ControllerLayout? {
        val raw = prefs.getString("layout_${id}", null)
        val parsed = raw?.let { parse(it, id) }
        return parsed ?: if (id == ControllerLayout.DEFAULT_ID) ControllerLayout.default() else null
    }

    private fun parse(raw: String, id: String): ControllerLayout? =
        try { layoutFromJson(JSONObject(raw), id) } catch (_: Exception) { null }

    fun newCustom(name: String): ControllerLayout =
        ControllerLayout(UUID.randomUUID().toString(), name, ControllerLayout.defaultButtons())

    fun duplicate(layout: ControllerLayout): ControllerLayout =
        layout.copy(id = UUID.randomUUID().toString(), name = "${layout.name} copy".take(40))
            .also { save(it) }

    fun importJson(text: String): ControllerLayout? =
        // A leading UTF-8 byte order mark is not valid JSON.
        parse(text.removePrefix("\uFEFF").trim(), UUID.randomUUID().toString())?.takeIf { it.buttons.size in 1..MAX_CONTROLS }?.also { save(it) }
}
