package com.example.android.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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

    val fabSize = 64.dp
    val fabRadius = fabSize / 2
    val notchRadius = with(density) { 38.dp.toPx() }
    val shoulderRadius = with(density) { 10.dp.toPx() }
    val barCornerRadius = with(density) { 20.dp.toPx() }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 800L

    val customShape = remember(density, notchRadius, shoulderRadius, barCornerRadius) {
        GenericShape { size, _ ->
            val middle = size.width / 2

            moveTo(0f, barCornerRadius)

            arcTo(
                rect = Rect(0f, 0f, barCornerRadius * 2, barCornerRadius * 2),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Línea hasta el inicio de la curva del hueco (hombro izquierdo)
            lineTo(middle - notchRadius - shoulderRadius, 0f)

            // Hombro izquierdo (Curva convexa hacia abajo)
            cubicTo(
                x1 = middle - notchRadius - shoulderRadius * 0.5f, y1 = 0f,
                x2 = middle - notchRadius, y2 = 0f,
                x3 = middle - notchRadius, y3 = shoulderRadius
            )

            // El Hueco (Círculo cóncavo)
            arcTo(
                rect = Rect(
                    left = middle - notchRadius,
                    top = shoulderRadius - notchRadius,
                    right = middle + notchRadius,
                    bottom = shoulderRadius + notchRadius
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )

            cubicTo(
                x1 = middle + notchRadius, y1 = 0f,
                x2 = middle + notchRadius + shoulderRadius * 0.5f, y2 = 0f,
                x3 = middle + notchRadius + shoulderRadius, y3 = 0f
            )

            lineTo(size.width - barCornerRadius, 0f)

            arcTo(
                rect = Rect(size.width - barCornerRadius * 2, 0f, size.width, barCornerRadius * 2),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
            shape = customShape,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > debounceTime) {
                                lastClickTime = currentTime
                                onHomeClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Inicio",
                            tint = activeColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Inicio",
                        color = activeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > debounceTime) {
                                lastClickTime = currentTime
                                onGesturesClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BackHand,
                            contentDescription = "Gestos",
                            tint = activeColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Gestos",
                        color = activeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                .offset(y = (-38).dp)
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
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}