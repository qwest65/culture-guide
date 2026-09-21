from kivy.graphics import Color,Line,Ellipse,Rectangle
from kivy.uix.widget import Widget

class OfflineCityMap(Widget):
    def __init__(self,**kwargs):
        super().__init__(**kwargs);self.places=[];self.route=[]
        self.bind(pos=self.redraw,size=self.redraw)

    def set_places(self,places):
        self.places=places;self.redraw()

    def set_route(self,route):
        self.route=route;self.redraw()

    def project(self,lat,lon):
        if not self.places:return self.center
        la=[p["lat"] for p in self.places];lo=[p["lon"] for p in self.places]
        minla,maxla,minlo,maxlo=min(la),max(la),min(lo),max(lo)
        return (self.x+35+(lon-minlo)/((maxlo-minlo) or 1)*(self.width-70),
                self.y+35+(lat-minla)/((maxla-minla) or 1)*(self.height-70))

    def redraw(self,*_):
        self.canvas.clear()
        with self.canvas:
            Color(.95,.96,.98,1);Rectangle(pos=self.pos,size=self.size)
            Color(.84,.86,.89,1)
            for x in range(int(self.x),int(self.right),45):Line(points=[x,self.y,x,self.top],width=.5)
            for y in range(int(self.y),int(self.top),45):Line(points=[self.x,y,self.right,y],width=.5)
            if len(self.route)>1:
                Color(.10,.25,.75,1);pts=[]
                for p in self.route:
                    x,y=self.project(p["lat"],p["lon"]);pts += [x,y]
                Line(points=pts,width=3)
            for p in self.places:
                x,y=self.project(p["lat"],p["lon"])
                Color(.85,.12,.25,1);Ellipse(pos=(x-7,y-7),size=(14,14))
