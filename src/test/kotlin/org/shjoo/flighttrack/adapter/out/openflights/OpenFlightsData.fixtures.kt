package org.shjoo.flighttrack.adapter.out.openflights

internal const val AIRLINES_CSV = """iata_code^icao_code^name^alias
KE^KAL^Korean Air^
OZ^AAR^Asiana Airlines^
0C^^Catovair^
^XYZ^No Iata Air^
"""

internal const val AIRPORTS_DAT = """1,"Incheon International Airport","Seoul","South Korea","ICN","RKSI",37.469101,126.450996,23,9,"U","Asia/Seoul","airport","OurAirports"
2,"Gimpo International Airport","Seoul","South Korea","GMP","RKSS",37.558300,126.790596,18,9,"U","Asia/Seoul","airport","OurAirports"
3,"Gimhae International Airport","Busan","South Korea","PUS","RKPK",35.179501,128.938004,12,9,"U","Asia/Seoul","airport","OurAirports"
4,"Narita International Airport","Tokyo","Japan","NRT","RJAA",35.764702,140.386002,141,9,"U","Asia/Tokyo","airport","OurAirports"
5,"Comma, Field","Nowhere","Nowhere","CMA","ZZZZ",10.5,20.5,0,0,"U","UTC","airport","OurAirports"
6,"No Coords Airport","Nowhere","Nowhere","NOC","ZZZY",\N,\N,0,0,"U","UTC","airport","OurAirports"
7,"No Iata Airport","Nowhere","Nowhere",\N,"ZZZX",1.0,2.0,0,0,"U","UTC","airport","OurAirports"
"""

internal const val ROUTES_DAT = """KE,1234,ICN,1,NRT,4,,0,333
KE,1234,PUS,3,ICN,1,,0,333
KE,1234,PUS,3,NRT,4,,0,333
KE,1234,PUS,3,\N,\N,,0,333
KE,1234,,1,NRT,4,,0,333
OZ,5678,GMP,2,PUS,3,,0,333
"""

internal fun testData(): OpenFlightsData = OpenFlightsData.parse(AIRLINES_CSV, ROUTES_DAT, AIRPORTS_DAT)
