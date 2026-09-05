package com.iykyk.videocollage.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.videocollage.ui.AppUiState
import com.iykyk.videocollage.ui.ProcessingStage

@Composable
fun ProcessingScreen(
    uiState: AppUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val progress = uiState.progress
    val currentStage = progress.stage
    val hasError = uiState.error != null
    val sampleName = uiState.currentSampleName

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (hasError) "$sampleName Failed" else "$sampleName processing...",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!hasError) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(64.dp)
                        .rotate(rotation),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (progress.statusText.isNotEmpty() && !hasError) {
                Text(
                    text = progress.statusText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (hasError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!hasError) {
                LinearProgressIndicator(
                    progress = { progress.overallFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress.overallFraction * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProcessingStage.entries.forEach { stage ->
                        StageRow(
                            stage = stage,
                            currentStage = currentStage,
                            hasError = hasError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Cancel")
                }

                if (hasError) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(
    stage: ProcessingStage,
    currentStage: ProcessingStage,
    hasError: Boolean
) {
    val isComplete = stage.order < currentStage.order ||
            (stage == ProcessingStage.COMPLETE && currentStage == ProcessingStage.COMPLETE && !hasError)
    val isCurrent = stage == currentStage && !hasError
    val isError = hasError && stage == currentStage

    val icon: ImageVector = when {
        isComplete -> Icons.Default.Check
        isError -> Icons.Default.Close
        else -> stageIcons[stage.order]
    }

    val iconTint = when {
        isComplete -> MaterialTheme.colorScheme.primary
        isError -> MaterialTheme.colorScheme.error
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val textColor = when {
        isComplete -> MaterialTheme.colorScheme.onSurface
        isError -> MaterialTheme.colorScheme.error
        isCurrent -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
        Text(
            text = stage.label,
            fontSize = 14.sp,
            fontWeight = fontWeight,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (isCurrent && !hasError) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private val stageIcons: List<ImageVector> = listOf(
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check,
    Icons.Default.Check
)
