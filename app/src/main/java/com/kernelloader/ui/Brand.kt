package com.kernelloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kernelloader.R

// Brand palette: deep navy -> dark green (opaque, never transparent).
val BrandBgTop = Color(0xFF0B1220)
val BrandBgMid = Color(0xFF0C1A17)
val BrandBgBottom = Color(0xFF0E2418)
val BrandAccent = Color(0xFF4CAF50)

/**
 * Shared opaque app background: vertical brand gradient + one faint
 * centered logo watermark (barely visible, alpha ~0.06).
 * Content draws on top and must add its own window-inset padding.
 */
@Composable
fun AppBackground(
    watermarkAlpha: Float = 0.045f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BrandBgTop, BrandBgMid, BrandBgBottom)
                )
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .alpha(watermarkAlpha)
        )
        content()
    }
}
