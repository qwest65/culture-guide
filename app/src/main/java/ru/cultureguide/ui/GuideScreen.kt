package ru.cultureguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.delay
import ru.cultureguide.GuideController
import ru.cultureguide.RouteBuildState
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.distanceMeters
import ru.cultureguide.navigation.formatDistance
import ru.cultureguide.navigation.formatWalkTime
import kotlin.math.roundToInt

private val SheetPeek = 290.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    controller: GuideController,
    onMapCreated: (MapView) -> Unit,
    onMapReleased: (MapView) -> Unit,
    onInsetsChanged: (topPx: Int, bottomPx: Int) -> Unit,
    onZoom: (Float) -> Unit,
    onMyLocation: () -> Unit,
    onStartGuidance: () -> Unit,
    onOpenExternal: (Place) -> Unit,
    onOpenSource: (String) -> Unit
) {
    var cityDialog by remember { mutableStateOf(false) }
    var routeDialog by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    var topPanelBottomPx by remember { mutableIntStateOf(0) }
    val peekPx = with(LocalDensity.current) { SheetPeek.roundToPx() }

    LaunchedEffect(topPanelBottomPx, peekPx) { onInsetsChanged(topPanelBottomPx, peekPx) }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SheetPeek,
        sheetContainerColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetShadowElevation = 8.dp,
        sheetDragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFBEC2C9))
            )
        },
        sheetContent = {
            RouteSheet(
                controller = controller,
                onStartGuidance = onStartGuidance,
                onChooseRoute = { routeDialog = true }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> MapView(context).also(onMapCreated) },
                onRelease = onMapReleased
            )

            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Камера центрирует объекты ниже этой панели и выше шторки.
                Column(
                    Modifier.onGloballyPositioned { topPanelBottomPx = it.boundsInRoot().bottom.roundToInt() },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopPanel(
                        controller = controller,
                        onCityClick = { cityDialog = true },
                        onRouteClick = { routeDialog = true },
                        onSearchClick = { searchDialog = true }
                    )
                    GuidanceBanner(controller)
                }
                StatusChip(controller.message)
            }

            MapControls(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, bottom = SheetPeek / 2),
                followUser = controller.followUser,
                onZoomIn = { onZoom(1f) },
                onZoomOut = { onZoom(-1f) },
                onMyLocation = onMyLocation,
                onToggleFollow = controller::toggleFollow
            )
        }
    }

    if (cityDialog) {
        PickerDialog(
            title = "Выберите город",
            items = controller.cities,
            label = { "${it.name}, ${it.country}" },
            selected = { it.id == controller.city?.id },
            onPick = { controller.selectCity(it); cityDialog = false },
            onDismiss = { cityDialog = false }
        )
    }

    if (routeDialog) {
        PickerDialog(
            title = "Тематические маршруты",
            items = controller.routes,
            label = { "${it.name} · ${it.placeIds.size} ${plural(it.placeIds.size, "объект", "объекта", "объектов")}" },
            subtitle = { it.description },
            selected = { it.id == controller.selectedRoute?.id },
            onPick = { controller.selectRoute(it); routeDialog = false },
            onDismiss = { routeDialog = false },
            empty = "В этом городе пока нет маршрутов."
        )
    }

    if (searchDialog) {
        SearchDialog(
            controller = controller,
            onDismiss = { searchDialog = false }
        )
    }

    controller.selectedPlace?.let { place ->
        PlaceCard(
            place = place,
            controller = controller,
            onDismiss = { controller.selectedPlace = null },
            onOpenExternal = onOpenExternal,
            onOpenSource = onOpenSource
        )
    }
}

