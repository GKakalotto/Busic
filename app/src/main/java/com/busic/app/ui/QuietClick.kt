package com.busic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.quietClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
fun FlatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    content: @Composable () -> Unit,
) {
    Box(modifier.quietClick(onClick), contentAlignment = Alignment.Center) {
        content()
    }
}
