# Flight Track

실시간 항공기 추적 + 노선/메타데이터 조회 백엔드 API 서버

## Screenshots

| 항공기 경로 추적 | 실시간 비행 추적 |
|:---:|:---:|
| ![항공기 경로 추적](docs/images/tracking-detail.png) | ![실시간 비행 추적](docs/images/tracking-route.png) |

## 기술 스택

### Language & Framework

| 기술 | 버전 | 설명 |
|------|------|------|
| **Kotlin** | 2.2.21 | JVM 기반 모던 언어, null-safety 및 코루틴 지원 |
| **Spring Boot** | 4.0.5 | 애플리케이션 프레임워크 |
| **Spring WebFlux** | - | 비동기/논블로킹 리액티브 웹 프레임워크 |
| **Kotlin Coroutines** | - | suspend 함수 기반 비동기 처리 |
| **Java** | 21 (LTS) | JVM 런타임 |

### 핵심 라이브러리

| 라이브러리 | 용도 |
|------------|------|
| **Reactor Kotlin Extensions** | Reactor ↔ Coroutines 브릿지 |
| **Jackson Kotlin Module** | JSON 직렬화/역직렬화 |
| **springdoc-openapi** 3.0.2 | Swagger UI 및 OpenAPI 3.0 문서 자동 생성 |
| **WebClient** | 논블로킹 HTTP 클라이언트 (외부 API 호출) |

### 외부 API & 데이터 소스

