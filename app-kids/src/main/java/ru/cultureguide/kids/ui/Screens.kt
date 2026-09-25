package ru.cultureguide.kids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.maps.MapView
import ru.cultureguide.kids.KaravanController
import ru.cultureguide.kids.Screen
import ru.cultureguide.kids.content.Clips
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.kidSteps
import ru.cultureguide.kids.content.stepsWord
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.formatDistance

/** Связь экранов с картой, которой владеет активность (жизненный цикл MapView). */
class MapHooks(
    val onCreated: (MapView) -> Unit,
    val onReleased: (MapView) -> Unit,
    val onUpdate: (Journey, LocationFix?) -> Unit,
    val onFitAll: () -> Unit
)

@Composable
fun KaravanApp(controller: KaravanController, onStartWalk: () -> Unit, map: MapHooks) {
    BackHandler(enabled = controller.screen != Screen.Home) {
        if (controller.screen == Screen.Stop) controller.backToWalk() else controller.goHome()
    }
    Box(Modifier.fillMaxSize().background(Karavan.Sand)) {
        when (controller.screen) {
            Screen.Home -> HomeScreen(controller, onStartWalk)
            Screen.Walk -> WalkScreen(controller, map)
            Screen.Stop -> StopScreen(controller)
            Screen.Finale -> FinaleScreen(controller)
            Screen.Album -> AlbumScreen(controller)
        }
    }
}

