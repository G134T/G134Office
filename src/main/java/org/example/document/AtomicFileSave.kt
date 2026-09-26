package org.example.document

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object AtomicFileSave {
    fun write(target: File, writer: (File) -> Unit) {
        val destination = target.toPath().toAbsolutePath().normalize()
        Files.createDirectories(destination.parent)
        val temporary = Files.createTempFile(destination.parent, ".g134-save-", ".tmp")
        var failure: Throwable? = null
        try {
            writer(temporary.toFile())
            // Do not fall back to a non-atomic overwrite if the filesystem rejects this.
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (ex: Throwable) {
            failure = ex
            throw ex
        } finally {
            try {
                Files.deleteIfExists(temporary)
            } catch (cleanup: Exception) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
