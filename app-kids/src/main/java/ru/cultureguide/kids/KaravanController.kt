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
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.GuidanceEngine
import ru.cultureguide.navigation.LocationFix

enum class Screen { Home, Walk, Stop, Finale, Album }

/**
 * Состояние «Маленького каравана»: какой экран открыт, сколько вещей Троши найдено
 * и где сейчас ребёнок с родителем.
 */
class KaravanController(
    context: Context,
    val route: KidsRoute,
    /** Объекты общего каталога для точек маршрута, в порядке [KidsRoute.stops]. */
    val places: List<Place>,
    val player: ClipPlayer,
    private val audioGuide: AudioGuide
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // Детский маршрут проходится строго по порядку: засчитываем только текущую точку.
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
    /** Расстояние по прямой до следующей точки, м; null — позиция неизвестна. */
    var distanceToTarget by mutableStateOf<Double?>(null)
        private set

    val parentStoryPlaying: Boolean get() = audioGuide.speakingPlaceId == places.getOrNull(openedStop)?.id

    fun startWalk() {
        stopAudio()
        if (journey.finished) {
            screen = Screen.Finale
            return
        }
        if (!journey.started) {
            journey = journey.start().also(::save)
            player.play(Clips.INTRO, Clips.ROAD)
        } else {
            player.play(Clips.GO)
        }
        screen = Screen.Walk
        location?.let(::updateGuidance)
    }

    fun onLocation(loc: Location) {
        val fix = LocationFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        location = fix
        updateGuidance(fix)
    }

    private fun updateGuidance(fix: LocationFix) {
        if (journey.finished) {
            distanceToTarget = null
            return
        }
        val update = engine.update(points, journey.activeIndex, fix)
        distanceToTarget = update.distanceToTarget
        if (update.reached.isNotEmpty() && screen == Screen.Walk) arrive()
    }

    /** Подошли к точке — по GPS или по кнопке «Мы на месте!». */
    fun arrive() {
        if (journey.finished) return
        openedStop = journey.activeIndex
        screen = Screen.Stop
        audioGuide.stop()
        player.play(Clips.arrival(openedStop))
    }

    fun replayStop() {
        audioGuide.stop()
        player.play(Clips.narrator(openedStop), Clips.trosha(openedStop), Clips.task(openedStop))
    }

    /** Задание выполнено: вещь Троши попадает в альбом, идём дальше. */
    fun completeStop() {
        stopAudio()
        journey = journey.collect(openedStop).also(::save)
        if (journey.finished) {
            screen = Screen.Finale
            player.play(Clips.BELL, Clips.FOUND)
        } else {
            screen = Screen.Walk
            player.play(Clips.FOUND, Clips.GO)
            location?.let(::updateGuidance)
        }
    }

    /** Подробная историческая справка из общего каталога — для взрослых, голосом синтезатора. */
    fun toggleParentStory() {
        player.stop()
        places.getOrNull(openedStop)?.let(audioGuide::toggle)
    }

    /** С экрана точки обратно на карту, не засчитывая находку. */
    fun backToWalk() {
        stopAudio()
        screen = Screen.Walk
    }

    fun openAlbum() {
        stopAudio()
        screen = Screen.Album
    }

    fun goHome() {
        stopAudio()
        screen = Screen.Home
    }

    fun resetJourney() {
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
        return Journey(count, prefs.getInt(KEY_FOUND, 0).coerceIn(0, count), prefs.getBoolean(KEY_STARTED, false))
    }

    private fun save(journey: Journey) {
        prefs.edit()
            .putString(KEY_ROUTE, route.id)
            .putInt(KEY_FOUND, journey.found)
            .putBoolean(KEY_STARTED, journey.started)
            .apply()
    }

    private companion object {
        const val PREFS = "journey"
        const val KEY_ROUTE = "route"
        const val KEY_FOUND = "found"
        const val KEY_STARTED = "started"
    }
}