@Composable
private fun HomeScreen(c: KaravanController, onStartWalk: () -> Unit) {
    val journey = c.journey
    var confirmReset by remember { mutableStateOf(false) }
    ScrollPage {
        Text(
            "Маленький караван",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Karavan.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Sticker("trosha", Modifier.size(240.dp))
        Panel {
            Text(c.route.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            Text(c.route.subtitle, fontSize = 17.sp, color = Karavan.Ink)
            Text(
                listOfNotNull(
                    "${c.route.stops.size} ${pointsWord(c.route.stops.size)}",
                    c.routeMeters?.let { "${formatDistance(it)} пешком" },
                    c.route.duration
                ).joinToString(" · "),
                fontSize = 15.sp,
                color = Karavan.Muted
            )
            if (journey.started) {
                Text("Найдено ${journey.found} из ${journey.stopCount}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Red)
                CaravanRow(c)
            }
        }
        BigButton(
            when {
                journey.finished -> "Посмотреть награду"
                journey.started -> "Продолжить прогулку"
                else -> "Начать прогулку"
            },
            onClick = onStartWalk
        )
        SoftButton("Мой альбом", c::openAlbum)
        if (journey.started) {
            TextButton(onClick = { confirmReset = true }) { Text("Начать заново", color = Karavan.Muted) }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Начать заново?") },
            text = { Text("Найденные вещи исчезнут из альбома, и Троша снова потеряется.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; c.resetJourney() }) { Text("Начать заново") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun WalkScreen(c: KaravanController, map: MapHooks) {
    val journey = c.journey
    val index = journey.activeIndex.coerceAtMost(c.route.stops.lastIndex)
    val stop = c.route.stops[index]
    val distance = c.distanceToTarget

    // Во время прогулки экран не гаснет: иначе остановится геолокация и Троша не узнает, что мы пришли.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(journey, c.location) { map.onUpdate(journey, c.location) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> MapView(context).also { it.onCreate(null); map.onCreated(it) } },
            onRelease = map.onReleased,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundButton("←", c::goHome)
            RoundButton("⤢", map.onFitAll)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Karavan.Card, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CaravanRow(c)
            Text("Точка ${index + 1}: ${stop.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            if (distance != null) {
                val steps = kidSteps(distance)
                Text("≈ $steps ${stepsWord(steps)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Karavan.Red)
                Text("${formatDistance(distance)} для взрослых", fontSize = 14.sp, color = Karavan.Muted)
            } else {
                Text("Ищем, где мы…", fontSize = 20.sp, color = Karavan.Muted)
            }
            Text("🤝 Держи взрослого за руку", fontSize = 16.sp, color = Karavan.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("🔊") { c.player.play(Clips.GO) }
                BigButton("🔔 Мы на месте!", Modifier.weight(1f), c::arrive)
            }
        }
    }
}

@Composable
private fun StopScreen(c: KaravanController) {
    val index = c.openedStop
    val stop = c.route.stops[index]
    val last = index == c.route.stops.lastIndex
    // Вещь «находится», когда Троша начинает о ней рассказывать; без звука — сразу после рассказа.
    var revealed by remember(index) { mutableStateOf(false) }
    var heardSomething by remember(index) { mutableStateOf(false) }
    val playing = c.player.playing
    LaunchedEffect(playing) {
        if (playing != null) heardSomething = true
        if (playing == Clips.trosha(index) || playing == Clips.task(index) || (playing == null && heardSomething)) {
            revealed = true
        }
    }
    var parentOpen by remember(index) { mutableStateOf(false) }

    ScrollPage {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = c::backToWalk) { Text("← К карте", color = Karavan.Muted) }
        }
        Text("Точка ${index + 1} из ${c.route.stops.size}", fontSize = 15.sp, color = Karavan.Muted)
        Text(stop.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Karavan.Ink, textAlign = TextAlign.Center)
        FoundItem(stop.sticker, revealed)
        if (revealed) {
            Text("Нашлось: ${stop.item}!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Karavan.Red)
        } else {
            TextButton(onClick = { revealed = true }) { Text("Показать находку", color = Karavan.Muted) }
        }
        StoryCard("Рассказчик", stop.narrator)
        StoryCard("Троша", stop.trosha, avatar = "trosha")
        Panel(color = Karavan.Gold.copy(alpha = 0.35f)) {
            Text("⭐ Задание", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
            Text(stop.task, fontSize = 20.sp, color = Karavan.Ink)
        }
        Panel(color = Karavan.Kraft.copy(alpha = 0.6f)) {
            TextButton(onClick = { parentOpen = !parentOpen }) {
                Text(if (parentOpen) "▾ Для взрослых" else "▸ Для взрослых", fontSize = 17.sp, color = Karavan.Ink)
            }
            if (parentOpen) {
                Text(stop.parent, fontSize = 16.sp, color = Karavan.Ink)
                OutlinedButton(onClick = c::toggleParentStory) {
                    Text(if (c.parentStoryPlaying) "Остановить" else "Подробная история голосом")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton("🔊", c::replayStop)
            BigButton(if (last) "Ура! Все вещи нашлись" else "Готово! Идём дальше", Modifier.weight(1f), c::completeStop)
        }
    }
}

@Composable
private fun FinaleScreen(c: KaravanController) {
    val pulse = rememberInfiniteTransition(label = "badge")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "badgeScale"
    )
    ScrollPage {
        Text("Ура!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Karavan.Red)
        Sticker(c.route.badge, Modifier.size(260.dp).scale(scale))
        Text(
            "Ты — Юный караванщик!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Karavan.Ink,
            textAlign = TextAlign.Center
        )
        Text(
            "Все вещи нашлись, и Троша догнал свой караван. Спасибо за помощь!",
            fontSize = 18.sp,
            color = Karavan.Ink,
            textAlign = TextAlign.Center
        )
        CaravanRow(c)
        BigButton("Мой альбом", onClick = c::openAlbum)
        SoftButton("На главную", c::goHome)
    }
}

@Composable
private fun AlbumScreen(c: KaravanController) {
    val journey = c.journey
    ScrollPage {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = c::goHome) { Text("← Назад", color = Karavan.Muted) }
        }
        Text("Мой альбом", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
        Text("Найдено ${journey.found} из ${journey.stopCount}", fontSize = 17.sp, color = Karavan.Muted)
        c.route.stops.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, stop ->
                    val found = journey.isFound(row * 2 + col)
                    AlbumCard(Modifier.weight(1f)) {
                        Sticker(stop.sticker, Modifier.size(120.dp), found = found)
                        Text(
                            if (found) stop.item else "Ещё не нашли",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (found) Karavan.Ink else Karavan.Muted,
                            textAlign = TextAlign.Center
                        )
                        Text(stop.title, fontSize = 13.sp, color = Karavan.Muted, textAlign = TextAlign.Center)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AlbumCard(Modifier.fillMaxWidth()) {
            Sticker(c.route.badge, Modifier.size(150.dp), found = journey.finished)
            Text(
                if (journey.finished) "Юный караванщик" else "Награда за весь маршрут",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Karavan.Ink
            )
        }
    }
}

/** Знак вопроса, который с пружинкой превращается в наклейку найденной вещи. */
@Composable
private fun FoundItem(sticker: String, revealed: Boolean) {
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        if (!revealed) {
            Mystery(Modifier.size(140.dp), fontSize = 80)
        }
        AnimatedVisibility(
            visible = revealed,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
        ) {
            Sticker(sticker, Modifier.size(210.dp))
        }
    }
}

/** Караван из найденных вещей: растёт с каждой точкой. */
@Composable
private fun CaravanRow(c: KaravanController) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        c.route.stops.forEachIndexed { i, stop ->
            if (c.journey.isFound(i)) {
                Sticker(stop.sticker, Modifier.size(52.dp))
            } else {
                Mystery(Modifier.size(40.dp), fontSize = 22)
            }
        }
    }
}

@Composable
private fun StoryCard(who: String, text: String, avatar: String? = null) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Sticker(avatar, Modifier.size(44.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(who, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
        }
        Text(text, fontSize = 18.sp, color = Karavan.Ink)
    }
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun Panel(color: Color = Karavan.Card, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun AlbumCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Karavan.Card)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Red, contentColor = Color.White)
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
        Text(text, fontSize = 18.sp, color = Karavan.Ink)
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Card, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 24.sp)
    }
}

private fun pointsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "точек"
        mod10 == 1 -> "точка"
        mod10 in 2..4 -> "точки"
        else -> "точек"
    }
}