@Composable
private fun TopPanel(
    controller: GuideController,
    onCityClick: () -> Unit,
    onRouteClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 5.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCityClick)
                ) {
                    Text("Культурный гид", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    val city = controller.city
                    Text(
                        if (city != null) "${city.name}, ${city.country} ▾" else "Выбрать город ▾",
                        fontSize = 13.sp,
                        color = Palette.Muted
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRouteClick),
                    shape = RoundedCornerShape(12.dp),
                    color = Palette.PrimarySoft
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, Modifier.size(18.dp), tint = Palette.Primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Маршруты ▾", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Primary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSearchClick),
                shape = RoundedCornerShape(12.dp),
                color = Palette.Field
            ) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = Palette.Muted)
                    Spacer(Modifier.width(8.dp))
                    Text("Найти музей, храм, памятник…", fontSize = 13.sp, color = Palette.Muted, maxLines = 1)
                }
            }
        }
    }
}

/** Панель режима ведения: текущая цель и живое расстояние до неё. */
@Composable
private fun GuidanceBanner(controller: GuideController) {
    val stops = controller.stops
    if (stops.isEmpty()) return

    if (controller.finished) {
        Surface(shape = RoundedCornerShape(16.dp), color = Palette.Success, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Маршрут пройден!", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                TextButton(onClick = controller::restartRoute) { Text("Пройти заново", color = Color.White) }
            }
        }
        return
    }

    val target = controller.target ?: return
    val distance = controller.distanceToTarget
    val remaining = controller.remainingMeters
    val guiding = controller.guiding
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (guiding) Palette.Primary else Color.White,
        shadowElevation = 4.dp
    ) {
        val onColor = if (guiding) Color.White else Palette.Ink
        val subColor = if (guiding) Color.White.copy(alpha = 0.8f) else Palette.Muted
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (guiding) Color.White else Palette.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${controller.activeIndex + 1}",
                    color = if (guiding) Palette.Primary else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Следующая точка", fontSize = 11.sp, color = subColor)
                Text(target.name, color = onColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        distance == null -> "Включите геолокацию, чтобы видеть расстояние"
                        remaining != null && controller.activeIndex < stops.lastIndex ->
                            "${formatDistance(distance)} · ${formatWalkTime(distance)} · весь путь ${formatDistance(remaining)}"
                        else -> "${formatDistance(distance)} · ${formatWalkTime(distance)}"
                    },
                    fontSize = 12.sp,
                    color = subColor
                )
            }
            TextButton(onClick = controller::skipTarget) {
                Text("Пропустить", color = if (guiding) Color.White else Palette.Primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatusChip(message: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        visible = message.isNotBlank()
        delay(4_000)
        visible = false
    }
    if (!visible) return
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 3.dp) {
        Text(
            message,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MapControls(
    modifier: Modifier,
    followUser: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapButton(description = "Приблизить", onClick = onZoomIn) { Text("+", fontSize = 22.sp, color = Palette.Ink) }
        MapButton(description = "Отдалить", onClick = onZoomOut) { Text("−", fontSize = 22.sp, color = Palette.Ink) }
        MapButton(description = "Показать, где я", onClick = onMyLocation) {
            Icon(Icons.Default.LocationOn, null, tint = Palette.Primary)
        }
        MapButton(
            description = if (followUser) "Выключить слежение за мной" else "Следовать за мной",
            onClick = onToggleFollow,
            active = followUser
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (followUser) Color.White else Palette.Ink)
        }
    }
}

@Composable
private fun MapButton(
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (active) Palette.Primary else Color.White, shadowElevation = 4.dp) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp).semantics { contentDescription = description }
        ) { content() }
    }
}

@Composable
private fun RouteSheet(
    controller: GuideController,
    onStartGuidance: () -> Unit,
    onChooseRoute: () -> Unit
) {
    val stops = controller.stops
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SheetPeek)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            controller.selectedRoute?.let { "Маршрут «${it.name}»" } ?: if (stops.isEmpty()) "Маршрут не выбран" else "Мой маршрут",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        RouteSummary(controller)
        if (controller.audio.available && stops.isNotEmpty()) {
            AudioToggle(controller.autoNarrate, controller::toggleAutoNarrate)
        }
        Spacer(Modifier.height(8.dp))

        if (stops.isEmpty()) {
            EmptyRoute(onChooseRoute)
            return@Column
        }

        ProgressDots(stops.size, controller.activeIndex)
        HorizontalDivider(color = Palette.Line)

        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
            itemsIndexed(stops, key = { _, place -> place.id }) { index, place ->
                StopRow(controller, index, place)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (controller.guiding) {
                Button(
                    onClick = controller::stopGuidance,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Остановить ведение", fontSize = 15.sp) }
            } else {
                Button(
                    onClick = onStartGuidance,
                    enabled = stops.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.finished) "Пройти заново" else "Начать маршрут", fontSize = 15.sp)
                }
            }
            OutlinedButton(
                onClick = controller::resetRoute,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Сбросить")
            }
        }
    }
}

