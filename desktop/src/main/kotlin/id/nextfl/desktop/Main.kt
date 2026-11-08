package id.nextfl.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.nextfl.app.AppContainer
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.AppRoot
import id.nextfl.app.ui.theme.AppTheme
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.io.File

// Size used after un-maximize
private val START_SIZE = DpSize(1200.dp, 680.dp)
private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 560
private const val SELF_TEST_FLAG = "--self-test"

fun main(args: Array<String>) {
    if (args.size == 3 && args[0] == SELF_TEST_FLAG) return SelfTest.run(File(args[1]), File(args[2]))
    FileKit.init(appId = "NextFL")
    val container = AppContainer(DesktopPlatform())
    container.session.start()
    application {
        val vm = remember { EditorViewModel(container) }
        val state = rememberWindowState(WindowPlacement.Maximized, size = START_SIZE, position = WindowPosition.Aligned(Alignment.Center))
        Window(
            onCloseRequest = {
                shutdown(container)
                exitApplication()
            },
            state = state,
            title = "NextFL",
            icon = painterResource("nextfl_icon.png"),
            onKeyEvent = { shortcut(vm, it) },
        ) {
            DisposableEffect(Unit) {
                DesktopWindow.frame = window
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                onDispose { DesktopWindow.frame = null }
            }
            AppTheme { AppRoot(vm) }
        }
    }
}

// Save before the process exits
private fun shutdown(container: AppContainer) {
    container.playback.stop()
    runBlocking { container.session.saveNow() }
}

// Space plays, Ctrl+Z undoes
private fun shortcut(vm: EditorViewModel, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.Spacebar && !event.isCtrlPressed -> true.also { vm.togglePlay() }
        event.isCtrlPressed && event.key == Key.Z && event.isShiftPressed -> true.also { vm.redo() }
        event.isCtrlPressed && event.key == Key.Z -> true.also { vm.undo() }
        event.isCtrlPressed && event.key == Key.Y -> true.also { vm.redo() }
        else -> false
    }
}
