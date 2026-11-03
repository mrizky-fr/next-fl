package id.nextfl.core.state

data class History<T>(
    val past: List<T> = emptyList(),
    val present: T,
    val future: List<T> = emptyList(),
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(next: T): History<T> {
        if (next === present) return this
        return History((past + present).takeLast(LIMIT), next, emptyList())
    }

    fun replace(next: T): History<T> = copy(present = next)

    fun undo(): History<T> {
        if (past.isEmpty()) return this
        return History(past.dropLast(1), past.last(), listOf(present) + future)
    }

    fun redo(): History<T> {
        if (future.isEmpty()) return this
        return History(past + present, future.first(), future.drop(1))
    }

    companion object {
        const val LIMIT = 100
    }
}
