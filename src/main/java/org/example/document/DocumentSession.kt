package org.example.document

import java.io.File

class DocumentSession {
    var file: File? = null
    var dirty: Boolean = false
    var encoding: String = "UTF-8"

    fun reset() {
        file = null
        dirty = false
        encoding = "UTF-8"
    }

    fun markSaved(target: File) {
        file = target
        dirty = false
    }

    fun isNew(): Boolean = file == null

    fun displayName(): String = file?.name ?: "Документ1"

    fun displayPath(): String = file?.absolutePath ?: "Новый документ"

    fun windowTitle(): String {
        val mark = if (dirty) "● " else ""
        return "$mark${displayName()} — G134Office"
    }

    fun ext(): String = file?.extension?.lowercase().orEmpty()
}