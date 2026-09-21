[app]
title = Культурный маршрут
package.name = cultureguide
package.domain = ru.cultureguide
source.dir = .
source.include_exts = py,kv,json,png,jpg,jpeg,atlas,db
version = 0.1.0
orientation = portrait
fullscreen = 0
android.api = 35
android.minapi = 23
android.archs = arm64-v8a, armeabi-v7a
android.allow_backup = True
android.accept_sdk_license = True
requirements = python3==3.12.8,kivy==2.3.0
android.permissions = INTERNET,ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION
p4a.branch = master

[buildozer]
log_level = 2
warn_on_root = 1
