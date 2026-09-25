package ru.cultureguide.kids

import android.content.Context
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.cultureguide.audio.AudioGuide
import ru.cultureguide.kids.audio.ClipPlayer
import ru.cultureguide.kids.content.Clips
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.KidsRoute
import ru.cultureguide.kids.content.RoutePaths
import ru.cultureguide.kids.content.walkingMeters
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.GuidanceEngine
import ru.cultureguide.navigation.LocationFix

enum class Screen { Home, Choose, Walk, Stop, Finale, Album }

/** Итог прогулки для финального экрана. */
data class WalkResult(
    /** Все выбранные точки пройдены, и что-то найдено — дают значок. */
    val complete: Boolean,
    /** Вещи, найденные на этой прогулке, в порядке маршрута. */
    val found: List<Int>
)

/**
 * Состояние «Маленького каравана»: какой экран открыт, куда идём, что уже в альбоме
 * и где сейчас ребёнок с родителем.
 */
class KaravanController(
    context: Context,
    val route: KidsRoute,
    /** Объекты общего каталога для точек маршрута, в порядке [KidsRoute.stops]. */
    val places: List<Place>,
    /** Пешеходные линии между точками; без них расстояние считается по прямой. */
    val paths: RoutePaths,
    val player: ClipPlayer,
    private val audioGuide: AudioGuide
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // Точки проходятся строго по порядку: засчитываем только текущую.
    private val engine = GuidanceEngine(lookAhead = 0)
    private val points = places.map { GeoPoint(it.lat, it.lon) }

    var screen by mutableStateOf(Screen.Home)
        private set
    var journey by mutableStateOf(loadJourney())
        private set
    /** Точка, открытая на экране «Нашли!». */
    var openedStop by mutableStateOf(0)
        private set
    var location by mutableStateOf<LocationFix?>(null)
        private set
    /** Сколько идти до следующей точки, м; null — позиция неизвестна. */
    var distanceToTarget by mutableStateOf<Double?>(null)
        private set
    var result by mutableStateOf<WalkResult?>(null)
        private set

    val parentStoryPlaying: Boolean get() = audioGuide.speakingPlaceId == places.getOrNull(openedStop)?.id

    /** Длина прогулки по выбранным точкам вдоль пешеходных линий; null — линий нет. */
    fun planMeters(plan: List<Int>): Double? = paths.planMeters(plan.sorted())

    fun openChooser() {
        stopAudio()
        screen = Screen.Choose
    }

    /** Новая прогулка по выбранным точкам. */
    fun startWalk(selection: Collection<Int>) {
        if (selection.isEmpty()) return
        stopAudio()
        journey = journey.start(selection).also(::save)
        screen = Screen.Walk
        player.play(Clips.INTRO, Clips.ROAD)
        location?.let(::updateGuidance)
    }

    fun resumeWalk() {
        if (!journey.inProgress) return
        stopAudio()
        screen = Screen.Walk
        player.play(Clips.GO)
        location?.let(::updateGuidance)
    }

    /** Пауза: прогулка сохраняется, её можно продолжить с главного экрана. */
    fun pause() {
        stopAudio()
        screen = Screen.Home
    }

    /** Закончить прогулку раньше; найденные вещи остаются в альбоме. */
    fun finishWalk() {
        if (journey.inProgress) endWalk(complete = false)
    }

    /** Пропустить текущую точку: закрыто, далеко или просто не хочется. */
    fun skipStop() {
        stopAudio()
        journey = journey.skip().also(::save)
        if (journey.walkComplete) {
            endWalk(complete = journey.walkFound.isNotEmpty())
        } else {
            screen = Screen.Walk
            player.play(Clips.GO)
            location?.let(::updateGuidance)
        }
    }

    fun onLocation(loc: Location) {
        val fix = LocationFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        location = fix
        updateGuidance(fix)
    }

    private fun updateGuidance(fix: LocationFix) {
        val target = journey.activeStop
        if (target == null) {
            distanceToTarget = null
            return
        }
        val update = engine.update(journey.plan.map { points[it] }, journey.position, fix)
        // К первой точке прогулки линии нет — идём от того места, где стоим.
        val path = journey.previousStop?.let { paths.between(it, target) }
        distanceToTarget = update.distanceToTarget?.let { walkingMeters(path, fix, it) }
        if (update.reached.isNotEmpty() && screen == Screen.Walk) arrive()
    }

    /** Подошли к точке — по GPS или по кнопке «Мы на месте!». */
    fun arrive() {
        val stop = journey.activeStop ?: return
        openedStop = stop
        screen = Screen.Stop
        audioGuide.stop()
        player.play(Clips.arrival(stop))
    }

    fun replayStop() {
        audioGuide.stop()
        player.play(Clips.narrator(openedStop), Clips.trosha(openedStop), Clips.task(openedStop))
    }

    /** Задание выполнено: вещь Троши попадает в альбом, идём дальше. */
    fun completeStop() {
        stopAudio()
        journey = journey.collect(openedStop).also(::save)
        if (journey.walkComplete) {
            endWalk(complete = true)
        } else {
            screen = Screen.Walk
            player.play(Clips.FOUND, Clips.GO)
            location?.let(::updateGuidance)
        }
    }

    private fun endWalk(complete: Boolean) {
        stopAudio()
        result = WalkResult(complete, journey.walkFound.sorted())
        journey = journey.finish().also(::save)
        distanceToTarget = null
        screen = Screen.Finale
        if (complete) player.play(Clips.BELL, Clips.FINALE) else player.play(Clips.LATER)
    }

    /** С экрана точки обратно на карту, не засчитывая находку. */
    fun backToWalk() {
        stopAudio()
        screen = Screen.Walk
    }

    /** Подробная историческая справка из общего каталога — для взрослых, голосом синтезатора. */
    fun toggleParentStory() {
        player.stop()
        places.getOrNull(openedStop)?.let(audioGuide::toggle)
    }

    fun openAlbum() {
        stopAudio()
        screen = Screen.Album
    }

    fun goHome() {
        stopAudio()
        screen = Screen.Home
    }

    /** Очистить альбом и значок; начатая прогулка тоже сбрасывается. */
    fun resetAlbum() {
        stopAudio()
        journey = journey.reset().also(::save)
        screen = Screen.Home
    }

    fun stopAudio() {
        player.stop()
        audioGuide.stop()
    }

    fun dispose() {
        player.stop()
        audioGuide.shutdown()
    }

    private fun loadJourney(): Journey {
        val count = route.stops.size
        if (prefs.getString(KEY_ROUTE, null) != route.id) return Journey(count)
        fun indices(key: String) =
            prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until count }
        val plan = indices(KEY_PLAN).distinct().sorted()
        return Journey(
            stopCount = count,
            plan = plan,
            position = prefs.getInt(KEY_POSITION, 0).coerceIn(0, plan.size),
            found = indices(KEY_FOUND).toSet(),
            walkFound = indices(KEY_WALK_FOUND).toSet(),
            badge = prefs.getBoolean(KEY_BADGE, false)
        )
    }

    private fun save(journey: Journey) {
        prefs.edit()
            .putString(KEY_ROUTE, route.id)
            .putString(KEY_PLAN, journey.plan.joinToString(","))
            .putInt(KEY_POSITION, journey.position)
            .putString(KEY_FOUND, journey.found.sorted().joinToString(","))
            .putString(KEY_WALK_FOUND, journey.walkFound.sorted().joinToString(","))
            .putBoolean(KEY_BADGE, journey.badge)
            .apply()
    }

    private companion object {
        // Новое имя файла: в «journey» версии 0.1.0 прогресс хранился в другом формате.
        const val PREFS = "journey2"
        const val KEY_ROUTE = "route"
        const val KEY_PLAN = "plan"
        const val KEY_POSITION = "position"
        const val KEY_FOUND = "found"
        const val KEY_WALK_FOUND = "walk_found"
        const val KEY_BADGE = "badge"
    }
}
