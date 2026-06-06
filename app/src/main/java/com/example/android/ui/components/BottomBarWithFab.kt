package com.example.android.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.R

@Composable
fun BottomBarWithFab(
    onHomeClick: () -> Unit,
    onGesturesClick: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val activeColor = colorResource(id = R.color.teal_primary)

    val fabSize = 54.dp
    val notchRadius = with(density) { 32.dp.toPx() }
    val shoulderRadius = with(density) { 8.dp.toPx() }
    val barCornerRadius = with(density) { 18.dp.toPx() }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 800L

    val customShape = remember(density, notchRadius, shoulderRadius, barCornerRadius) {
        GenericShape { size, _ ->
            val middle = size.width / 2
            moveTo(0f, barCornerRadius)
            arcTo(
                rect = Rect(0f, 0f, barCornerRadius * 2, barCornerRadius * 2),
                startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            lineTo(middle - notchRadius - shoulderRadius, 0f)
            cubicTo(
                x1 = middle - notchRadius - shoulderRadius * 0.5f, y1 = 0f,
                x2 = middle - notchRadius, y2 = 0f,
                x3 = middle - notchRadius, y3 = shoulderRadius
            )
            arcTo(
                rect = Rect(
                    left = middle - notchRadius,
                    top = shoulderRadius - notchRadius,
                    right = middle + notchRadius,
                    bottom = shoulderRadius + notchRadius
                ),
                startAngleDegrees = 180f, sweepAngleDegrees = -180f, forceMoveTo = false
            )
            cubicTo(
                x1 = middle + notchRadius, y1 = 0f,
                x2 = middle + notchRadius + shoulderRadius * 0.5f, y2 = 0f,
                x3 = middle + notchRadius + shoulderRadius, y3 = 0f
            )
            lineTo(size.width - barCornerRadius, 0f)
            arcTo(
                rect = Rect(size.width - barCornerRadius * 2, 0f, size.width, barCornerRadius * 2),
                startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
            shape = customShape,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Área Botón Inicio
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime > debounceTime) {
                                    lastClickTime = currentTime
                                    onHomeClick()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Inicio",
                            tint = activeColor,
                            modifier = Modifier.size(25.dp)
                        )
                        Text(
                            text = "Inicio",
                            color = activeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Espacio central para el Notch (FAB)
                Spacer(modifier = Modifier.width(76.dp))

                // Área Botón Gestos
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime > debounceTime) {
                                    lastClickTime = currentTime
                                    onGesturesClick()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.BackHand,
                            contentDescription = "Gestos",
                            tint = activeColor,
                            modifier = Modifier.size(25.dp)
                        )
                        Text(
                            text = "Gestos",
                            color = activeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > debounceTime) {
                    lastClickTime = currentTime
                    onFabClick()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-31).dp)
                .size(fabSize),
            containerColor = activeColor,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 10.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Menú",
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }
    }
}