| 소스 | 용도 |
|-----|------|
| **[OpenSky Network](https://opensky-network.org)** | 실시간 항공기 위치 (`/states/all`) 및 비행 경로 (`/tracks/all`) |
| **[adsb.lol](https://api.adsb.lol)** | Callsign 기반 출발/도착 공항 결정 (`/api/0/routeset`) |
| **[hexdb.io](https://hexdb.io)** | ICAO24 기반 항공기 메타데이터 (등록번호, 기종, 운항사) |
| **[OpenFlights](https://openflights.org/data.html)** | `airports.dat` / `routes.dat` — 공항 좌표·이름 + 항공사 노선 DB |
| **[benct/iata-utils](https://github.com/benct/iata-utils)** | 항공사 ICAO(3-letter) → IATA(2-letter) 매핑 |

### 빌드 & 테스트

| 도구 | 설명 |
|------|------|
| **Gradle** (Kotlin DSL) | 빌드 시스템 |
| **JUnit 5** | 단위 테스트 프레임워크 |
| **Spring Boot Test** | 통합 테스트 지원 |
| **Docker** | `Dockerfile` (멀티스테이지 빌드), `Dockerfile.amd64` (amd64 배포용) |

## 아키텍처

**헥사고날 아키텍처 (Ports & Adapters)** 패턴을 적용하여 도메인 로직과 외부 의존성을 분리합니다.

```
                    ┌─────────────────────────────────────┐
                    │            Adapter (In)              │
                    │   TrackingController                 │
                    │   AirportController                  │
                    └──────────┬──────────────────────────┘
                               │ UseCase (Input Port)
                    ┌──────────▼──────────────────────────┐
                    │        Application Service           │
                    │   AircraftService                    │
                    │   AirportService                     │
                    └──────────┬──────────────────────────┘
                               │ Port (Output Port)
                    ┌──────────▼──────────────────────────┐
                    │            Adapter (Out)             │
                    │   OpenSkyAircraftAdapter             │
                    │     ├─ AircraftTrackingPort          │
                    │     ├─ AircraftTrackPort             │
                    │     └─ AircraftMetadataPort (hexdb)  │
                    │   AdsbLolRouteAdapter                │
                    │     └─ FlightRoutePort               │
                    │   RouteResolver                      │
                    │     └─ OpenFlights routes/airports   │
                    └─────────────────────────────────────┘
```

- **Domain Layer** — 순수 도메인 모델과 포트 인터페이스 (외부 의존성 없음)
- **Application Layer** — 유스케이스 구현, 포트를 통해 외부 어댑터 호출
- **Adapter Layer (In)** — REST 컨트롤러 (WebFlux)
- **Adapter Layer (Out)** — 외부 API 클라이언트, OpenFlights 데이터 로더

### 노선(출발/도착) 결정 전략

`/api/tracking/track` 응답의 `estDepartureAirport`, `estArrivalAirport`는 다음 우선순위로 결정됩니다.

1. **adsb.lol routeset** — callsign으로 노선 DB 조회 (결정적, 2시간 positive / 15분 negative 캐시)
2. **RouteResolver 휴리스틱** — adsb.lol 매칭 실패 시
   - 지상(`onGround=true`) waypoint 근접 공항으로 출발/도착 식별
   - OpenFlights `routes.dat` + 항공사 IATA로 도착지 후보 추정
   - 후보가 여럿이면 마지막 waypoint의 heading과 일치하는 방향의 공항 선택

## 디렉토리 구조

```
flight-track/
├── build.gradle.kts                                # Gradle 빌드 설정 (Kotlin DSL)
├── settings.gradle.kts
├── gradlew / gradlew.bat                           # Gradle Wrapper
├── Dockerfile                                      # 멀티스테이지 빌드 (Alpine + JDK 21)
├── Dockerfile.amd64                                # 사전 빌드된 jar 배포용
├── docs/
│   └── images/                                     # README 스크린샷
├── src/
│   ├── main/
│   │   ├── kotlin/org/shjoo/flighttrack/
│   │   │   ├── FlightTrackApplication.kt           # Spring Boot 진입점
│   │   │   │
│   │   │   ├── domain/                             # 도메인 레이어
│   │   │   │   ├── model/
│   │   │   │   │   ├── Aircraft.kt                 # AircraftSnapshot/State/Info
│   │   │   │   │   ├── Airport.kt                  # 공항 도메인 모델
│   │   │   │   │   ├── Track.kt                    # Track + TrackWaypoint
│   │   │   │   │   └── FlightRoute.kt              # callsign 기반 노선 결과
│   │   │   │   └── port/
│   │   │   │       ├── in/                         # 인바운드 포트 (UseCase)
│   │   │   │       │   ├── AircraftUseCase.kt      # GetAircraft/Track/AircraftInfo
│   │   │   │       │   └── AirportUseCase.kt
│   │   │   │       └── out/                        # 아웃바운드 포트
│   │   │   │           └── AircraftPort.kt         # Tracking/Track/Metadata/FlightRoute
│   │   │   │
│   │   │   ├── application/                        # 애플리케이션 레이어
│   │   │   │   └── service/
│   │   │   │       ├── AircraftService.kt          # 실시간 위치 + 경로 + 메타데이터
│   │   │   │       └── AirportService.kt           # IATA 코드로 공항 조회
│   │   │   │
│   │   │   ├── adapter/                            # 어댑터 레이어
│   │   │   │   ├── in/web/                         # 인바운드 어댑터 (REST)
│   │   │   │   │   ├── AirportController.kt
│   │   │   │   │   └── TrackingController.kt
│   │   │   │   └── out/                            # 아웃바운드 어댑터
│   │   │   │       ├── opensky/
│   │   │   │       │   ├── OpenSkyAircraftAdapter.kt   # OpenSky + hexdb.io 통합
│   │   │   │       │   └── dto/
│   │   │   │       │       └── OpenSkyResponse.kt      # API 응답 DTO
│   │   │   │       ├── adsblol/
│   │   │   │       │   ├── AdsbLolRouteAdapter.kt      # callsign → 노선 lookup
│   │   │   │       │   └── dto/
│   │   │   │       │       └── AdsbLolRouteResponse.kt
│   │   │   │       └── openflights/
│   │   │   │           └── RouteResolver.kt            # 휴리스틱 노선 추정
│   │   │   │
│   │   │   └── config/                             # Spring 설정
│   │   │       ├── OpenSkyConfig.kt                # OpenSky API 인증 설정
│   │   │       ├── WebConfig.kt                    # CORS 설정
│   │   │       └── OpenApiConfig.kt                # Swagger/OpenAPI 설정
│   │   │
│   │   └── resources/
│   │       └── application.yaml                    # 애플리케이션 설정
│   │
│   └── test/kotlin/org/shjoo/flighttrack/
│       └── FlightTrackApplicationTests.kt
└── README.md
```

## 캐싱 전략

| 대상 | TTL | 방식 | 설명 |
|------|-----|------|------|
| 실시간 항공기 스냅샷 (`/states/all`) | 5분 | `@Volatile` + 코루틴 `Mutex` | OpenSky rate-limit 회피, 동시 요청 단일 fetch |
| 항공기 비행 경로 (`/tracks/all`) | 5분 | In-memory `MutableMap` | icao24 단위, 100건 초과 시 LRU eviction |
| 항공기 메타데이터 (hexdb.io) | 5분 | In-memory `MutableMap` | 200건 초과 시 만료 엔트리 제거 |
| adsb.lol 노선 lookup (positive) | 2시간 | `ConcurrentHashMap` | callsign 단위 |
| adsb.lol 노선 lookup (negative) | 15분 | `ConcurrentHashMap` | 노선 DB miss 재조회 간격 |

OpenSky 호출은 최대 3회까지 backoff(3s × attempt) 재시도하며, 실패해도 직전 캐시가 있으면 그 값을 반환합니다.

## API 엔드포인트

서버 실행 후 **Swagger UI**에서 전체 API 문서를 확인할 수 있습니다:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/airports/{code}` | IATA 공항 코드 단건 조회 (이름·좌표) |
| `GET` | `/api/tracking/aircraft` | 실시간 항공기 위치 — `callsign` 검색 또는 bounding box (`lamin/lomin/lamax/lomax`) |
| `GET` | `/api/tracking/aircraft/{icao24}/info` | 항공기 등록번호/기종/운항사 등 상세 메타데이터 |
| `GET` | `/api/tracking/track?icao24=...` | 비행 경로 (waypoint) + 추정 출발/도착 공항 |

## 시작하기

### 환경 변수

```bash
# OpenSky 인증 (선택) — 미설정 시 익명, rate-limit 적용
export OPENSKY_USERNAME=your_username
export OPENSKY_PASSWORD=your_password

# OpenSky base URL (기본값: https://opensky-network.org)
export OPENSKY_BASE_URL=https://opensky-network.org
```

OpenSky 계정은 [opensky-network.org](https://opensky-network.org)에서 무료 발급 가능. adsb.lol과 hexdb.io는 인증 없이 호출됩니다.

### 실행

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew bootRun        # 개발 서버 (port 8080)
./gradlew build          # 빌드 + 테스트
./gradlew build -x test  # 테스트 제외 빌드
```

### Docker

```bash
# 멀티스테이지 빌드 (소스 → 빌드 → 런타임 이미지)
docker build -t flight-track .

# amd64 배포용 (사전 빌드된 jar 사용, 가벼움)
./gradlew bootJar
docker build -f Dockerfile.amd64 --platform linux/amd64 -t flight-track:amd64 .

docker run -p 8080:8080 \
  -e OPENSKY_USERNAME=... \
  -e OPENSKY_PASSWORD=... \
  flight-track
```

### 사용 예시

```bash
# 공항 정보 조회
curl "http://localhost:8080/api/airports/ICN"

# 콜사인으로 항공기 검색 (KE로 시작하는 모든 항공기)
curl "http://localhost:8080/api/tracking/aircraft?callsign=KE"

# 한반도 영역 항공기만 조회
curl "http://localhost:8080/api/tracking/aircraft?lamin=33&lomin=124&lamax=39&lomax=132"

# 항공기 상세 정보 (등록번호, 기종 등)
curl "http://localhost:8080/api/tracking/aircraft/abc123/info"

# 비행 경로 + 추정 출발/도착 공항
curl "http://localhost:8080/api/tracking/track?icao24=abc123"
```
