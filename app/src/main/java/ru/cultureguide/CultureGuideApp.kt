package ru.cultureguide

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class CultureGuideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
    }
}
