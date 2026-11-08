package id.nextfl.app.platform

import java.io.File
import java.io.InputStream

/** File chosen in the Windows dialog. */
class PickedFile(val file: File) {
    val name: String get() = file.name

    fun open(): InputStream = file.inputStream()
}
