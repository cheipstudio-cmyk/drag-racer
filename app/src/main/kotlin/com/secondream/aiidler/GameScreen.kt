package com.secondream.aiidler

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

private val CoreBlue = Color(0xFF4A9EFF)
private val Amber = Color(0xFFD9A85C)
private val Green = Color(0xFF00FF88)
private val BgColor = Color(0xFF0B0B0D)
private const val CORE_Y_FRACTION = 0.40f

@Composable
fun GameScreen(
    gameLogic: GameLogic,
    audioManager: AudioManager,
    hapticManager: HapticManager
) {
    val gameState by gameLogic.gameState.collectAsState()
    val scope = rememberCoroutineScope()

    var particles by remember { mutableStateOf(listOf<Particle>()) }
    var rings by remember { mutableStateOf(listOf<Ring>()) }
    var tick by remember { mutableStateOf(0L) }
    var breakthroughTime by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val coreScale = remember { Animatable(1f) }

    // ~60fps frame loop drives all animation + particle/ring physics
    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.delay(16)
            val now = System.currentTimeMillis()
            val dt = (now - last).coerceAtMost(64L).toFloat()
            last = now
            particles = particles.mapNotNull { it.advance(dt) }
            rings = rings.mapNotNull { it.advance(dt) }
            tick = now
        }
    }

    fun handleTap() {
        val (_, breakthrough) = gameLogic.tap()
        val core = Offset(canvasSize.width / 2f, canvasSize.height * CORE_Y_FRACTION)

        audioManager.playTapSound()
        if (breakthrough) hapticManager.breakthrough() else hapticManager.tap()

        rings = rings + Ring(life = 520f, maxRadius = 190f, color = Amber, width = 4f)
        particles = particles + spawnBurst(core, 16)

        if (breakthrough) {
            audioManager.playBreakthroughSound()
            breakthroughTime = System.currentTimeMillis()
            rings = rings + Ring(life = 900f, maxRadius = 540f, color = Amber, width = 8f)
            particles = particles + spawnBurst(core, 34)
        }

        scope.launch {
            coreScale.snapTo(0.84f)
            coreScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { handleTap() })
                }
        ) {
            val w = size.width
            val h = size.height
            val core = Offset(w / 2f, h * CORE_Y_FRACTION)
            val frame = tick // snapshot read forces redraw each frame

            drawRect(BgColor)

            // Background grid (dim, anchored, gently pulsing)
            val spacing = 90f
            val cols = (w / spacing).toInt() + 1
            val rows = (h / spacing).toInt() + 1
            val t = frame / 1000f
            for (cx in 0..cols) {
                for (cy in 0..rows) {
                    val a = (sin(t + cx * 0.5f + cy * 0.5f) * 0.5f + 0.5f) * 0.12f
                    drawCircle(
                        color = CoreBlue.copy(alpha = a),
                        radius = 1.6f,
                        center = Offset(cx * spacing, cy * spacing)
                    )
                }
            }

            val pulse = sin(frame / 600.0).toFloat() * 4f
            val baseR = 46f + min(gameState.level - 1, 10) * 1.5f
            val coreRadius = (baseR + pulse) * coreScale.value

            // Orbiting GPU nodes (contained: orbit radius small, never clipped)
            val farm = min(gameState.upgrades["gpu_farm"]?.owned ?: 0, 16)
            if (farm > 0) {
                val orbitR = baseR + 82f
                val rot = frame / 2600f
                for (i in 0 until farm) {
                    val ang = (i.toFloat() / farm) * (2f * PI.toFloat()) + rot
                    val nx = core.x + cos(ang) * orbitR
                    val ny = core.y + sin(ang) * orbitR
                    drawCircle(Green.copy(alpha = 0.22f), radius = 10f, center = Offset(nx, ny))
                    drawCircle(Green, radius = 4f, center = Offset(nx, ny))
                }
            }

            // Expanding ring pulses
            rings.forEach { r ->
                drawCircle(
                    color = r.color.copy(alpha = r.alpha * 0.8f),
                    radius = r.radius,
                    center = core,
                    style = Stroke(width = r.width)
                )
            }

            // Core glow halo
            drawCircle(CoreBlue.copy(alpha = 0.10f), radius = coreRadius * 2.4f, center = core)
            drawCircle(CoreBlue.copy(alpha = 0.16f), radius = coreRadius * 1.7f, center = core)
            // Core body
            drawCircle(CoreBlue, radius = coreRadius, center = core)
            // Amber rim
            drawCircle(Amber.copy(alpha = 0.9f), radius = coreRadius, center = core, style = Stroke(width = 3f))
            // Inner highlight
            drawCircle(
                Color.White.copy(alpha = 0.85f),
                radius = coreRadius * 0.30f,
                center = Offset(core.x - coreRadius * 0.18f, core.y - coreRadius * 0.18f)
            )

            // Particles (front)
            particles.forEach { p ->
                drawCircle(p.color.copy(alpha = p.alpha), radius = p.radius, center = Offset(p.x, p.y))
            }

            // Breakthrough surge: center-clear amber vignette, brief, low alpha (NOT a wash)
            val bt = frame - breakthroughTime
            if (breakthroughTime > 0L && bt < 600L) {
                val k = 1f - (bt / 600f)
                val brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Amber.copy(alpha = 0.22f * k)),
                    center = core,
                    radius = maxOf(w, h)
                )
                drawRect(brush)
            }
        }

        // HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Level ${gameState.level}", color = Amber, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${formatNumber(gameState.gpu)} GPU", color = CoreBlue, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${gameState.workers * 10}/sec", color = Color(0xFF7A7A82), fontSize = 13.sp)
        }

        UpgradesPanel(
            gameState = gameState,
            gameLogic = gameLogic,
            audioManager = audioManager,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun UpgradesPanel(
    gameState: GameState,
    gameLogic: GameLogic,
    audioManager: AudioManager,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .background(Color(0xFF16161A))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            "UPGRADES",
            color = Amber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(scroll)
        ) {
            gameState.upgrades.forEach { (id, up) ->
                val cost = gameLogic.getUpgradeCost(id)
                UpgradeRow(
                    name = up.name,
                    owned = up.owned,
                    cost = cost,
                    canAfford = gameState.gpu >= cost,
                    onBuy = { if (gameLogic.buyUpgrade(id)) audioManager.playUpgradeSound() }
                )
            }
        }
    }
}

