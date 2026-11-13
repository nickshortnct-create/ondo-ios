package com.nickshortnct.ondo.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.nickshortnct.ondo.ui.navigation.Screen

@Composable
expect fun animatedTabIconPainter(destination: Screen): Painter?

@Composable
expect fun playPauseIconPainter(reversed: Boolean): Painter?
