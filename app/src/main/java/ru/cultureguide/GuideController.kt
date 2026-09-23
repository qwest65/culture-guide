package ru.cultureguide

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yandex.mapkit.geometry.Point
import ru.cultureguide.data.CatalogDatabase
import ru.cultureguide.map.MapController
import ru.cultureguide.map.RouteLeg
import ru.cultureguide.map.WalkingRouteBuilder
import ru.cultureguide.model.City
import ru.cultureguide.model.Place
import ru.cultureguide.model.RouteLine
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.GuidanceEngine
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.distanceMeters
import ru.cultureguide.navigation.formatDistance
import ru.cultureguide.navigation.formatWalkTime
import java.util.Locale

enum class RouteBuildState { EMPTY, BUILDING, READY, ERROR }

/**
 * Состояние путеводителя и все сценарии: выбор города и маршрута, редактирование остановок,
 * автоматическое перестроение пешеходной линии и режим ведения по GPS.
 * Compose-экран только читает состояние и вызывает методы.
 */
class GuideController(
    context: Context,
    private val db: CatalogDatabase,
    private val map: MapController,
    private val routeBuilder: WalkingRouteBuilder,
    private val approachBuilder: WalkingRouteBuilder,
    private val notify: (String) -> Unit
) {
    private val prefs = context.getSharedPreferences("guide", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val engine = GuidanceEngine()

    var cities by mutableStateOf<List<City>>(emptyList()); private set
    var city by mutableStateOf<City?>(null); private set
    var places by mutableStateOf<List<Place>>(emptyList()); private set
    var routes by mutableStateOf<List<RouteLine>>(emptyList()); private set
    var selectedRoute by mutableStateOf<RouteLine?>(null); private set
    var stops by mutableStateOf<List<Place>>(emptyList()); private set
    var activeIndex by mutableStateOf(0); private set
    var legs by mutableStateOf<List<RouteLeg>>(emptyList()); private set
    var buildState by mutableStateOf(RouteBuildState.EMPTY); private set
    var buildProgress by mutableStateOf(0 to 0); private set
    var buildError by mutableStateOf<String?>(null); private set
    var location by mutableStateOf<LocationFix?>(null); private set
    var guiding by mutableStateOf(false); private set
    var followUser by mutableStateOf(false); private set
    var selectedPlace by mutableStateOf<Place?>(null)
    var message by mutableStateOf(""); private set

    private var approachLeg by mutableStateOf<RouteLeg?>(null)
    private var approachOrigin: LocationFix? = null
    private var approachTargetId: Long? = null
    private var approachRequestedAt = 0L
    private var fitAfterBuild = false
    private val rebuildTask = Runnable { rebuild() }

    val finished: Boolean get() = stops.isNotEmpty() && activeIndex >= stops.size
    val target: Place? get() = stops.getOrNull(activeIndex)
    val totalMeters: Double get() = legs.sumOf { it.distanceMeters }

    /** Живое расстояние до текущей цели: по пешеходной линии «от меня», иначе по прямой. */
    val distanceToTarget by derivedStateOf {
        val fix = location
        val goal = stops.getOrNull(activeIndex)
        if (fix == null || goal == null) return@derivedStateOf null
        val straight = distanceMeters(fix.lat, fix.lon, goal.lat, goal.lon)
        val leg = approachLeg
        val origin = approachOrigin
        if (leg != null && origin != null && approachTargetId == goal.id) {
            val moved = distanceMeters(origin.lat, origin.lon, fix.lat, fix.lon)
            if (moved < APPROACH_REUSE_M) return@derivedStateOf maxOf(straight, leg.distanceMeters - moved)
        }
        straight
    }

    /** Весь оставшийся путь: до цели и далее по участкам маршрута. */
    val remainingMeters: Double?
        get() {
            val toTarget = distanceToTarget ?: return null
            return GuidanceEngine.remainingMeters(toTarget, activeIndex, legs.map { it.distanceMeters })
        }

    fun start() {
        db.ensureBundledCatalog()
        cities = db.cities()
        val saved = prefs.getLong(KEY_CITY, -1L)
        (cities.firstOrNull { it.id == saved } ?: cities.firstOrNull())?.let(::selectCity)
    }

    // --- Город и маршрут -------------------------------------------------------------------

    fun selectCity(newCity: City) {
        city = newCity
        prefs.edit().putLong(KEY_CITY, newCity.id).apply()
        places = db.places(newCity.id)
        routes = db.routes(newCity.id)
        stopGuidance()
        // Никаких пустых экранов: сразу открываем первый тематический маршрут города.
        val default = routes.firstOrNull { it.placeIds.size >= 2 }
        if (default != null) {
            selectRoute(default)
        } else {
            setStops(emptyList(), route = null, fit = false)
            map.moveTo(newCity.lat, newCity.lon, 14.5f)
        }
    }

    fun selectRoute(route: RouteLine) {
        stopGuidance()
        setStops(db.routePlaces(route, places), route, fit = true)
        message = "Маршрут «${route.name}»"
    }

    // --- Редактирование остановок ----------------------------------------------------------

    fun isStop(place: Place) = stops.any { it.id == place.id }

    fun togglePlace(place: Place) {
        val updated = if (isStop(place)) stops.filterNot { it.id == place.id } else stops + place
        message = if (updated.size > stops.size) "«${place.name}» добавлен в маршрут" else "«${place.name}» убран из маршрута"
        setStops(updated, route = null, fit = false)
    }

    fun moveStop(from: Int, to: Int) {
        if (from !in stops.indices || to !in stops.indices || from == to) return
        val updated = stops.toMutableList().apply { add(to, removeAt(from)) }
        setStops(updated, route = null, fit = false)
    }

    fun removeStop(index: Int) {
        stops.getOrNull(index)?.let(::togglePlace)
    }

    /** Полный сброс: линии, счётчики, остановки и режим ведения. */
    fun resetRoute() {
        stopGuidance()
        setStops(emptyList(), route = null, fit = false)
        message = "Маршрут сброшен"
    }

    /**
     * Единая точка изменения списка остановок. Любое изменение сразу стирает старую
     * линию и обнуляет расстояния, а новая линия строится автоматически (с небольшой
     * задержкой, чтобы серия быстрых правок породила один запрос).
     */
    private fun setStops(newStops: List<Place>, route: RouteLine?, fit: Boolean) {
        val previousTarget = stops.getOrNull(activeIndex)?.id
        selectedRoute = route ?: selectedRoute?.takeIf { it.placeIds == newStops.map { p -> p.id } }
        stops = newStops
        activeIndex = when {
            route != null -> 0
            previousTarget != null -> newStops.indexOfFirst { it.id == previousTarget }.takeIf { it >= 0 } ?: 0
            else -> activeIndex.coerceAtMost(newStops.size)
        }

        handler.removeCallbacks(rebuildTask)
        routeBuilder.cancel()
        clearApproach()
        map.clearRoute()
        legs = emptyList()
        buildError = null
        buildProgress = 0 to 0
        fitAfterBuild = fit
        refreshPlaces()

        if (fit && newStops.isNotEmpty()) map.fit(newStops.map { Point(it.lat, it.lon) })
        if (newStops.size >= 2) {
            buildState = RouteBuildState.BUILDING
            handler.postDelayed(rebuildTask, REBUILD_DELAY_MS)
        } else {
            buildState = RouteBuildState.EMPTY
            if (newStops.isEmpty()) guiding = false
        }
        refreshApproach(force = true)
    }

    fun retryBuild() {
        if (stops.size < 2) return
        buildState = RouteBuildState.BUILDING
        rebuild()
    }

    private fun rebuild() {
        val points = stops.map { Point(it.lat, it.lon) }
        buildError = null
        buildProgress = 0 to points.size - 1
        routeBuilder.build(
            points,
            onProgress = { done, total -> buildProgress = done to total },
            onComplete = { built ->
                legs = built
                buildState = RouteBuildState.READY
                map.showRoute(built, activeIndex)
                if (fitAfterBuild && !followUser) {
                    map.fit(built.flatMap { it.geometry.points })
                }
                fitAfterBuild = false
                message = "Пешком ${formatDistance(totalMeters)} · ${formatWalkTime(totalMeters)}"
            },
            onError = { error ->
                legs = emptyList()
                map.clearRoute()
                buildState = RouteBuildState.ERROR
                buildError = error
                message = error
            }
        )
    }

    // --- Ведение --------------------------------------------------------------------------

    fun setActive(index: Int) {
        if (index !in 0..stops.size) return
        activeIndex = index
        onActiveChanged()
        stops.getOrNull(index)?.let { map.moveTo(it.lat, it.lon, 16.5f) }
    }

    fun skipTarget() {
        val skipped = target ?: return
        activeIndex += 1
        message = "Пропущено: ${skipped.name}"
        onActiveChanged()
        announceTarget()
    }

    fun restartRoute() {
        activeIndex = 0
        onActiveChanged()
        announceTarget()
    }

    /** Делает объект текущей целью, при необходимости добавляя его в маршрут следующим. */
    fun goTo(place: Place) {
        val index = stops.indexOfFirst { it.id == place.id }
        if (index >= 0) {
            setActive(index)
        } else {
            val insertAt = activeIndex.coerceAtMost(stops.size)
            setStops(stops.toMutableList().apply { add(insertAt, place) }, route = null, fit = false)
            activeIndex = insertAt
            onActiveChanged()
        }
        message = "Идём к «${place.name}»"
    }

    fun startGuidance() {
        if (stops.isEmpty()) return
        if (finished) activeIndex = 0
        guiding = true
        followUser = true
        onActiveChanged()
        val fix = location
        if (fix == null) {
            message = "Ищем вас по GPS…"
        } else {
            map.moveTo(fix.lat, fix.lon, 16.5f)
            announceTarget()
        }
    }

    fun stopGuidance() {
        guiding = false
        followUser = false
    }

    fun toggleFollow() {
        followUser = !followUser
        location?.takeIf { followUser }?.let { map.moveTo(it.lat, it.lon, 16.5f) }
        message = if (followUser) "Карта следует за вами" else "Слежение выключено"
    }

    fun showMyLocation(): Boolean {
        val fix = location ?: return false
        map.moveTo(fix.lat, fix.lon, 16.5f)
        return true
    }

    fun onLocation(raw: Location) {
        val fix = LocationFix(raw.latitude, raw.longitude, if (raw.hasAccuracy()) raw.accuracy else null)
        location = fix
        map.showUser(fix.lat, fix.lon)
        if (followUser) map.moveTo(fix.lat, fix.lon)

        if (stops.isEmpty() || finished) return
        val update = engine.update(stops.map { GeoPoint(it.lat, it.lon) }, activeIndex, fix)
        if (update.reached.isNotEmpty()) {
            val reachedPlace = stops[update.reached.last()]
            activeIndex = update.activeIndex
            onActiveChanged()
            if (update.finished) {
                guiding = false
                followUser = false
                message = "Маршрут пройден! Последняя точка: ${reachedPlace.name}"
                notify("Маршрут пройден 🎉")
            } else {
                val next = stops[activeIndex]
                message = "Вы у «${reachedPlace.name}». Далее: ${next.name}"
                notify("Вы у «${reachedPlace.name}». Следующая точка — «${next.name}»")
            }
        } else {
            refreshApproach(force = false)
        }
    }

    private fun announceTarget() {
        val goal = target ?: return
        val distance = distanceToTarget
        message = if (distance != null) "Следующая точка: ${goal.name} · ${formatDistance(distance)}" else "Следующая точка: ${goal.name}"
    }

    private fun onActiveChanged() {
        refreshPlaces()
        if (legs.isNotEmpty()) map.showRoute(legs, activeIndex)
        refreshApproach(force = true)
    }

    private fun refreshPlaces() {
        map.showPlaces(places, stops.map { it.id }, activeIndex)
    }

    /** Пешеходная линия «от меня до цели»; обновляется при смене цели или заметном смещении. */
    private fun refreshApproach(force: Boolean) {
        val fix = location
        val goal = target
        if (fix == null || goal == null || distanceMeters(fix.lat, fix.lon, goal.lat, goal.lon) > APPROACH_MAX_M) {
            clearApproach()
            return
        }
        val origin = approachOrigin
        val now = System.currentTimeMillis()
        val stale = origin == null || approachTargetId != goal.id ||
            (distanceMeters(origin.lat, origin.lon, fix.lat, fix.lon) > APPROACH_REUSE_M && now - approachRequestedAt > APPROACH_MIN_INTERVAL_MS)
        if (!force && !stale) return

        approachOrigin = fix
        approachTargetId = goal.id
        approachRequestedAt = now
        approachBuilder.build(
            listOf(Point(fix.lat, fix.lon), Point(goal.lat, goal.lon)),
            onProgress = { _, _ -> },
            onComplete = { built ->
                approachLeg = built.firstOrNull()
                map.showApproach(approachLeg?.geometry)
            },
            onError = { map.showApproach(null) }
        )
    }

    private fun clearApproach() {
        approachBuilder.cancel()
        approachLeg = null
        approachOrigin = null
        approachTargetId = null
        map.showApproach(null)
    }

    // --- Поиск ----------------------------------------------------------------------------

    fun search(query: String): List<Place> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return places
        return places.filter {
            it.name.lowercase(Locale.getDefault()).contains(q) ||
                it.category.lowercase(Locale.getDefault()).contains(q) ||
                it.address.lowercase(Locale.getDefault()).contains(q) ||
                it.description.lowercase(Locale.getDefault()).contains(q)
        }
    }

    /** Автофокус камеры на найденном объекте с открытием карточки. */
    fun focusPlace(place: Place) {
        followUser = false
        map.moveTo(place.lat, place.lon, 17f)
        selectedPlace = place
    }

    /** Автофокус на всех результатах поиска. */
    fun focusResults(results: List<Place>) {
        when (results.size) {
            0 -> message = "Ничего не найдено"
            1 -> focusPlace(results.first())
            else -> {
                followUser = false
                map.fit(results.map { Point(it.lat, it.lon) })
                message = "Найдено объектов: ${results.size}"
            }
        }
    }

    fun showPlaceOnMap(place: Place) {
        followUser = false
        map.moveTo(place.lat, place.lon, 17f)
    }

    fun routesWith(place: Place): List<RouteLine> = routes.filter { place.id in it.placeIds }

    fun dispose() {
        handler.removeCallbacks(rebuildTask)
        routeBuilder.cancel()
        approachBuilder.cancel()
    }

    private companion object {
        const val KEY_CITY = "city_id"
        const val REBUILD_DELAY_MS = 350L
        const val APPROACH_MAX_M = 5_000.0
        const val APPROACH_REUSE_M = 30.0
        const val APPROACH_MIN_INTERVAL_MS = 15_000L
    }
}
