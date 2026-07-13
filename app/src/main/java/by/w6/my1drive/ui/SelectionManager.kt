package by.w6.my1drive.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionManager {

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(itemId: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectItems(itemIds: Collection<String>) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { addAll(itemIds) }
    }

    fun deselectItems(itemIds: Collection<String>) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { removeAll(itemIds.toSet()) }
    }
}
