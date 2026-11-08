package nl.enadeag.carchargeplanner.android.home

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Logic that breaks `AppArchitectureTest`'s rule on files on purpose. */
internal class LogicThatWritesAFile {

    /** Writes, moves and deletes files itself instead of leaving them to the engine. */
    fun write(folder: File) {
        File(folder, "notes.txt").writeText("kept")
        Files.move(Path.of(folder.path, "notes.txt"), Path.of(folder.path, "old.txt"))
        File(folder, "old.txt").delete()
    }
}
