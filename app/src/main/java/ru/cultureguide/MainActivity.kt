package ru.cultureguide

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import ru.cultureguide.audio.AudioGuide
import ru.cultureguide.data.CatalogDatabase
import ru.cultureguide.location.LocationTracker
import ru.cultureguide.map.MapController
import ru.cultureguide.map.WalkingRouteBuilder
import ru.cultureguide.model.Place
import ru.cultureguide.ui.GuideScreen
import ru.cultureguide.ui.GuideTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: GuideController
    private lateinit var mapController: MapController
    private lateinit var tracker: LocationTracker
    private var mapView: MapView? = null
    private var permissionAsked = false

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                startTracking()
            } else {
                toast("Без доступа к геолокации ведение по маршруту недоступно")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapKitFactory.initialize(this)

        mapController = MapController(this) { place -> controller.selectedPlace = place }
        val router = TransportFactory.getInstance().createPedestrianRouter()
        controller = GuideController(
            context = this,
            db = CatalogDatabase(applicationContext),
            map = mapController,
            routeBuilder = WalkingRouteBuilder(router),
            approachBuilder = WalkingRouteBuilder(router),
            audio = AudioGuide(this),
            notify = ::toast
        )
        tracker = LocationTracker(this, controller::onLocation)
        controller.start()

        setContent {
            GuideTheme {
                GuideScreen(
                    controller = controller,
                    onMapCreated = ::attachMap,
                    onMapReleased = ::detachMap,
                    onInsetsChanged = mapController::setInsets,
                    onZoom = mapController::zoomBy,
                    onMyLocation = ::showMyLocation,
                    onStartGuidance = ::startGuidance,
                    onOpenExternal = ::openInYandexMaps,
                    onOpenSource = ::openUrl
                )
            }
        }
    }

    private fun attachMap(view: MapView) {
        mapView = view
        mapController.attach(view)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
    }

    private fun detachMap(view: MapView) {
        mapController.detach(view)
        if (mapView === view) mapView = null
    }

    private fun startGuidance() {
        ensureTracking()
        controller.startGuidance()
    }

    private fun showMyLocation() {
        ensureTracking()
        if (!controller.showMyLocation()) {
            toast(if (tracker.isProviderEnabled()) "Определяем местоположение…" else "Включите геолокацию в настройках")
        }
    }

    private fun ensureTracking() {
        if (tracker.hasPermission()) startTracking() else requestLocationPermission()
    }

    private fun requestLocationPermission() {
        permissionAsked = true
        permissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun startTracking() {
        if (!tracker.start() && !tracker.isProviderEnabled()) toast("Включите геолокацию в настройках")
    }

    /** Пешеходный маршрут в Яндекс Картах; без приложения — в браузере. */
    private fun openInYandexMaps(place: Place) {
        val from = controller.location?.let { "${it.lat},${it.lon}" }.orEmpty()
        val query = "rtext=$from~${place.lat},${place.lon}&rtt=pd"
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?$query"))
            .setPackage("ru.yandex.yandexmaps")
        try {
            startActivity(app)
        } catch (_: ActivityNotFoundException) {
            openUrl("https://yandex.ru/maps/?$query")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast("Нет приложения для открытия ссылки")
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
        // Спрашиваем разрешение один раз за запуск; повторно — только по кнопке «Где я» / «Начать».
        if (tracker.hasPermission()) tracker.start() else if (!permissionAsked) requestLocationPermission()
    }

    override fun onStop() {
        tracker.stop()
        mapView?.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }
}