@Composable
private fun RouteSummary(controller: GuideController) {
    val stops = controller.stops
    when (controller.buildState) {
        RouteBuildState.EMPTY -> Text(
            if (stops.size == 1) "Добавьте ещё одну точку — линия построится сама" else "Пешеходная линия строится автоматически",
            fontSize = 13.sp,
            color = Palette.Muted
        )
        RouteBuildState.BUILDING -> Column {
            val (done, total) = controller.buildProgress
            Text("Прокладываем пешеходный путь… $done/$total", fontSize = 13.sp, color = Palette.Muted)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Palette.Primary,
                trackColor = Palette.Upcoming
            )
        }
        RouteBuildState.READY -> Text(
            "${stops.size} ${plural(stops.size, "остановка", "остановки", "остановок")} · " +
                "${formatDistance(controller.totalMeters)} · ${formatWalkTime(controller.totalMeters)} пешком",
            fontSize = 13.sp,
            color = Palette.Muted
        )
        RouteBuildState.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(controller.buildError ?: "Не удалось построить маршрут", Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFFC62828))
            TextButton(onClick = controller::retryBuild) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun AudioToggle(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Palette.PrimarySoft else Palette.Field)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (enabled) "🔊" else "🔇", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (enabled) "Аудиогид: рассказ у каждой точки" else "Аудиогид выключен",
            fontSize = 12.sp,
            color = if (enabled) Palette.Primary else Palette.Muted
        )
    }
}

@Composable
private fun EmptyRoute(onChooseRoute: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Выберите готовый тематический маршрут или нажмите на объект на карте и добавьте его в свой маршрут.",
            textAlign = TextAlign.Center,
            color = Palette.Muted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onChooseRoute, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Place, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Выбрать маршрут")
        }
    }
}

