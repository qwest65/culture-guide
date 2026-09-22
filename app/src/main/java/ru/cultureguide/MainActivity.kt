package ru.cultureguide

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.*
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import kotlin.math.*

data class Place(val name:String,val category:String,val description:String,val address:String,val lat:Double,val lon:Double)

class Db(ctx:Context):SQLiteOpenHelper(ctx,"culture.db",null,2){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE cities(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,country TEXT,lat REAL,lon REAL)")
  db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER,name TEXT,category TEXT,description TEXT,address TEXT,lat REAL,lon REAL)")
  val city=db.compileStatement("INSERT INTO cities(name,country,lat,lon) VALUES('Троицк','Россия',54.0820,61.5596)").executeInsert()
  val ps=listOf(
   arrayOf("Троицкий краеведческий музей","Музеи","Городской краеведческий музей.","ул. Ленина, 70",54.082118,61.559624),
   arrayOf("Торговые ряды","Архитектура","Исторические торговые ряды Троицка.","ул. Климова, 5А",54.083491,61.559328),
   arrayOf("Пассаж братьев Яушевых","Архитектура","Памятник архитектуры федерального значения, построен в 1908–1910 годах.","ул. Малышева, 32",54.086718,61.560654),
   arrayOf("Свято-Троицкий собор","Религия","Кафедральный собор, одно из старейших сохранившихся каменных зданий города.","ул. Красногвардейская, 1А",54.077845,61.557944),
   arrayOf("Памятный камень Троицкой крепости","История","Памятный знак на месте основания Троицкой крепости в 1743 году.","ул. Красногвардейская, 1",54.077485,61.556362),
   arrayOf("Памятник Ф. Н. Плевако","Памятники","Памятник знаменитому адвокату и уроженцу Троицка.","ул. Октябрьская",54.081818,61.562668),
   arrayOf("Центральная площадь","История","Исторический центр города и место бывшей Михайловской площади.","Центральная площадь",54.082600,61.559233),
   arrayOf("Мечеть Гатауллы муллы","Религия","Историческая мечеть, построенная в 1894–1895 годах.","ул. Ленина, 117",54.087017,61.571150),
   arrayOf("Водонапорная башня","Архитектура","Памятник архитектуры 1927 года, городская высотная доминанта.","ул. Гагарина, 22А",54.080950,61.533317),
   arrayOf("Памятник И. И. Неплюеву","Памятники","Памятник основателю Троицка, установлен в 2001 году.","ул. Гагарина",54.0820,61.5620)
  )
  val s=db.compileStatement("INSERT INTO places(city_id,name,category,description,address,lat,lon) VALUES(?,?,?,?,?,?,?)")
  for(p in ps){s.bindLong(1,city);s.bindString(2,p[0] as String);s.bindString(3,p[1] as String);s.bindString(4,p[2] as String);s.bindString(5,p[3] as String);s.bindDouble(6,p[4] as Double);s.bindDouble(7,p[5] as Double);s.executeInsert()}
 }
 override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){db.execSQL("DROP TABLE IF EXISTS places");db.execSQL("DROP TABLE IF EXISTS cities");onCreate(db)}
 fun places(city:Long):List<Place>{val r=readableDatabase.rawQuery("SELECT name,category,description,address,lat,lon FROM places WHERE city_id=? ORDER BY id",arrayOf(city.toString()));val a=mutableListOf<Place>();r.use{while(it.moveToNext())a+=Place(it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getDouble(4),it.getDouble(5))};return a}
}

fun dist(a:Place,b:Place):Double{val r=6371.0088;val p1=Math.toRadians(a.lat);val p2=Math.toRadians(b.lat);val dp=Math.toRadians(b.lat-a.lat);val dl=Math.toRadians(b.lon-a.lon);val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 2*r*asin(sqrt(h))}

