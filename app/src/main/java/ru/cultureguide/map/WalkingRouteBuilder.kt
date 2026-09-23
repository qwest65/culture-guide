package ru.cultureguide.map

import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.Route
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError
import ru.cultureguide.navigation.WALK_SPEED_M_PER_MIN
import ru.cultureguide.navigation.polylineLengthMeters

/** Пешеходный участок между двумя соседними остановками. */
data class RouteLeg(
    val index: Int,
    val geometry: Polyline,
    val distanceMeters: Double,
    val timeSeconds: Double
)

/**
 * Длина пешеходного маршрута MapKit.
 *
 * `RouteMetadata.weight.walkingDistance` — это [com.yandex.mapkit.LocalizedValue], где число
 * лежит в `value` (метры), а `text` — только подпись вида «1,2 км». Для части ответов
 * вес маршрута приходит пустым, поэтому считаем по секциям, а в крайнем случае — по геометрии.
 */
fun Route.walkingDistanceMeters(): Double {
    val total = metadata?.weight?.walkingDistance?.value ?: 0.0
    if (total > 0.0) return total
    val bySections = sections.orEmpty().sumOf { it.metadata?.weight?.walkingDistance?.value ?: 0.0 }
    if (bySections > 0.0) return bySections
    return geometry.lengthMeters()
}

/** Время в пути, секунды; при отсутствии данных оценивается по средней скорости пешехода. */
fun Route.walkingTimeSeconds(distanceMeters: Double): Double {
    val time = metadata?.weight?.time?.value ?: 0.0
    return if (time > 0.0) time else distanceMeters / WALK_SPEED_M_PER_MIN * 60.0
}

fun Polyline.lengthMeters(): Double =
    polylineLengthMeters(points.orEmpty().map { it.latitude to it.longitude })

/**
 * Строит пешеходный маршрут через все точки. PedestrianRouter надёжно работает с парой точек,
 * поэтому каждый участок запрашивается отдельно (параллельно), а результат собирается по индексам.
 * Любой новый запрос или [cancel] инвалидирует ответы предыдущих запросов.
 * Для независимых запросов (например, «от меня до цели») нужен отдельный экземпляр.
 */
class WalkingRouteBuilder(private val router: PedestrianRouter) {
    private val sessions = mutableListOf<Session>()
    // MapKit держит слушателей слабо — сохраняем сильные ссылки до конца запроса.
    private val listeners = mutableListOf<Session.RouteListener>()
    private var generation = 0

    fun cancel() {
        generation++
        sessions.forEach { it.cancel() }
        sessions.clear()
        listeners.clear()
    }

    fun build(
        points: List<Point>,
        onProgress: (done: Int, total: Int) -> Unit,
        onComplete: (List<RouteLeg>) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        val total = points.size - 1
        if (total < 1) {
            onComplete(emptyList())
            return
        }
        val token = generation
        val legs = arrayOfNulls<RouteLeg>(total)
        var done = 0
        var failed = false

        for (index in 0 until total) {
            request(points[index], points[index + 1], token) { leg, error ->
                if (token != generation || failed) return@request
                if (leg == null) {
                    failed = true
                    cancel()
                    onError(error ?: "Не удалось построить участок ${index + 1}")
                    return@request
                }
                legs[index] = leg.copy(index = index)
                done++
                onProgress(done, total)
                if (done == total) {
                    sessions.clear()
                    listeners.clear()
                    onComplete(legs.filterNotNull())
                }
            }
        }
    }

    private fun request(
        from: Point,
        to: Point,
        token: Int,
        callback: (RouteLeg?, String?) -> Unit
    ): Session {
        val listener = object : Session.RouteListener {
            override fun onMasstransitRoutes(routes: MutableList<Route>) {
                listeners.remove(this)
                if (token != generation) return
                val route = routes.firstOrNull()
                if (route == null) {
                    callback(null, "Пешеходный маршрут не найден")
                    return
                }
                val meters = route.walkingDistanceMeters()
                callback(RouteLeg(0, route.geometry, meters, route.walkingTimeSeconds(meters)), null)
            }

            override fun onMasstransitRoutesError(error: Error) {
                listeners.remove(this)
                if (token != generation) return
                callback(null, if (error is NetworkError) "Нет сети для построения маршрута" else "Сервис маршрутов недоступен")
            }
        }
        listeners += listener
        val session = router.requestRoutes(
            listOf(
                RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
                RequestPoint(to, RequestPointType.WAYPOINT, null, null, null)
            ),
            TimeOptions(),
            RouteOptions(FitnessOptions(false, false)),
            listener
        )
        sessions += session
        return session
    }
}
