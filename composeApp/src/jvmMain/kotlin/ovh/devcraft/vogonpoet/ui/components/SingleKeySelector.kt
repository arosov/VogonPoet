package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGray
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreenDark

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SingleKeySelector(
    label: String,
    currentKey: String,
    onKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRecording by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GruvboxFg0.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        color = if (isRecording) GruvboxGreenDark.copy(alpha = 0.2f) else GruvboxBg1,
                        shape = RoundedCornerShape(4.dp),
                    ).border(
                        width = 1.dp,
                        color = if (isRecording) GruvboxGreenDark else GruvboxGray,
                        shape = RoundedCornerShape(4.dp),
                    ).clickable {
                        isRecording = true
                    }.focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (isRecording && event.type == KeyEventType.KeyDown) {
                            val keyName =
                                when (event.key) {
                                    Key.CtrlLeft -> "Left Ctrl"
                                    Key.CtrlRight -> "Right Ctrl"
                                    Key.ShiftLeft -> "Left Shift"
                                    Key.ShiftRight -> "Right Shift"
                                    Key.AltLeft -> "Left Alt"
                                    Key.AltRight -> "Right Alt"
                                    Key.MetaLeft -> "Left Meta"
                                    Key.MetaRight -> "Right Meta"
                                    Key.A -> "A"
                                    Key.B -> "B"
                                    Key.C -> "C"
                                    Key.D -> "D"
                                    Key.E -> "E"
                                    Key.F -> "F"
                                    Key.G -> "G"
                                    Key.H -> "H"
                                    Key.I -> "I"
                                    Key.J -> "J"
                                    Key.K -> "K"
                                    Key.L -> "L"
                                    Key.M -> "M"
                                    Key.N -> "N"
                                    Key.O -> "O"
                                    Key.P -> "P"
                                    Key.Q -> "Q"
                                    Key.R -> "R"
                                    Key.S -> "S"
                                    Key.T -> "T"
                                    Key.U -> "U"
                                    Key.V -> "V"
                                    Key.W -> "W"
                                    Key.X -> "X"
                                    Key.Y -> "Y"
                                    Key.Z -> "Z"
                                    Key.Zero -> "0"
                                    Key.One -> "1"
                                    Key.Two -> "2"
                                    Key.Three -> "3"
                                    Key.Four -> "4"
                                    Key.Five -> "5"
                                    Key.Six -> "6"
                                    Key.Seven -> "7"
                                    Key.Eight -> "8"
                                    Key.Nine -> "9"
                                    Key.Spacebar -> "Space"
                                    Key.Enter -> "Enter"
                                    Key.Tab -> "Tab"
                                    Key.Escape -> "Escape"
                                    Key.F1 -> "F1"
                                    Key.F2 -> "F2"
                                    Key.F3 -> "F3"
                                    Key.F4 -> "F4"
                                    Key.F5 -> "F5"
                                    Key.F6 -> "F6"
                                    Key.F7 -> "F7"
                                    Key.F8 -> "F8"
                                    Key.F9 -> "F9"
                                    Key.F10 -> "F10"
                                    Key.F11 -> "F11"
                                    Key.F12 -> "F12"
                                    else -> null
                                }

                            if (keyName != null) {
                                onKeyChange(keyName)
                                isRecording = false
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isRecording) "Press any key..." else currentKey,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRecording) GruvboxGreenDark else GruvboxFg0,
            )
        }

        LaunchedEffect(isRecording) {
            if (isRecording) {
                focusRequester.requestFocus()
            }
        }
    }
}