class MetroView(c:Context):View(c){
 var places:List<Place> = emptyList();var route:List<Place> = emptyList();val p=Paint(1)
 override fun onDraw(c:Canvas){
  c.drawColor(Color.rgb(248,249,251));p.color=Color.rgb(225,227,232);p.strokeWidth=1f
  for(x in 0..width step 40)c.drawLine(x.toFloat(),0f,x.toFloat(),height.toFloat(),p)
  for(y in 0..height step 40)c.drawLine(0f,y.toFloat(),width.toFloat(),y.toFloat(),p)
  if(places.isEmpty())return
  val la0=places.minOf{it.lat};val la1=places.maxOf{it.lat};val lo0=places.minOf{it.lon};val lo1=places.maxOf{it.lon}
  fun xy(z:Place)=PointF((35+(z.lon-lo0)/((lo1-lo0).coerceAtLeast(1e-9))*(width-70)).toFloat(),(height-35-(z.lat-la0)/((la1-la0).coerceAtLeast(1e-9))*(height-70)).toFloat())
  val o=if(route.isEmpty())places else route
  p.color=Color.rgb(49,94,251);p.style=Paint.Style.STROKE;p.strokeWidth=9f;p.strokeCap=Paint.Cap.ROUND
  for(i in 1 until o.size){val a=xy(o[i-1]);val b=xy(o[i]);c.drawLine(a.x,a.y,b.x,b.y,p)}
  p.style=Paint.Style.FILL
  for((i,z) in places.withIndex()){val q=xy(z);p.color=Color.WHITE;c.drawCircle(q.x,q.y,11f,p);p.color=Color.rgb(49,94,251);c.drawCircle(q.x,q.y,6f,p);p.color=Color.DKGRAY;p.textSize=21f;c.drawText("${i+1}",q.x+13,q.y+7,p)}
 }
}

