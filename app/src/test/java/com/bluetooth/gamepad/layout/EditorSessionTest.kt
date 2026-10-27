package com.bluetooth.gamepad.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {

    private fun session() = EditorSession(ControllerLayout.default())

    private fun EditorSession.button(id: String) = buttons.first { it.id == id }

    private fun EditorSession.ids() = buttons.map { it.id }

    @Test
    fun undoRestoresTheStateBeforeTheEdit() {
        val s = session()
        s.changeSize("A", 0.05f)
        assertEquals(0.14f, s.button("A").sizeFrac, 1e-6f)
        s.undo()
        assertEquals(0.09f, s.button("A").sizeFrac, 1e-6f)
    }

    @Test
    fun redoReappliesAnUndoneEdit() {
        val s = session()
        s.changeSize("A", 0.05f)
        s.undo()
        s.redo()
        assertEquals(0.14f, s.button("A").sizeFrac, 1e-6f)
        assertTrue(s.redoStack.isEmpty())
    }

    @Test
    fun aNewEditClearsTheRedoHistory() {
        val s = session()
        s.changeSize("A", 0.05f)
        s.undo()
        assertEquals(1, s.redoStack.size)
        s.changeSize("B", 0.01f)
        assertTrue(s.redoStack.isEmpty())
    }

    @Test
    fun undoAndRedoWithNothingToDoChangeNothing() {
        val s = session()
        val before = s.buttons.toList()
        s.undo()
        s.redo()
        assertEquals(before, s.buttons.toList())
        assertTrue(s.undoStack.isEmpty())
        assertTrue(s.redoStack.isEmpty())
    }

    @Test
    fun historyKeepsTheLast50Steps() {
        val s = session()
        repeat(60) { s.changeSize("A", 0.001f) }
        assertEquals(50, s.undoStack.size)
        repeat(50) { s.undo() }
        assertTrue(s.undoStack.isEmpty())
        assertEquals(0.09f + 0.01f, s.button("A").sizeFrac, 1e-4f)
    }

    @Test
    fun editsMarkTheLayoutEditedButUndoAndRedoDoNot() {
        val s = session()
        assertFalse(s.edited.value)
        s.changeSize("A", 0.01f)
        assertTrue(s.edited.value)
        s.edited.value = false
        s.undo()
        assertFalse(s.edited.value)
        s.redo()
        assertFalse(s.edited.value)
    }

    @Test
    fun addingUsesTheBareIdThenNumberedSuffixes() {
        val s = session()
        s.deleteElement("A")
        val template = ControllerLayout.defaultButtons().first { it.id == "A" }
        s.addElement(template)
        assertEquals("A", s.selectedId.value)
        s.addElement(template)
        assertEquals("A_2", s.selectedId.value)
        s.addElement(template)
        assertEquals("A_3", s.selectedId.value)
    }

    @Test
    fun addingProbesForTheNextFreeSuffix() {
        val s = session()
        val template = ControllerLayout.defaultButtons().first { it.id == "A" }
        s.addElement(template)
        s.addElement(template)
        assertEquals(listOf("A", "A_2", "A_3"), s.ids().filter { it.startsWith("A") })
        s.deleteElement("A_2")
        s.addElement(template)
        assertEquals("A_2", s.selectedId.value)
        assertEquals(1, s.ids().count { it == "A_3" })
    }

    @Test
    fun anAddedElementIsCentredAndSelected() {
        val s = session()
        s.addElement(ControllerLayout.defaultButtons().first { it.id == "A" })
        val added = s.button("A_2")
        assertEquals(0.5f, added.xFrac, 0f)
        assertEquals(0.5f, added.yFrac, 0f)
        assertEquals("A_2", s.selectedId.value)
        assertEquals(16, s.buttons.size)
    }

    @Test
    fun deletingRemovesTheButtonAndClearsItsSelection() {
        val s = session()
        s.selectedId.value = "X"
        s.deleteElement("X")
        assertFalse("X" in s.ids())
        assertNull(s.selectedId.value)
        s.undo()
        assertTrue("X" in s.ids())
    }

    @Test
    fun deletingAnotherButtonKeepsTheSelection() {
        val s = session()
        s.selectedId.value = "X"
        s.deleteElement("Y")
        assertEquals("X", s.selectedId.value)
    }

    @Test
    fun sizeChangesAreClamped() {
        val s = session()
        s.changeSize("A", 5f)
        assertEquals(0.6f, s.button("A").sizeFrac, 0f)
        s.changeSize("A", -5f)
        assertEquals(0.04f, s.button("A").sizeFrac, 0f)
    }

    @Test
    fun positionChangesAreClamped() {
        val s = session()
        s.changePosition("A", 5f, 5f)
        assertEquals(0.98f, s.button("A").xFrac, 0f)
        assertEquals(0.98f, s.button("A").yFrac, 0f)
        s.changePosition("A", -5f, -5f)
        assertEquals(0.02f, s.button("A").xFrac, 0f)
        assertEquals(0.02f, s.button("A").yFrac, 0f)
    }

    @Test
    fun changesToUnknownIdsDoNothingAndAddNoHistory() {
        val s = session()
        val before = s.buttons.toList()
        s.changeSize("NOPE", 0.1f)
        s.changePosition("NOPE", 0.1f, 0.1f)
        s.changeLabel("NOPE", "x")
        s.updateButton(before.first().copy(id = "NOPE"))
        s.dragBy("NOPE", 10f, 10f, 100f, 100f)
        s.scaleBy("NOPE", 2f)
        s.snapToGrid("NOPE", 100f, 100f, 10f)
        assertEquals(before, s.buttons.toList())
        assertTrue(s.undoStack.isEmpty())
        assertFalse(s.edited.value)
    }

    @Test
    fun updatingWithTheSameValueAddsNoHistory() {
        val s = session()
        s.updateButton(s.button("A"))
        assertTrue(s.undoStack.isEmpty())
        s.updateButton(s.button("A").copy(opacity = 0.5f))
        assertEquals(1, s.undoStack.size)
        assertEquals(0.5f, s.button("A").opacity, 0f)
    }

    @Test
    fun labelEditsInARowShareOneUndoStep() {
        val s = session()
        s.changeLabel("A", "1")
        s.changeLabel("A", "12")
        s.changeLabel("A", "123")
        assertEquals("123", s.button("A").label)
        assertEquals(1, s.undoStack.size)
        s.undo()
        assertEquals("A", s.button("A").label)
    }

    @Test
    fun aLabelEditOnAnotherButtonStartsANewStep() {
        val s = session()
        s.changeLabel("A", "1")
        s.changeLabel("B", "2")
        assertEquals(2, s.undoStack.size)
    }

    @Test
    fun selectingAButtonEndsTheLabelEdit() {
        val s = session()
        s.changeLabel("A", "1")
        s.select("A")
        s.changeLabel("A", "12")
        assertEquals(2, s.undoStack.size)
    }

    @Test
    fun otherEditsAndUndoEndTheLabelEdit() {
        val s = session()
        s.changeLabel("A", "1")
        s.changeSize("B", 0.01f)
        s.changeLabel("A", "12")
        assertEquals(3, s.undoStack.size)
        s.changeLabel("A", "123")
        assertEquals(3, s.undoStack.size)
        s.undo()
        s.changeLabel("A", "x")
        assertEquals(3, s.undoStack.size)
    }

    @Test
    fun endingTheLabelEditStartsAFreshStepOnTheNextLabelChange() {
        val s = session()
        s.changeLabel("A", "1")
        s.endLabelEdit()
        s.changeLabel("A", "12")
        assertEquals(2, s.undoStack.size)
    }

    @Test
    fun draggingMovesByTheFractionOfTheCanvas() {
        val s = session()
        val a = s.button("A")
        s.dragBy("A", 100f, -50f, 1000f, 500f)
        assertEquals(a.xFrac + 0.1f, s.button("A").xFrac, 1e-6f)
        assertEquals(a.yFrac - 0.1f, s.button("A").yFrac, 1e-6f)
        assertTrue(s.undoStack.isEmpty())
    }

    @Test
    fun draggingIsClampedAndIgnoredWithoutACanvasSize() {
        val s = session()
        s.dragBy("A", 100000f, 100000f, 1000f, 500f)
        assertEquals(0.98f, s.button("A").xFrac, 0f)
        assertEquals(0.98f, s.button("A").yFrac, 0f)
        val before = s.button("B")
        s.dragBy("B", 10f, 10f, 0f, 500f)
        s.dragBy("B", 10f, 10f, 1000f, 0f)
        assertEquals(before, s.button("B"))
    }

    @Test
    fun scalingMultipliesAndClamps() {
        val s = session()
        s.scaleBy("A", 2f)
        assertEquals(0.18f, s.button("A").sizeFrac, 1e-6f)
        s.scaleBy("A", 100f)
        assertEquals(0.6f, s.button("A").sizeFrac, 0f)
        s.scaleBy("A", 0.0001f)
        assertEquals(0.04f, s.button("A").sizeFrac, 0f)
    }

    @Test
    fun snappingMovesTheCentreToTheNearestGridPoint() {
        val s = session()
        s.snapToGrid("A", 1000f, 500f, 50f)
        assertEquals(0.8f, s.button("A").xFrac, 1e-6f)
        assertEquals(0.8f, s.button("A").yFrac, 1e-6f)
        s.snapToGrid("A", 0f, 500f, 50f)
        assertEquals(0.8f, s.button("A").xFrac, 1e-6f)
    }
}
