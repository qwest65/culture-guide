from pathlib import Path
from kivy.app import App
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.properties import StringProperty, NumericProperty\nfrom kivy.uix.behaviors import ButtonBehavior\nfrom kivy.uix.label import Label
from app.database import Database
from app.routes import build_tour, build_metro_lines

class CityRow(ButtonBehavior, Label):\n    city_id = StringProperty("")\n\nclass PlaceRow(ButtonBehavior, Label):\n    place_id = StringProperty("")\n\nKV = Path(__file__).parent.joinpath("app", "ui.kv").read_text(encoding="utf-8")

class CultureGuideApp(App):
    city_name = StringProperty("")
    city_country = StringProperty("")
    object_count = NumericProperty(0)
    status_text = StringProperty("Готово")

    def build(self):
        Builder.load_string(KV)
        self.db = Database(self.user_data_dir)
        self.db.seed_if_empty(Path(__file__).parent / "data" / "seed.json")
        Clock.schedule_once(self.refresh, 0)
        return self.root

    def refresh(self, *_):
        cities = self.db.list_cities()
        if cities:
            self.city_name = cities[0]["name"]
            self.city_country = cities[0]["country"]
            self.refresh_city(cities[0]["id"])

    def refresh_city(self, city_id):
        places = self.db.list_places(city_id)
        self.object_count = len(places)
        self.root.ids.city_list.data = [{"text":c["name"] + ", " + c["country"], "city_id":c["id"]} for c in self.db.list_cities()]\n        self.root.ids.place_list.data = [{"text":p["name"],"category":p["category"],"place_id":p["id"]} for p in places]
        self.root.ids.city_map.set_places(places)
        self.root.ids.metro.set_lines(build_metro_lines(places))
        self.status_text = f"{len(places)} объектов"

    def select_city(self, city_id):
        city = self.db.get_city(city_id)
        if city:
            self.city_name, self.city_country = city["name"], city["country"]
            self.refresh_city(city_id)

    def search(self, text):
        cities = self.db.list_cities()
        if not cities:
            return
        places = self.db.search_places(text, cities[0]["id"])
        self.root.ids.place_list.data = [{"text":p["name"],"category":p["category"],"place_id":p["id"]} for p in places]

    def make_tour(self):
        cities = self.db.list_cities()
        if not cities:
            return
        tour = build_tour(self.db.list_places(cities[0]["id"]))
        self.root.ids.tour.text = tour["text"]
        self.root.ids.city_map.set_route(tour["route"])
        self.status_text = f'Маршрут {tour["distance_km"]:.1f} км'

    def add_demo_city(self):
        cid = self.db.add_city("Челябинск","Россия",55.1644,61.4368)
        self.db.add_place(cid,"Центральная площадь","culture","Демонстрационный объект",55.1644,61.4368)
        self.db.add_place(cid,"Памятник","monument","Демонстрационный объект",55.1600,61.4400)
        self.status_text = "Город добавлен"

if __name__ == "__main__":
    CultureGuideApp().run()
