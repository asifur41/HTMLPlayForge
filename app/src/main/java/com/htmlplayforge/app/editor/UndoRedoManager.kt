package com.htmlplayforge.app.editor

/** One saved state of the editor. */
data class EditState(val text: String, val cursor: Int)

/**
 * Undo/redo with up to 100 steps. Fast typing (changes less than [mergeMs] apart)
 * is merged into one step. Pure Kotlin: the caller gives the time.
 */
class UndoRedoManager(private val limit: Int = 100, private val mergeMs: Long = 800L) {
    private val states = ArrayList<EditState>()
    private var index = -1
    private var lastTime = 0L
    private var lastMergeable = false

    fun reset(initial: EditState) {
        states.clear(); states.add(initial); index = 0; lastTime = 0L; lastMergeable = false
    }

    /** Call after every change made by the user. */
    fun record(state: EditState, nowMs: Long) {
        if (index >= 0 && states[index].text == state.text) return
        val merge = index > 0 && lastMergeable && nowMs - lastTime < mergeMs && index == states.size - 1
        if (merge) {
            states[index] = state
        } else {
            while (states.size > index + 1) states.removeAt(states.size - 1)
            states.add(state); index++
            if (states.size > limit + 1) { states.removeAt(0); index-- }
        }
        lastTime = nowMs
        lastMergeable = true
    }

    fun canUndo(): Boolean = index > 0
    fun canRedo(): Boolean = index >= 0 && index < states.size - 1

    fun undo(): EditState? {
        if (!canUndo()) return null
        index--; lastMergeable = false
        return states[index]
    }

    fun redo(): EditState? {
        if (!canRedo()) return null
        index++; lastMergeable = false
        return states[index]
    }
}
