package no.mwm.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import no.mwm.chess.ui.ChessViewModel
import no.mwm.chess.ui.GameScreen
import no.mwm.chess.ui.MenuScreen
import no.mwm.chess.ui.theme.ChessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
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
