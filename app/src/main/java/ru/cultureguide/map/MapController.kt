package ru.cultureguide.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import com.yandex.mapkit.Animation
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.LineStyle
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.cultureguide.R
import ru.cultureguide.model.Place
import java.lang.ref.WeakReference

/**
 * Всё, что рисуется на карте. Каждый тип объектов живёт в своей коллекции,
 * поэтому сброс маршрута — это `clear()` коллекции маршрута: полилинии
 * гарантированно исчезают, а метки объектов и пользователя не трогаются.
 *
 * Контроллер помнит последнее состояние и повторно применяет его при
 * подключении нового MapView (поворот экрана, пересоздание Compose-дерева).
 */
class MapController(
    private val context: Context,
    private val onPlaceTap: (Place) -> Unit
) {
    private var mapView: MapView? = null
    private var routeLayer: MapObjectCollection? = null
    private var approachLayer: MapObjectCollection? = null
    private var placesLayer: MapObjectCollection? = null
    private var userLayer: MapObjectCollection? = null
    private var userMark: PlacemarkMapObject? = null

    private var places: List<Place> = emptyList()
    private var stopIds: List<Long> = emptyList()
    private var activeIndex = 0
    private var legs: List<RouteLeg> = emptyList()
    private var approach: Polyline? = null
    private var user: Point? = null
    private var pendingCamera: CameraPosition? = null
    private var topInsetPx = 0
    private var bottomInsetPx = 0

    private val icons by lazy {
        mapOf(
            PinKind.PLACE to vectorImage(R.drawable.ic_place_pin),
            PinKind.STOP to vectorImage(R.drawable.ic_stop_pin),
            PinKind.TARGET to vectorImage(R.drawable.ic_target_pin),
            PinKind.DONE to vectorImage(R.drawable.ic_done_pin)
        )
    }
    private val userIcon by lazy { vectorImage(R.drawable.ic_user_dot) }

    // MapKit хранит слушатель по слабой ссылке — держим его в поле.
    private val tapListener = MapObjectTapListener { mapObject, _ ->
        (mapObject.userData as? Place)?.let(onPlaceTap)
        true
    }

    fun attach(view: MapView) {
        if (mapView === view) return
        mapView = view
        val root = view.mapWindow.map.mapObjects
        root.clear()
        routeLayer = root.addCollection().apply { zIndex = 10f }
        approachLayer = root.addCollection().apply { zIndex = 15f }
        placesLayer = root.addCollection().apply { zIndex = 30f }
        userLayer = root.addCollection().apply { zIndex = 60f }
        userMark = null
        applyFocusRect()
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyFocusRect() }
        renderPlaces()
        renderRoute()
        renderApproach()
        renderUser()
        pendingCamera?.let {
            view.mapWindow.map.move(it)
            pendingCamera = null
        }
    }

    fun detach(view: MapView) {
        if (mapView !== view) return
        mapView = null
        routeLayer = null
        approachLayer = null
        placesLayer = null
        userLayer = null
        userMark = null
    }

    /** Области экрана, перекрытые верхней панелью и шторкой, — камера центрирует всё между ними. */
    fun setInsets(topPx: Int, bottomPx: Int) {
        if (topPx == topInsetPx && bottomPx == bottomInsetPx) return
        topInsetPx = topPx
        bottomInsetPx = bottomPx
        applyFocusRect()
    }

    fun showPlaces(places: List<Place>, stopIds: List<Long>, activeIndex: Int) {
        this.places = places
        this.stopIds = stopIds
        this.activeIndex = activeIndex
        renderPlaces()
    }

    fun showRoute(legs: List<RouteLeg>, activeIndex: Int) {
        this.legs = legs
        this.activeIndex = activeIndex
        renderRoute()
    }

    fun showApproach(polyline: Polyline?) {
        approach = polyline
        renderApproach()
    }

    /** Полностью убирает линии маршрута и подход к цели. */
    fun clearRoute() {
        legs = emptyList()
        approach = null
        routeLayer?.clear()
        approachLayer?.clear()
    }

    fun showUser(lat: Double, lon: Double) {
        user = Point(lat, lon)
        renderUser()
    }

    fun moveTo(lat: Double, lon: Double, zoom: Float? = null, animated: Boolean = true) {
        val map = mapView?.mapWindow?.map
        val target = Point(lat, lon)
        if (map == null) {
            pendingCamera = CameraPosition(target, zoom ?: DEFAULT_ZOOM, 0f, 0f)
            return
        }
        val current = map.cameraPosition
        move(CameraPosition(target, zoom ?: current.zoom, current.azimuth, current.tilt), animated)
    }

    /** Подбирает камеру так, чтобы все точки поместились в видимую область. */
    fun fit(points: List<Point>, maxZoom: Float = 17f) {
        if (points.isEmpty()) return
        if (points.size == 1 || points.all { it.latitude == points[0].latitude && it.longitude == points[0].longitude }) {
            moveTo(points[0].latitude, points[0].longitude, 16.5f)
            return
        }
        val box = BoundingBox(
            Point(points.minOf { it.latitude }, points.minOf { it.longitude }),
            Point(points.maxOf { it.latitude }, points.maxOf { it.longitude })
        )
        val map = mapView?.mapWindow?.map
        if (map == null) {
            val center = Point((box.southWest.latitude + box.northEast.latitude) / 2, (box.southWest.longitude + box.northEast.longitude) / 2)
            pendingCamera = CameraPosition(center, 14.5f, 0f, 0f)
            return
        }
        val fitted = map.cameraPosition(Geometry.fromBoundingBox(box))
        move(CameraPosition(fitted.target, (fitted.zoom - FIT_PADDING_ZOOM).coerceAtMost(maxZoom), 0f, 0f), true)
    }

    fun zoomBy(delta: Float) {
        val map = mapView?.mapWindow?.map ?: return
        val current = map.cameraPosition
        move(CameraPosition(current.target, (current.zoom + delta).coerceIn(3f, 20f), current.azimuth, current.tilt), true)
    }

    private fun move(position: CameraPosition, animated: Boolean) {
        val map = mapView?.mapWindow?.map ?: return
        if (animated) map.move(position, Animation(Animation.Type.SMOOTH, 0.6f), null) else map.move(position)
    }

    private fun applyFocusRect() {
        val window = mapView?.mapWindow ?: return
        val width = window.width()
        val height = window.height()
        if (width <= 0 || height <= 0) return
        val top = topInsetPx.coerceIn(0, height / 2)
        val bottom = (height - bottomInsetPx).coerceIn(top + 1, height)
        window.focusRect = ScreenRect(ScreenPoint(0f, top.toFloat()), ScreenPoint(width.toFloat(), bottom.toFloat()))
    }

    private fun renderPlaces() {
        val layer = placesLayer ?: return
        layer.clear()
        places.forEach { place ->
            val stopIndex = stopIds.indexOf(place.id)
            val kind = when {
                stopIndex < 0 -> PinKind.PLACE
                stopIndex < activeIndex -> PinKind.DONE
                stopIndex == activeIndex -> PinKind.TARGET
                else -> PinKind.STOP
            }
            layer.addPlacemark().apply {
                geometry = Point(place.lat, place.lon)
                setIcon(icons.getValue(kind), IconStyle().apply {
                    anchor = PointF(0.5f, 1f)
                    scale = if (kind == PinKind.TARGET) 1.0f else if (kind == PinKind.PLACE) 0.62f else 0.78f
                    zIndex = kind.z
                })
                if (stopIndex >= 0) {
                    setText(
                        (stopIndex + 1).toString(),
                        TextStyle().apply {
                            size = 11f
                            color = Color.rgb(24, 32, 48)
                            outlineColor = Color.WHITE
                            outlineWidth = 2f
                            placement = TextStyle.Placement.RIGHT
                            offset = 2f
                        }
                    )
                }
                userData = place
                addTapListener(WeakReference(tapListener))
            }
        }
    }

    private fun renderRoute() {
        val layer = routeLayer ?: return
        layer.clear()
        legs.forEach { leg ->
            // Участок i ведёт к остановке i + 1; текущий — тот, что ведёт к активной цели.
            val state = when {
                leg.index + 1 < activeIndex -> LegState.PASSED
                leg.index + 1 == activeIndex -> LegState.CURRENT
                else -> LegState.UPCOMING
            }
            layer.addPolyline(leg.geometry).apply {
                setStrokeColor(state.color)
                style = LineStyle()
                    .setStrokeWidth(state.width)
                    .setOutlineColor(Color.WHITE)
                    .setOutlineWidth(1.5f)
                zIndex = state.z
            }
        }
    }

    private fun renderApproach() {
        val layer = approachLayer ?: return
        layer.clear()
        val line = approach ?: return
        layer.addPolyline(line).apply {
            setStrokeColor(Color.rgb(255, 143, 0))
            style = LineStyle()
                .setStrokeWidth(5f)
                .setDashLength(10f)
                .setGapLength(6f)
                .setOutlineColor(Color.WHITE)
                .setOutlineWidth(1f)
        }
    }

    private fun renderUser() {
        val layer = userLayer ?: return
        val point = user ?: return
        val mark = userMark ?: layer.addPlacemark().apply {
            setIcon(
                userIcon,
                IconStyle().apply {
                    anchor = PointF(0.5f, 0.5f)
                    scale = 0.9f
                    zIndex = 100f
                }
            )
            userMark = this
        }
        mark.geometry = point
    }

    /**
     * `ImageProvider.fromResource` декодирует ресурс через BitmapFactory и не умеет
     * векторные drawable (получается пустая иконка), поэтому растеризуем вектор сами.
     */
    private fun vectorImage(id: Int): ImageProvider {
        val drawable = requireNotNull(context.getDrawable(id)) { "Нет drawable $id" }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return ImageProvider.fromBitmap(bitmap)
    }

    private enum class PinKind(val z: Float) { PLACE(1f), DONE(2f), STOP(3f), TARGET(5f) }

    private enum class LegState(val color: Int, val width: Float, val z: Float) {
        PASSED(Color.rgb(158, 166, 178), 5f, 1f),
        UPCOMING(Color.rgb(79, 134, 247), 6f, 2f),
        CURRENT(Color.rgb(21, 88, 214), 8f, 3f)
    }

    private companion object {
        const val DEFAULT_ZOOM = 14.5f
        const val FIT_PADDING_ZOOM = 0.4f
    }
}
