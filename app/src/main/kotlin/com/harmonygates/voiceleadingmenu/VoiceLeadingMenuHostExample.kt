package com.harmonygates.voiceleadingmenu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VoiceLeadingMenuHostExample(
    onStartExercise: (VoiceLeadingMenuState) -> Unit,
) {
    var menuState by remember { mutableStateOf(VoiceLeadingMenuState()) }

    Box(Modifier.fillMaxSize()) {
        // Put the static/generated room artwork behind this Box.

        VoiceLeadingMenu(
            state = menuState,
            onStateChange = { menuState = it },
            onStartExercise = onStartExercise,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        )
    }
}
