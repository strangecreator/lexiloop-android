package ru.lexiloop.app.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.lexiloop.app.data.api.PoolDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pool the user is currently studying, shared by Overview, Study, and
 * Library. `null` means "all pools".
 */
@Singleton
class PoolSelection @Inject constructor() {
    private val _selected = MutableStateFlow<PoolDto?>(null)
    val selected: StateFlow<PoolDto?> = _selected

    fun select(pool: PoolDto?) {
        _selected.value = pool
    }
}
