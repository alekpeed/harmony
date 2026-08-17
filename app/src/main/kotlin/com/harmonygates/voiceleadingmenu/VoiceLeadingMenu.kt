package com.harmonygates.voiceleadingmenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceLeadingMenu(
    state: VoiceLeadingMenuState,
    onStateChange: (VoiceLeadingMenuState) -> Unit,
    onStartExercise: (VoiceLeadingMenuState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.collapsed) {
            CollapsedHandle(onExpand = { onStateChange(state.copy(collapsed = false)) })
        }

        AnimatedVisibility(
            visible = !state.collapsed,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(VoiceLeadingMenuColors.Panel)
                    .padding(20.dp)
            ) {
                Header(onCollapse = { onStateChange(state.copy(collapsed = true)) })
                Spacer(Modifier.height(18.dp))

                SectionLabel("EXERCISE")
                ChoiceRow(
                    values = VoiceLeadingExercise.entries,
                    label = { it.label },
                    selected = state.exercise,
                    onSelect = { onStateChange(state.copy(exercise = it)) },
                )

                Spacer(Modifier.height(16.dp))
                SectionLabel("KEY")
                ChoiceRow(
                    values = VoiceLeadingKeys,
                    label = { it },
                    selected = state.key,
                    onSelect = { onStateChange(state.copy(key = it)) },
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("VOICE MOTION")
                        ChoiceRow(
                            values = VoiceLeadingMotion.entries,
                            label = { it.label },
                            selected = state.motion,
                            onSelect = { onStateChange(state.copy(motion = it)) },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        SectionLabel("REGISTER")
                        ChoiceRow(
                            values = VoiceLeadingRange.entries,
                            label = { it.label },
                            selected = state.range,
                            onSelect = { onStateChange(state.copy(range = it)) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("DIFFICULTY")
                        ChoiceRow(
                            values = VoiceLeadingDifficulty.entries,
                            label = { it.label },
                            selected = state.difficulty,
                            onSelect = { onStateChange(state.copy(difficulty = it)) },
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        SectionLabel("TEMPO  ${state.tempoBpm} BPM")
                        Slider(
                            value = state.tempoBpm.toFloat(),
                            onValueChange = { onStateChange(state.copy(tempoBpm = it.toInt())) },
                            valueRange = 40f..160f,
                            steps = 23,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stepper(
                        label = "Repetitions",
                        value = state.repetitions,
                        onMinus = { onStateChange(state.copy(repetitions = (state.repetitions - 1).coerceAtLeast(1))) },
                        onPlus = { onStateChange(state.copy(repetitions = (state.repetitions + 1).coerceAtMost(32))) },
                    )
                    Toggle("Metronome", state.metronome) { onStateChange(state.copy(metronome = it)) }
                    Toggle("Hints", state.showHints) { onStateChange(state.copy(showHints = it)) }
                    Button(
                        onClick = {
                            onStartExercise(state)
                            onStateChange(state.copy(collapsed = true))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoiceLeadingMenuColors.Brass,
                            contentColor = Color(0xFF161819),
                        ),
                    ) {
                        Text("START EXERCISE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "VOICE LEADING",
                color = VoiceLeadingMenuColors.Text,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Exercise setup",
                color = VoiceLeadingMenuColors.MutedText,
                fontSize = 13.sp,
            )
        }
        Text(
            "COLLAPSE  ↓",
            color = VoiceLeadingMenuColors.BrassBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCollapse)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun CollapsedHandle(onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(VoiceLeadingMenuColors.Panel)
            .clickable(onClick = onExpand)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            "VOICE LEADING SETUP  ↑",
            color = VoiceLeadingMenuColors.BrassBright,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = VoiceLeadingMenuColors.MutedText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun <T> ChoiceRow(
    values: Iterable<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { value ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) VoiceLeadingMenuColors.SelectedBright else VoiceLeadingMenuColors.PanelSoft)
                    .clickable { onSelect(value) }
                    .then(
                        if (active) Modifier
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    label(value),
                    color = if (active) VoiceLeadingMenuColors.Text else VoiceLeadingMenuColors.MutedText,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = VoiceLeadingMenuColors.Text, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        Text(label, color = VoiceLeadingMenuColors.MutedText, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", onMinus)
            Text(
                value.toString(),
                color = VoiceLeadingMenuColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            StepButton("+", onPlus)
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VoiceLeadingMenuColors.PanelSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = VoiceLeadingMenuColors.BrassBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