class MainActivity:Activity(){
 lateinit var db:Db;lateinit var map:MetroView;lateinit var web:WebView;lateinit var list:LinearLayout;lateinit var status:TextView;lateinit var locationManager:LocationManager;var cityId=1L;var currentPlaces:List<Place> = emptyList();var lastLocation:Location?=null
 val locationListener=object:LocationListener{override fun onLocationChanged(location:Location){lastLocation=location;web.evaluateJavascript("showUser(\${location.latitude},\${location.longitude},true);",null);status.text="GPS: %.5f, %.5f".format(java.util.Locale.US,location.latitude,location.longitude)}}
 override fun onCreate(b:Bundle?){super.onCreate(b);db=Db(this);locationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager;ui();refresh();requestLocation()}
 fun ui(){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(14,10,14,8)
  val title=TextView(this);title.text="ТроицкGuide";title.textSize=26f;root.addView(title)
  val sub=TextView(this);sub.text="Троицк, Челябинская область";sub.textSize=16f;root.addView(sub)
  val tabs=LinearLayout(this)
  val mapBtn=Button(this);mapBtn.text="Карта";val schemeBtn=Button(this);schemeBtn.text="Схема";val routeBtn=Button(this);routeBtn.text="Маршрут";val gpsBtn=Button(this);gpsBtn.text="GPS"
  tabs.addView(mapBtn,LinearLayout.LayoutParams(0,52,1f));tabs.addView(schemeBtn,LinearLayout.LayoutParams(0,52,1f));tabs.addView(routeBtn,LinearLayout.LayoutParams(0,52,1f));tabs.addView(gpsBtn,LinearLayout.LayoutParams(0,52,1f));root.addView(tabs)
  web=WebView(this);web.webViewClient=WebViewClient();web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true
  root.addView(web,LinearLayout.LayoutParams(-1,0,1.35f))
  map=MetroView(this);map.visibility=View.GONE;root.addView(map,LinearLayout.LayoutParams(-1,0,1.35f))
  status=TextView(this);status.textSize=15f;status.setPadding(4,5,4,5);root.addView(status)
  val sv=ScrollView(this);list=LinearLayout(this);list.orientation=LinearLayout.VERTICAL;sv.addView(list);root.addView(sv,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
  mapBtn.setOnClickListener{web.visibility=View.VISIBLE;map.visibility=View.GONE;status.text="Карта OpenStreetMap · выбери объект в списке"}
  schemeBtn.setOnClickListener{web.visibility=View.GONE;map.visibility=View.VISIBLE;status.text="Схематическая карта маршрутов"}
  routeBtn.setOnClickListener{buildRoute()}
  gpsBtn.setOnClickListener{requestLocation();lastLocation?.let{web.visibility=View.VISIBLE;map.visibility=View.GONE;web.evaluateJavascript("centerMap(\${it.latitude},\${it.longitude});",null)}}
 }
 fun requestLocation(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),1001);return};try{val provider=when{locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)->LocationManager.GPS_PROVIDER;locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)->LocationManager.NETWORK_PROVIDER;else->null};if(provider==null){status.text="GPS недоступен: включите геолокацию";return};locationManager.requestLocationUpdates(provider,5000L,5f,locationListener);locationManager.getLastKnownLocation(provider)?.let{locationListener.onLocationChanged(it)}}catch(_:SecurityException){status.text="Нет разрешения на геолокацию"}}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==1001&&results.any{it==PackageManager.PERMISSION_GRANTED})requestLocation()else if(requestCode==1001)status.text="Геолокация отключена пользователем"}
 override fun onDestroy(){if(::locationManager.isInitialized)locationManager.removeUpdates(locationListener);super.onDestroy()}
 fun buildRoute(){
  if(currentPlaces.isEmpty())return
  val r=mutableListOf(currentPlaces.first());val left=currentPlaces.drop(1).toMutableList()
  while(left.isNotEmpty()){val n=left.minBy{dist(r.last(),it)};r+=n;left.remove(n)}
  map.route=r;map.invalidate()
  web.evaluateJavascript("showRoute([${r.joinToString(","){ "[${it.lat},${it.lon}]" }}]);",null)
  status.text="Маршрут: %.1f км · ${r.size} объектов".format(java.util.Locale.US,r.zipWithNext().sumOf{dist(it.first,it.second)})
 }
 fun refresh(){
  currentPlaces=db.places(cityId);map.places=currentPlaces;map.route=emptyList();map.invalidate();web.loadDataWithBaseURL("https://www.openstreetmap.org/","${mapHtml()}","text/html","UTF-8",null)
  list.removeAllViews()
  currentPlaces.forEachIndexed{i,z->
   val t=TextView(this);t.text="${i+1}. ${z.name}\\n${z.category}\\n${z.address}";t.textSize=16f;t.setPadding(8,12,8,12)
   t.setOnClickListener{showPlace(z)};list.addView(t)
  }
  status.text="${currentPlaces.size} объектов · карта OpenStreetMap"
 }
 fun showPlace(p:Place){
  val box=TextView(this);box.text="${p.category}\\n\\n${p.description}\\n\\nАдрес: ${p.address}\\n\\nКоординаты: %.6f, %.6f".format(java.util.Locale.US,p.lat,p.lon);box.textSize=16f;box.setPadding(28,8,28,8)
  AlertDialog.Builder(this).setTitle(p.name).setView(box).setPositiveButton("Открыть карту"){_,_->openMap(p)}.setNegativeButton("Закрыть",null).show()
 }
 fun openMap(p:Place){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(p.name)})")))}
 fun mapHtml():String{
  val markers=currentPlaces.joinToString(","){ "{name:'${it.name.replace("'","\\\\'")}',lat:${it.lat},lon:${it.lon},cat:'${it.category}',address:'${it.address.replace("'","\\\\'")}'}" }
  return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><style>html,body,#map{height:100%;margin:0}.leaflet-popup-content{font-size:15px}</style></head><body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
var data=[$markers];var map=L.map('map').setView([54.0820,61.5596],14);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);var layer=L.layerGroup().addTo(map);
function draw(){layer.clearLayers();data.forEach(function(x,i){L.marker([x.lat,x.lon]).addTo(layer).bindPopup('<b>'+(i+1)+'. '+x.name+'</b><br>'+x.cat+'<br>'+x.address);});}
var userLayer=L.layerGroup().addTo(map);function showUser(lat,lon,center){userLayer.clearLayers();L.circleMarker([lat,lon],{radius:9,weight:3,fillOpacity:0.8}).addTo(userLayer).bindPopup('Моё положение');if(center)map.setView([lat,lon],15);}function centerMap(lat,lon){map.setView([lat,lon],16);}function showRoute(points){draw();if(points.length>1){L.polyline(points,{weight:6}).addTo(map);map.fitBounds(points,{padding:[20,20]});}}draw();
</script></body></html>"""
 }
}
