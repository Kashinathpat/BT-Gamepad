package com.bluetooth.gamepad

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ButtonConfig(
    val id: String,
    val label: String,
    val xFrac: Float,
    val yFrac: Float,
    val sizeFrac: Float
)

// Duplicated buttons get an "_N" suffix (e.g. "A_2"). The control type is the part before the
// first underscore. No palette id contains an underscore, so this is unambiguous. All id-based
// dispatch (input wiring, rendering, labels) must use the base id so duplicates behave like their
// originals.
val ButtonConfig.baseId: String
    get() = id.baseButtonId()

fun String.baseButtonId(): String = substringBefore('_')

/**
 * Mutable working state for one layout-editing session. Held by the host (MainActivity) so it
 * survives the editor leaving composition during a Test preview, and is discarded only when the
 * user leaves the editor entirely. Uses Compose snapshot state so the editor observes changes.
 */
class EditorSession(layout: ControllerLayout) {
    val buttons = mutableStateListOf<ButtonConfig>().also { it.addAll(layout.buttons) }
    val undoStack = mutableStateListOf<List<ButtonConfig>>()
    val redoStack = mutableStateListOf<List<ButtonConfig>>()
    val selectedId = mutableStateOf<String?>(null)
    val edited = mutableStateOf(false)
}

data class ControllerLayout(
    val id: String,
    val name: String,
    val buttons: List<ButtonConfig>
) {
    val isDefault get() = id == DEFAULT_ID

    companion object {
        const val DEFAULT_ID = "default"

        fun default() = ControllerLayout(
            id = DEFAULT_ID,
            name = "Standard",
            buttons = defaultButtons()
        )

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

class LayoutRepository(private val prefs: SharedPreferences) {

    fun getAll(): List<ControllerLayout> {
        val ids = prefs.getString("layout_ids", "") ?: ""
        val custom = if (ids.isBlank()) emptyList()
        else ids.split(",").mapNotNull { load(it) }
        return listOf(load(ControllerLayout.DEFAULT_ID) ?: ControllerLayout.default()) + custom
    }

    fun save(layout: ControllerLayout) {
        val json = JSONObject().apply {
            put("id", layout.id)
            put("name", layout.name)
            put("buttons", JSONArray().also { arr ->
                layout.buttons.forEach { b ->
                    arr.put(JSONObject().apply {
                        put("id", b.id)
                        put("label", b.label)
                        put("x", b.xFrac.toDouble())
                        put("y", b.yFrac.toDouble())
                        put("size", b.sizeFrac.toDouble())
                    })
                }
            })
        }
        val editor = prefs.edit().putString("layout_${layout.id}", json.toString())
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
        if (id == ControllerLayout.DEFAULT_ID) {
            val raw = prefs.getString("layout_${ControllerLayout.DEFAULT_ID}", null)
                ?: return ControllerLayout.default()
            return try {
                val json = JSONObject(raw)
                val arr = json.getJSONArray("buttons")
                val btns = (0 until arr.length()).map { i ->
                    val b = arr.getJSONObject(i)
                    ButtonConfig(
                        id       = b.getString("id"),
                        label    = b.getString("label"),
                        xFrac    = b.getDouble("x").toFloat(),
                        yFrac    = b.getDouble("y").toFloat(),
                        sizeFrac = b.getDouble("size").toFloat()
                    )
                }
                ControllerLayout(ControllerLayout.DEFAULT_ID, json.getString("name"), btns)
            } catch (_: Exception) { ControllerLayout.default() }
        }
        val raw = prefs.getString("layout_${id}", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val arr = json.getJSONArray("buttons")
            val buttons = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                ButtonConfig(
                    id      = b.getString("id"),
                    label   = b.getString("label"),
                    xFrac   = b.getDouble("x").toFloat(),
                    yFrac   = b.getDouble("y").toFloat(),
                    sizeFrac = b.getDouble("size").toFloat()
                )
            }
            ControllerLayout(json.getString("id"), json.getString("name"), buttons)
        } catch (_: Exception) { null }
    }

    fun newCustom(name: String): ControllerLayout {
        return ControllerLayout(
            id = UUID.randomUUID().toString(),
            name = name,
            buttons = ControllerLayout.defaultButtons()
        )
    }
}
