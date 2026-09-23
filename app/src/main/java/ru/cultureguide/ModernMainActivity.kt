package ru.cultureguide

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.IconStyle
import com.yandex.runtime.ui_view.ViewProvider
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session as RouteSession
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError
import com.yandex.runtime.image.ImageProvider
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.drawable.ColorDrawable
import java.lang.ref.WeakReference

class ModernMainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var cityButton: TextView
    private lateinit var searchBox: EditText
    private lateinit var sheet: LinearLayout
    private lateinit var sheetTitle: TextView
    private lateinit var sheetSubtitle: TextView
    private lateinit var routeButton: TextView
    private lateinit var statusText: TextView
    private lateinit var routeList: LinearLayout
    private lateinit var progressRow: LinearLayout

    private var cityId = 0L
    private var cities: List<City> = emptyList()
    private var places: List<Place> = emptyList()
    private var routes: List<RouteLine> = emptyList()
    private var selectedRoute: RouteLine? = null
    private var routePlaces: List<Place> = emptyList()
    private var lastLocation: Location? = null
    private var userPlacemark: PlacemarkMapObject? = null
    private val routeObjects = mutableListOf<MapObject>()
    private val routeSessions = mutableListOf<RouteSession>()
    private var pedestrianRouter: PedestrianRouter? = null
    private var routeDistanceMeters = 0.0
    private var activeStopIndex = 0

    private val placeTapListener = MapObjectTapListener { obj, _ ->
        val place = obj.userData as? Place ?: return@MapObjectTapListener false
        showPlace(place)
        true
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            showUserLocation(location.latitude, location.longitude, false)
            statusText.text = "Ваше положение обновлено"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)
        db = Db(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        pedestrianRouter = TransportFactory.getInstance().createPedestrianRouter()
        buildModernUi()
        loadCatalog()
        requestLocation()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeWidthDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), stroke)
        }

    private fun makeIconButton(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(40, 44, 52))
        background = rounded(Color.WHITE, Color.rgb(220, 223, 229), 14, 1)
        elevation = dp(3).toFloat()
    }

    private fun makeModeButton(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(55, 59, 68))
        background = rounded(Color.rgb(246, 247, 249), Color.TRANSPARENT, 13, 0)
    }

    private fun buildModernUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(10), dp(8))
            background = ColorDrawable(Color.WHITE)
            elevation = dp(3).toFloat()
        }
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88)).apply { gravity = Gravity.TOP })

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "Культурный маршрут"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(31, 36, 48))
        }
        titleBlock.addView(title, LinearLayout.LayoutParams(0, dp(28), 1f))
        cityButton = TextView(this).apply {
            text = "Выбор города"
            textSize = 13f
            setTextColor(Color.rgb(105, 111, 122))
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { chooseCity() }
        }
        titleBlock.addView(cityButton, LinearLayout.LayoutParams(-1, dp(24)))
        titleRow.addView(titleBlock, LinearLayout.LayoutParams(0, dp(54), 1f))

        val mode = TextView(this).apply {
            text = "Карта  ▾"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(44, 49, 60))
            background = rounded(Color.rgb(247, 248, 250), Color.rgb(225, 227, 232), 13, 1)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { showRouteChooser() }
        }
        titleRow.addView(mode, LinearLayout.LayoutParams(dp(76), dp(38)).apply { rightMargin = dp(2) })
        topBar.addView(titleRow)

        val utilityRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val search = TextView(this).apply {
            text = "⌕  Найти объект"
            textSize = 13f
            setTextColor(Color.rgb(112, 117, 128))
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(247, 248, 250), Color.TRANSPARENT, 12, 0)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { showSearchDialog() }
        }
        utilityRow.addView(search, LinearLayout.LayoutParams(0, dp(32), 1f))
        val menu = makeIconButton("⋯")
        menu.setOnClickListener { showCatalogMenu() }
        utilityRow.addView(menu, LinearLayout.LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(7) })
        topBar.addView(utilityRow, LinearLayout.LayoutParams(-1, dp(38)))

        val locate = makeIconButton("⌖")
        root.addView(locate, FrameLayout.LayoutParams(dp(46), dp(46)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(104)
            rightMargin = dp(14)
        })
        locate.setOnClickListener {
            requestLocation()
            lastLocation?.let { moveCamera(it.latitude, it.longitude, 16f) }
        }

        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            background = rounded(Color.WHITE, Color.TRANSPARENT, 24, 0)
            elevation = dp(14).toFloat()
        }
        root.addView(sheet, FrameLayout.LayoutParams(-1, dp(452)).apply { gravity = Gravity.BOTTOM })

        val handle = View(this).apply {
            background = rounded(Color.rgb(190, 194, 201), Color.TRANSPARENT, 3, 0)
        }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(38), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        val routeHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val headerBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheetTitle = TextView(this).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(28, 32, 42))
        }
        sheetSubtitle = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(105, 111, 122))
        }
        headerBlock.addView(sheetTitle, LinearLayout.LayoutParams(-1, dp(28)))
        headerBlock.addView(sheetSubtitle, LinearLayout.LayoutParams(-1, dp(22)))
        routeHeader.addView(headerBlock, LinearLayout.LayoutParams(0, dp(52), 1f))

        val routeAction = TextView(this).apply {
            text = "⋯"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 75, 85))
            setOnClickListener { showRouteChooser() }
        }
        routeHeader.addView(routeAction, LinearLayout.LayoutParams(dp(42), dp(42)))
        sheet.addView(routeHeader, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(4) })

        progressRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        sheet.addView(progressRow, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(2) })

        val divider = View(this).apply { setBackgroundColor(Color.rgb(232, 234, 238)) }
        sheet.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        }
        routeList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        scroll.addView(routeList, ScrollView.LayoutParams(-1, -2))
        sheet.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(3) })

        routeButton = TextView(this).apply {
            text = "Построить маршрут"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(39, 112, 239), Color.TRANSPARENT, 16, 0)
            elevation = dp(2).toFloat()
            setOnClickListener {
                if (selectedRoute == null) showRouteChooser() else buildWalkingRoute()
            }
        }
        sheet.addView(routeButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })

        statusText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(120, 125, 135))
            gravity = Gravity.CENTER
            maxLines = 1
        }
        sheet.addView(statusText, LinearLayout.LayoutParams(-1, dp(18)).apply { topMargin = dp(2) })

        searchBox = EditText(this).apply {
            setSingleLine(true)
            visibility = View.GONE
        }

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val topParams = topBar.layoutParams as FrameLayout.LayoutParams
                topParams.topMargin = bars.top
                topBar.layoutParams = topParams
                val bottomParams = sheet.layoutParams as FrameLayout.LayoutParams
                bottomParams.bottomMargin = bars.bottom
                sheet.layoutParams = bottomParams
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Название, категория или адрес"
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
        }
        AlertDialog.Builder(this)
            .setTitle("Найти объект")
            .setView(input)
            .setPositiveButton("Найти") { _, _ ->
                renderMap(input.text.toString())
                renderSheet()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    private fun loadCatalog() {
        cities = db.cities()
        if (cities.isEmpty()) return
        val city = cities.firstOrNull { it.id == cityId } ?: cities.first()
        cityId = city.id
        cityButton.text = city.name + ", " + city.country
        places = db.places(cityId)
        routes = db.routes(cityId)
        selectedRoute = routes.firstOrNull()
        routePlaces = selectedRoute?.let { db.routePlaces(it, places) } ?: emptyList()
        activeStopIndex = 0
        renderMap()
        renderSheet()
    }

    private fun chooseCity() {
        val labels = cities.map { "${it.name}, ${it.country}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Город").setItems(labels) { _, which ->
            cityId = cities[which].id
            selectedRoute = null
            routePlaces = emptyList()
            loadCatalog()
        }.show()
    }

    private fun chooseCategory() {
        val categories = db.categories(cityId).toTypedArray()
        AlertDialog.Builder(this).setTitle("Фильтр объектов").setItems(categories) { _, which ->
            statusText.text = "${filterPlaces(searchBox.text.toString(), categories[which]).size} объектов · ${categories[which]}"
        }.show()
    }

    private fun filterPlaces(query: String, category: String? = null): List<Place> {
        val q = query.trim().lowercase(Locale.getDefault())
        return places.filter {
            (category == null || category == "Все" || it.category == category) &&
                (q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) ||
                    it.category.lowercase(Locale.getDefault()).contains(q) ||
                    it.address.lowercase(Locale.getDefault()).contains(q))
        }
    }

    private fun renderMap(query: String? = null) {
        val objects = mapView.mapWindow.map.mapObjects
        objects.clear()
        routeObjects.clear()
        userPlacemark = null

        val visible = filterPlaces(query ?: "")
        visible.forEachIndexed { index, place ->
            val pinView = TextView(this).apply {
                text = (index + 1).toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(39, 112, 239), Color.WHITE, 13, 2)
                elevation = dp(2).toFloat()
                layoutParams = ViewGroup.LayoutParams(dp(26), dp(26))
            }
            objects.addPlacemark().apply {
                geometry = Point(place.lat, place.lon)
                setView(
                    ViewProvider(pinView),
                    IconStyle().apply {
                        anchor = android.graphics.PointF(0.5f, 0.5f)
                        scale = 1.0f
                        zIndex = 30f
                    }
                )
                userData = place
                addTapListener(WeakReference(placeTapListener))
            }
        }

        cities.firstOrNull { it.id == cityId }?.let { city ->
            if (selectedRoute == null) moveCamera(city.lat, city.lon, 14.8f)
        }
        lastLocation?.let { showUserLocation(it.latitude, it.longitude, false) }
        drawSelectedRoute()
    }

    private fun renderSheet() {
        val city = cities.firstOrNull { it.id == cityId }
        val line = selectedRoute
        val list = routePlaces

        sheetTitle.text = line?.name ?: "Культурные места"
        sheetSubtitle.text = if (line == null) {
            (city?.name ?: "") + " · " + filterPlaces().size + " объектов"
        } else {
            list.size.toString() + " объектов · " + (city?.name ?: "")
        }
        routeButton.text = if (line == null) "Выбрать культурный маршрут" else "Построить маршрут"

        progressRow.removeAllViews()
        if (list.isEmpty()) {
            progressRow.visibility = View.GONE
            routeList.removeAllViews()
            statusText.text = "Выберите культурный маршрут"
            return
        }
        progressRow.visibility = View.VISIBLE

        list.forEachIndexed { index, _ ->
            val marker = TextView(this).apply {
                text = if (index < activeStopIndex) "✓" else (index + 1).toString()
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (index <= activeStopIndex) Color.WHITE else Color.rgb(102, 108, 119))
                background = rounded(
                    if (index < activeStopIndex) Color.rgb(28, 157, 91)
                    else if (index == activeStopIndex) Color.rgb(39, 112, 239)
                    else Color.WHITE,
                    if (index == activeStopIndex) Color.rgb(39, 112, 239) else Color.rgb(205, 208, 215),
                    11,
                    1
                )
            }
            progressRow.addView(marker, LinearLayout.LayoutParams(dp(24), dp(24)))
            if (index < list.lastIndex) {
                val connector = View(this).apply {
                    setBackgroundColor(if (index < activeStopIndex) Color.rgb(28, 157, 91) else Color.rgb(210, 213, 219))
                }
                progressRow.addView(connector, LinearLayout.LayoutParams(0, dp(2), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }
        }

        routeList.removeAllViews()
        list.forEachIndexed { index, place ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
                background = if (index == activeStopIndex) rounded(Color.rgb(238, 245, 255), Color.TRANSPARENT, 10, 0) else null
                setOnClickListener {
                    activeStopIndex = index
                    moveCamera(place.lat, place.lon, 16f)
                    renderSheet()
                }
            }

            val state = TextView(this).apply {
                text = if (index < activeStopIndex) "✓" else (index + 1).toString()
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (index <= activeStopIndex) Color.WHITE else Color.rgb(105, 111, 122))
                background = rounded(
                    if (index < activeStopIndex) Color.rgb(28, 157, 91)
                    else if (index == activeStopIndex) Color.rgb(39, 112, 239)
                    else Color.rgb(241, 242, 245),
                    Color.TRANSPARENT,
                    10,
                    0
                )
            }
            row.addView(state, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })

            val icon = TextView(this).apply {
                text = when {
                    place.category.contains("музе", true) -> "▥"
                    place.category.contains("памят", true) -> "▲"
                    place.category.contains("храм", true) -> "✝"
                    place.category.contains("парк", true) -> "♣"
                    else -> "●"
                }
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (index == activeStopIndex) Color.rgb(39, 112, 239) else Color.rgb(52, 57, 68))
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(26), dp(28)))

            val name = TextView(this).apply {
                text = place.name
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (index == activeStopIndex) Color.rgb(39, 112, 239) else Color.rgb(55, 60, 70))
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(34), 1f))

            val distance = if (index == 0) {
                "0 м"
            } else {
                val a = list[index - 1]
                val result = FloatArray(1)
                Location.distanceBetween(a.lat, a.lon, place.lat, place.lon, result)
                result[0].toInt().toString() + " м"
            }
            val time = if (index == 0) "—" else {
                val a = list[index - 1]
                val result = FloatArray(1)
                Location.distanceBetween(a.lat, a.lon, place.lat, place.lon, result)
                "~" + maxOf(1, (result[0] / 75f).roundToInt()) + " мин"
            }
            val meta = TextView(this).apply {
                text = distance + "  " + time
                textSize = 10f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setTextColor(Color.rgb(125, 130, 140))
            }
            row.addView(meta, LinearLayout.LayoutParams(dp(72), dp(34)))

            routeList.addView(row, LinearLayout.LayoutParams(-1, dp(36)).apply {
                topMargin = dp(1)
                bottomMargin = dp(1)
            })
        }

        val total = list.zipWithNext().sumOf { pair ->
            val result = FloatArray(1)
            Location.distanceBetween(pair.first.lat, pair.first.lon, pair.second.lat, pair.second.lon, result)
            result[0].toDouble()
        }
        val estimated = maxOf(1, (total / 75.0).roundToInt())
        statusText.text = "≈ %.1f км · ~%d мин · пешком".format(Locale.US, total / 1000.0, estimated)
    }

    private fun showRouteChooser() {
        if (routes.isEmpty()) {
            Toast.makeText(this, "В этом городе пока нет культурных маршрутов", Toast.LENGTH_LONG).show()
            return
        }
        val labels = routes.map { "${it.name} · ${it.placeIds.size} объектов" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Культурные маршруты").setItems(labels) { _, which ->
            selectedRoute = routes[which]
            routePlaces = db.routePlaces(selectedRoute!!, places)
            activeStopIndex = 0
            drawSelectedRoute()
            renderSheet()
            moveCameraToRoute()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun moveCameraToRoute() {
        routePlaces.firstOrNull()?.let { moveCamera(it.lat, it.lon, 14.8f) }
    }

    private fun drawSelectedRoute() {
        val objects = mapView.mapWindow.map.mapObjects
        routeObjects.forEach { objects.remove(it) }
        routeObjects.clear()
        val line = selectedRoute ?: return
        if (routePlaces.size < 2) return
        routeObjects += objects.addPolyline(Polyline(routePlaces.map { Point(it.lat, it.lon) })).apply {
            setStrokeColor(Color.rgb(49, 94, 251))
            setStrokeWidth(7f)
            zIndex = 5f
        }
    }

    private fun buildWalkingRoute() {
        val selected = selectedRoute ?: run { showRouteChooser(); return }
        if (routePlaces.size < 2) return
        routeSessions.forEach { it.cancel() }
        routeSessions.clear()
        routeObjects.forEach { mapView.mapWindow.map.mapObjects.remove(it) }
        routeObjects.clear()
        routeDistanceMeters = 0.0

        val points = mutableListOf<Point>()
        lastLocation?.let { points += Point(it.latitude, it.longitude) }
        points += routePlaces.map { Point(it.lat, it.lon) }
        statusText.text = "Строю пешеходный маршрут…"

        fun leg(index: Int) {
            if (index >= points.size - 1) {
                statusText.text = "Маршрут построен · %.2f км".format(Locale.US, routeDistanceMeters / 1000.0)
                return
            }
            val router = pedestrianRouter ?: return
            val request = listOf(
                RequestPoint(points[index], RequestPointType.WAYPOINT, null, null, null),
                RequestPoint(points[index + 1], RequestPointType.WAYPOINT, null, null, null)
            )
            val listener = object : RouteSession.RouteListener {
                override fun onMasstransitRoutes(result: MutableList<com.yandex.mapkit.transport.masstransit.Route>) {
                    if (result.isEmpty()) {
                        statusText.text = "Не удалось построить участок ${index + 1}"
                        return
                    }
                    routeObjects += mapView.mapWindow.map.mapObjects.addPolyline(result[0].geometry).apply {
                        setStrokeColor(Color.rgb(49, 94, 251))
                        setStrokeWidth(8f)
                        zIndex = 6f
                    }
                    routeDistanceMeters += polylineDistance(result[0].geometry)
                    statusText.text = "Маршрут · участок ${index + 1}/${points.size - 1} · %.2f км".format(Locale.US, routeDistanceMeters / 1000.0)
                    leg(index + 1)
                }

                override fun onMasstransitRoutesError(error: Error) {
                    statusText.text = when (error) {
                        is NetworkError -> "Нет сети для построения маршрута"
                        else -> "Не удалось построить маршрут"
                    }
                }
            }
            routeSessions += router.requestRoutes(request, TimeOptions(), RouteOptions(FitnessOptions(false, false)), listener)
        }
        leg(0)
    }

    private fun polylineDistance(polyline: Polyline): Double {
        var meters = 0.0
        val p = polyline.points
        for (i in 1 until p.size) {
            val result = FloatArray(1)
            Location.distanceBetween(p[i - 1].latitude, p[i - 1].longitude, p[i].latitude, p[i].longitude, result)
            meters += result[0]
        }
        return meters
    }

    private fun showPlace(place: Place) {
        val lines = db.routesForPlace(cityId, place.id)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 4)
        }
        box.addView(TextView(this).apply {
            text = "${place.category}\n\n${place.description}\n\nАдрес: ${place.address}\n\n${if (lines.isEmpty()) "Маршруты: —" else "Маршруты: " + lines.joinToString(", ") { it.name }}"
            textSize = 16f
            setTextColor(Color.rgb(45, 48, 55))
        })
        AlertDialog.Builder(this).setTitle(place.name).setView(box)
            .setPositiveButton("Показать на карте") { _, _ -> moveCamera(place.lat, place.lon, 16.5f) }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun showSchemeMode() {
        AlertDialog.Builder(this).setTitle("Схема маршрутов")
            .setItems(routes.map { "${it.name} · ${it.placeIds.size} объектов" }.toTypedArray()) { _, which ->
                selectedRoute = routes[which]
                routePlaces = db.routePlaces(selectedRoute!!, places)
                drawSelectedRoute()
                renderSheet()
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun showMapMode() {
        selectedRoute = null
        routePlaces = emptyList()
        renderMap()
        renderSheet()
    }

    private fun showCatalogMenu() {
        AlertDialog.Builder(this).setTitle("Культурный маршрут")
            .setItems(arrayOf("Обновить каталог", "Моё положение", "О приложении")) { _, which ->
                when (which) {
                    0 -> loadCatalog()
                    1 -> {
                        requestLocation()
                        lastLocation?.let { moveCamera(it.latitude, it.longitude, 16f) }
                    }
                    2 -> AlertDialog.Builder(this).setTitle("Культурный маршрут")
                        .setMessage("Карта города, культурные объекты и тематические пешие маршруты.")
                        .setPositiveButton("Закрыть", null).show()
                }
            }.show()
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
            return
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return
        try {
            locationManager.requestLocationUpdates(provider, 5000L, 5f, locationListener)
            locationManager.getLastKnownLocation(provider)?.let {
                lastLocation = it
                showUserLocation(it.latitude, it.longitude, false)
            }
        } catch (_: SecurityException) {}
    }

    private fun showUserLocation(lat: Double, lon: Double, center: Boolean) {
        val objects = mapView.mapWindow.map.mapObjects
        if (userPlacemark == null) {
            userPlacemark = objects.addPlacemark().apply {
                setIcon(ImageProvider.fromResource(this@ModernMainActivity, R.drawable.ic_user_pin))
                setIconStyle(IconStyle().apply {
                    anchor = android.graphics.PointF(0.5f, 0.5f)
                    scale = 1.0f
                    zIndex = 50f
                })
            }
        }
        userPlacemark?.geometry = Point(lat, lon)
        if (center) moveCamera(lat, lon, 16f)
    }

    private fun moveCamera(lat: Double, lon: Double, zoom: Float) {
        mapView.mapWindow.map.move(CameraPosition(Point(lat, lon), zoom, 0f, 0f))
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroy() {
        routeSessions.forEach { it.cancel() }
        locationManager.removeUpdates(locationListener)
        super.onDestroy()
    }
}