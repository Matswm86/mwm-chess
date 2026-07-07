package no.mwm.chess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwm.chess.engine.ai.Difficulty

@Composable
fun MenuScreen(vm: ChessViewModel) {
    var mode by remember { mutableStateOf(GameMode.VS_AI) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var color by remember { mutableStateOf(ColorChoice.WHITE) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "MWM Chess",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Play chess the easy way. Free, open source, made for beginners.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        SectionCard("Mode") {
            ChipRow(
                options = listOf("Play the computer" to GameMode.VS_AI, "Two players" to GameMode.TWO_PLAYER),
                selected = mode,
                onSelect = { mode = it },
            )
        }

        if (mode == GameMode.VS_AI) {
            Spacer(Modifier.height(14.dp))
            SectionCard("Difficulty") {
                ChipRow(
                    options = Difficulty.entries.map { it.label to it },
                    selected = difficulty,
                    onSelect = { difficulty = it },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    difficultyHint(difficulty),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(if (mode == GameMode.VS_AI) "Play as" else "Bottom side") {
            ChipRow(
                options = listOf(
                    "White" to ColorChoice.WHITE,
                    "Random" to ColorChoice.RANDOM,
                    "Black" to ColorChoice.BLACK,
                ),
                selected = color,
                onSelect = { color = it },
            )
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { vm.startGame(mode, difficulty, color) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Start game", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Tap a piece to see where it can go.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun difficultyHint(d: Difficulty): String = when (d) {
    Difficulty.EASY -> "Relaxed. The computer sometimes plays a loose move, great for learning."
    Difficulty.MEDIUM -> "A steady club opponent. Punishes obvious mistakes."
    Difficulty.HARD -> "Strong and tactical. Thinks several moves ahead."
    Difficulty.EXPERT -> "Toughest. Deep search with no mercy."
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