@Composable
private fun ProgressDots(count: Int, activeIndex: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until count) {
            StatusBadge(index, activeIndex, small = true)
            if (index < count - 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (index + 1 <= activeIndex) Palette.Success else Palette.Upcoming)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(index: Int, activeIndex: Int, small: Boolean = false) {
    val size = if (small) 20.dp else 26.dp
    val (bg, fg) = when {
        index < activeIndex -> Palette.Success to Color.White
        index == activeIndex -> Palette.Accent to Color.White
        else -> Palette.Upcoming to Palette.Muted
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (index < activeIndex) {
            Icon(Icons.Default.Check, "Пройдено", tint = fg, modifier = Modifier.size(size * 0.62f))
        } else {
            Text("${index + 1}", color = fg, fontSize = if (small) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StopRow(controller: GuideController, index: Int, place: Place) {
    val active = controller.activeIndex
    val current = index == active
    val legs = controller.legs
    val toTarget = controller.distanceToTarget
    val hint = when {
        index < active -> "пройдено"
        current && toTarget != null -> "${formatDistance(toTarget)} от вас"
        current -> "текущая цель"
        toTarget != null && legs.size >= index -> {
            val ahead = toTarget + (active until index).sumOf { legs.getOrNull(it)?.distanceMeters ?: 0.0 }
            "через ${formatDistance(ahead)}"
        }
        index > 0 && legs.getOrNull(index - 1) != null -> "+${formatDistance(legs[index - 1].distanceMeters)}"
        else -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) Palette.AccentSoft else Color.Transparent)
            .clickable { controller.selectedPlace = place }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge(index, active)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place.name,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (index < active) Palette.Muted else Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Text(listOf(place.category, hint).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
        }
        if (!current && index >= active) {
            TextButton(onClick = { controller.setActive(index) }) { Text("Цель", fontSize = 12.sp) }
        }
        SmallIconButton(Icons.Default.KeyboardArrowUp, "Переместить выше", enabled = index > 0) { controller.moveStop(index, index - 1) }
        SmallIconButton(Icons.Default.KeyboardArrowDown, "Переместить ниже", enabled = index < controller.stops.lastIndex) { controller.moveStop(index, index + 1) }
        SmallIconButton(Icons.Default.Close, "Убрать из маршрута") { controller.removeStop(index) }
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, description, Modifier.size(18.dp))
    }
}

@Composable
private fun <T> PickerDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: ((T) -> String)? = null,
    empty: String = "Список пуст."
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) Text(empty)
                items.forEach { item ->
                    val isSelected = selected(item)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Palette.PrimarySoft else Color.Transparent)
                            .clickable { onPick(item) }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(label(item), fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        subtitle?.invoke(item)?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, color = Palette.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun SearchDialog(controller: GuideController, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = controller.search(query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск объектов") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Название, категория или адрес") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text("Ничего не найдено", color = Palette.Muted, modifier = Modifier.padding(8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { place ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDismiss()
                                    controller.focusPlace(place)
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp)
                        ) {
                            Text(place.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${place.category} · ${place.address}", fontSize = 12.sp, color = Palette.Muted, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = results.isNotEmpty(),
                onClick = {
                    onDismiss()
                    controller.focusResults(results)
                }
            ) { Text("Показать на карте") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceCard(
    place: Place,
    controller: GuideController,
    onDismiss: () -> Unit,
    onOpenExternal: (Place) -> Unit,
    onOpenSource: (String) -> Unit
) {
    val inRoute = controller.isStop(place)
    val isTarget = controller.target?.id == place.id
    val routes = controller.routesWith(place)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = Palette.PrimarySoft) {
                Text(
                    place.category,
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(place.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            if (place.address.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = Palette.Muted)
                    Spacer(Modifier.width(4.dp))
                    Text(place.address, fontSize = 14.sp, color = Palette.Muted)
                }
            }
            controller.location?.let { fix ->
                val meters = distanceMeters(fix.lat, fix.lon, place.lat, place.lon)
                Text("${formatDistance(meters)} от вас · ${formatWalkTime(meters)} пешком", fontSize = 13.sp, color = Palette.Muted)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("История", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (controller.audio.available && place.description.isNotBlank()) {
                    val speaking = controller.audio.speakingPlaceId == place.id
                    OutlinedButton(onClick = { controller.audio.toggle(place) }, shape = RoundedCornerShape(12.dp)) {
                        Icon(if (speaking) Icons.Default.Close else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (speaking) "Остановить" else "Слушать")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            place.description.ifBlank { "Описание пока не добавлено в каталог." }
                .split("\n")
                .filter { it.isNotBlank() }
                .forEach { paragraph ->
                    Text(paragraph.trim(), fontSize = 15.sp, lineHeight = 22.sp, color = Palette.Ink)
                    Spacer(Modifier.height(8.dp))
                }

            if (routes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Входит в маршруты: " + routes.joinToString { "«${it.name}»" },
                    fontSize = 13.sp,
                    color = Palette.Muted
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        controller.goTo(place)
                        onDismiss()
                    },
                    enabled = !isTarget,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (isTarget) "Уже цель" else "Идти сюда") }
                OutlinedButton(
                    onClick = {
                        controller.togglePlace(place)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (inRoute) "Убрать из маршрута" else "В маршрут", maxLines = 1) }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = {
                    controller.showPlaceOnMap(place)
                    onDismiss()
                }) { Text("Показать на карте") }
                TextButton(onClick = { onOpenExternal(place) }) { Text("В Яндекс Картах") }
                if (place.sourceUrl.isNotBlank()) {
                    TextButton(onClick = { onOpenSource(place.sourceUrl) }) { Text("Источник") }
                }
            }
        }
    }
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
