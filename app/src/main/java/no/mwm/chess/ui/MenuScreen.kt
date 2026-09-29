package no.mwm.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwm.chess.engine.ai.Difficulty
import no.mwm.chess.ui.theme.Cinzel
import no.mwm.chess.ui.theme.Design

@Composable
fun MenuScreen(vm: ChessViewModel) {
    var mode by remember { mutableStateOf(GameMode.VS_AI) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var color by remember { mutableStateOf(ColorChoice.WHITE) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Design.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text("♚", fontSize = 54.sp, color = Design.gold)
        Spacer(Modifier.height(8.dp))
        Text(
            "MWM CHESS",
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = 4.sp,
            color = Design.creamBright,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Play chess the easy way. Free, open source, made for beginners.",
            fontFamily = Cinzel, fontSize = 13.sp, textAlign = TextAlign.Center, color = Design.muted,
        )
        Spacer(Modifier.height(26.dp))

        Section("Mode") {
            ChipRow(
                listOf(
                    "Play the computer" to GameMode.VS_AI,
                    "Two players, one phone" to GameMode.TWO_PLAYER,
                    "Play a friend online" to GameMode.ONLINE,
                ),
                mode,
            ) { mode = it; vm.clearOnlineError() }
        }

        if (mode == GameMode.ONLINE) {
            OnlineMenu(vm, color) { color = it }
        } else {
            if (mode == GameMode.VS_AI) {
                Spacer(Modifier.height(14.dp))
                Section("Difficulty") {
                    ChipRow(Difficulty.entries.map { it.label to it }, difficulty) { difficulty = it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        difficultyHint(difficulty),
                        fontFamily = Cinzel, fontSize = 12.sp, color = Design.muted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Section(if (mode == GameMode.VS_AI) "Play as" else "Bottom side") {
                ChipRow(
                    listOf(
                        "White" to ColorChoice.WHITE,
                        "Random" to ColorChoice.RANDOM,
                        "Black" to ColorChoice.BLACK,
                    ),
                    color,
                ) { color = it }
            }

            Spacer(Modifier.height(30.dp))
            StartButton { vm.startGame(mode, difficulty, color) }
            Spacer(Modifier.height(18.dp))
            Text(
                "Tap a piece to see where it can go.",
                fontFamily = Cinzel, fontSize = 12.sp, color = Design.muted,
            )
        }
    }
}

@Composable
private fun OnlineMenu(vm: ChessViewModel, color: ColorChoice, onColor: (ColorChoice) -> Unit) {
    var code by remember { mutableStateOf("") }

    Spacer(Modifier.height(14.dp))
    Section("Start a new game") {
        Text(
            "You get a 4-letter code. Send it to your friend, and the game starts when they join.",
            fontFamily = Cinzel, fontSize = 12.sp, color = Design.muted,
        )
        Spacer(Modifier.height(12.dp))
        ChipRow(
            listOf(
                "White" to ColorChoice.WHITE,
                "Random" to ColorChoice.RANDOM,
                "Black" to ColorChoice.BLACK,
            ),
            color,
            onColor,
        )
        Spacer(Modifier.height(14.dp))
        GoldButton("CREATE GAME", enabled = !vm.connecting) { vm.createOnlineGame(color) }
    }

    Spacer(Modifier.height(14.dp))
    Section("Join a friend's game") {
        OutlinedTextField(
            value = code,
            onValueChange = { raw -> code = raw.uppercase().filter { it in 'A'..'Z' }.take(4) },
            placeholder = { Text("CODE", fontFamily = Cinzel, color = Design.muted.copy(alpha = 0.5f)) },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                letterSpacing = 8.sp, color = Design.creamBright, textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrect = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { vm.joinOnlineGame(code) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Design.goldLight,
                unfocusedBorderColor = Design.gold,
                cursorColor = Design.goldLight,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        GoldButton("JOIN GAME", enabled = !vm.connecting && code.length == 4) { vm.joinOnlineGame(code) }
    }

    Spacer(Modifier.height(18.dp))
    if (vm.connecting) {
        CircularProgressIndicator(Modifier.height(28.dp), color = Design.goldLight, strokeWidth = 2.5.dp)
    }
    vm.onlineError?.let {
        Text(it, fontFamily = Cinzel, fontSize = 13.sp, textAlign = TextAlign.Center, color = Design.danger)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Online games need an internet connection. Only the moves are sent, nothing else.",
        fontFamily = Cinzel, fontSize = 11.sp, textAlign = TextAlign.Center, color = Design.muted,
    )
}

@Composable
private fun GoldButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9A6730), Color(0xFF5A3A19))))
            .border(1.5.dp, Design.gold, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp,
            color = if (enabled) Design.creamBright else Design.muted.copy(alpha = 0.5f),
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Design.panelShape)
            .background(Design.panel)
            .border(1.dp, Design.gold, Design.panelShape)
            .padding(16.dp),
    ) {
        Text(
            title.uppercase(),
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp,
            color = Design.goldText,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            Chip(label, selected == value) { onSelect(value) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(Design.goldLight, Design.goldDark)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .border(1.5.dp, Design.gold, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = if (selected) Color(0xFF201203) else Design.cream,
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9A6730), Color(0xFF5A3A19))))
            .border(1.5.dp, Design.gold, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "START GAME",
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.sp,
            color = Design.creamBright,
        )
    }
}