@Composable
private fun UpgradeRow(
    name: String,
    owned: Int,
    cost: Long,
    canAfford: Boolean,
    onBuy: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (canAfford) CoreBlue else Color(0xFF26262B))
            .clickable(enabled = canAfford) { onBuy() }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    name,
                    color = if (canAfford) Color.White else Color(0xFF8A8A90),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "Posseduti: $owned",
                    color = if (canAfford) Color(0xFFE2ECFF) else Color(0xFF6A6A70),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Text(
                formatNumber(cost),
                color = if (canAfford) Color.White else Color(0xFF8A8A90),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun spawnBurst(center: Offset, count: Int): List<Particle> {
    return List(count) { i ->
        val angle = (i.toFloat() / count) * (2f * PI.toFloat()) + (Math.random().toFloat() * 0.3f)
        val speed = 0.18f + Math.random().toFloat() * 0.22f
        Particle(
            x = center.x,
            y = center.y,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            life = 600f + Math.random().toFloat() * 250f,
            startSize = 6f + Math.random().toFloat() * 4f,
            color = when (i % 3) {
                0 -> Amber
                1 -> CoreBlue
                else -> Green
            }
        )
    }
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float = life,
    val startSize: Float,
    val color: Color
) {
    fun advance(dt: Float): Particle? {
        val nl = life - dt
        if (nl <= 0f) return null
        return copy(
            x = x + vx * dt,
            y = y + vy * dt,
            vy = vy + 0.0006f * dt,
            life = nl
        )
    }

    val alpha: Float get() = (life / maxLife).coerceIn(0f, 1f)
    val radius: Float get() = startSize * alpha
}

data class Ring(
    val life: Float,
    val maxLife: Float = life,
    val maxRadius: Float,
    val startRadius: Float = 30f,
    val color: Color,
    val width: Float = 4f
) {
    fun advance(dt: Float): Ring? {
        val nl = life - dt
        if (nl <= 0f) return null
        return copy(life = nl)
    }

    val progress: Float get() = (1f - (life / maxLife)).coerceIn(0f, 1f)
    val radius: Float get() = startRadius + (maxRadius - startRadius) * progress
    val alpha: Float get() = (1f - progress).coerceIn(0f, 1f)
}

fun formatNumber(value: Long): String {
    return when {
        value >= 1_000_000_000 -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000 -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.2fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
