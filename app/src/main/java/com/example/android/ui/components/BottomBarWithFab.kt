package com.example.android.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.colorResource
import com.example.android.R

@Composable
fun BottomBarWithFab(
    onHomeClick: () -> Unit,
    onGesturesClick: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fabSize = 64.dp
    val activeColor = colorResource(id = R.color.teal_primary)

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 800L

    val customShape = remember(density) {
        GenericShape { size, _ ->
            val notchRadius = with(density) { 42.dp.toPx() }
            val centerX = size.width / 2
            
            moveTo(0f, 0f)
            lineTo(centerX - notchRadius * 1.2f, 0f)

            cubicTo(
                x1 = centerX - notchRadius * 0.8f, y1 = 0f,
                x2 = centerX - notchRadius * 0.7f, y2 = notchRadius * 0.8f,
                x3 = centerX, y3 = notchRadius * 0.8f
            )
            cubicTo(
                x1 = centerX + notchRadius * 0.7f, y1 = notchRadius * 0.8f,
                x2 = centerX + notchRadius * 0.8f, y2 = 0f,
                x3 = centerX + notchRadius * 1.2f, y3 = 0f
            )
            
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
            shape = customShape,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > debounceTime) {
                                lastClickTime = currentTime
                                onHomeClick()
                            }
                        }) {
                            Icon(Icons.Outlined.Home, "Inicio", tint = activeColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Inicio", color = activeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(80.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > debounceTime) {
                                lastClickTime = currentTime
                                onGesturesClick()
                            }
                        }) {
                            Icon(Icons.Outlined.BackHand, "Gestos", tint = activeColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Gestos", color = activeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(fabSize),
            containerColor = activeColor,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            Icon(Icons.Default.Add, "Menú", modifier = Modifier.size(32.dp))
        }
    }
}
