package no.mwm.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import no.mwm.chess.ui.ChessViewModel
import no.mwm.chess.ui.GameScreen
import no.mwm.chess.ui.MenuScreen
import no.mwm.chess.ui.theme.ChessTheme
import no.mwm.chess.ui.theme.Design

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ChessTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(Design.background),
                ) {
                    ChessApp()
                }
            }
        }
    }
}

@Composable
fun ChessApp(vm: ChessViewModel = viewModel()) {
    if (vm.inMenu) MenuScreen(vm) else GameScreen(vm)
}
