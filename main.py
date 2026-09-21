from pathlib import Path
from kivy.app import App
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.properties import StringProperty, NumericProperty
from kivy.uix.behaviors import ButtonBehavior
from kivy.uix.label import Label
from app.database import Database
from app.routes import build_tour, build_metro_lines

class CityRow(ButtonBehavior, Label):
    city_id = StringProperty("")

class PlaceRow(ButtonBehavior, Label):
    place_id = StringProperty("")

KV = Path(__file__).parent.joinpath("app", "ui.kv").read_text(encoding="utf-8")

class CultureGuideApp(App):
    city_name = StringProperty("")
    city_country = StringProperty("")
    object_count = NumericProperty(0)
    status_text = StringProperty("Готово")
    current_city_id = StringProperty("")

    def build(self):
        Builder.load_string(KV)
        self.db = Database(self.user_data_dir)
        self.db.seed_if_empty(Path(__file__).parent / "data" / "seed.json")
        Clock.schedule_once(self.refresh, 0)
        return self.root

    def refresh(self, *_):
        cities = self.db.list_cities()
        if cities: self.select_city(cities[0]["id"])

    def refresh_city(self, city_id):
        places = self.db.list_places(city_id)
        self.object_count = len(places)
        self.root.ids.city_list.data = [{"text": c["name"] + ", " + c["country"], "city_id": c["id"]} for c in self.db.list_cities()]
        self.root.ids.place_list.data = [{"text": p["name"], "category": p["category"], "place_id": p["id"]} for p in places]
        self.root.ids.city_map.set_places(places)
        self.root.ids.metro.set_lines(build_metro_lines(places))
        self.status_text = f"{len(places)} объектов"

    def select_city(self, city_id):
        city = self.db.get_city(city_id)
        if city:
            self.current_city_id = city_id
            self.city_name = city["name"]
            self.city_country = city["country"]
            self.refresh_city(city_id)

    def search(self, text):
        if not self.current_city_id: return
        places = self.db.search_places(text, self.current_city_id)
        self.root.ids.place_list.data = [{"text": p["name"], "category": p["category"], "place_id": p["id"]} for p in places]

    def make_tour(self):
        if not self.current_city_id: return
        tour = build_tour(self.db.list_places(self.current_city_id))
        self.root.ids.tour.text = tour["text"]
        self.root.ids.city_map.set_route(tour["route"])
        self.status_text = f'Маршрут {tour["distance_km"]:.1f} км'

    def add_demo_city(self):
        cid = self.db.add_city("Челябинск", "Россия", 55.1644, 61.4368)
        self.db.add_place(cid, "Центральная площадь", "culture", "Демонстрационный объект", 55.1644, 61.4368)
        self.db.add_place(cid, "Памятник", "monument", "Демонстрационный объект", 55.1600, 61.4400)
        self.select_city(cid)
        self.status_text = "Город добавлен"

if __name__ == "__main__":
    CultureGuideApp().run()
