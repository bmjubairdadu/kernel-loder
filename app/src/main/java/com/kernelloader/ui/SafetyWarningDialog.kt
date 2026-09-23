package com.kernelloader.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

import com.kernelloader.R

@Composable
fun SafetyWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF111C26),
        titleContentColor = androidx.compose.ui.graphics.Color.White,
        textContentColor = androidx.compose.ui.graphics.Color(0xFFB0BEC5),
        icon = {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        },
        title = { Text(text = stringResource(R.string.safety_warning_title)) },
        text = {
            Text(text = stringResource(R.string.safety_warning_message))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1E7A46),
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF37474F),
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(stringResource(R.string.exit))
            }
        }
    )
}
