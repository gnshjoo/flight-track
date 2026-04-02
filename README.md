# Flight Track

항공편 검색 및 실시간 항공기 추적 백엔드 API 서버

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

### 외부 API

| API | 용도 |
|-----|------|
| **[Amadeus API](https://developers.amadeus.com)** | 항공편 검색, 공항 정보, 노선 상세 조회 |
| **[OpenSky Network](https://opensky-network.org)** | 실시간 항공기 위치 및 비행 경로 추적 |

### 빌드 & 테스트

| 도구 | 설명 |
|------|------|
| **Gradle** (Kotlin DSL) | 빌드 시스템 |
| **JUnit 5** | 단위 테스트 프레임워크 |
| **Spring Boot Test** | 통합 테스트 지원 |

## 아키텍처

**헥사고날 아키텍처 (Ports & Adapters)** 패턴을 적용하여 도메인 로직과 외부 의존성을 분리합니다.

```
                    ┌─────────────────────────────────┐
                    │          Adapter (In)            │
                    │   FlightController               │
                    │   AirportController              │
                    │   TrackingController              │
                    └──────────┬──────────────────────┘
                               │ UseCase (Input Port)
                    ┌──────────▼──────────────────────┐
                    │       Application Service        │
                    │   FlightService                  │
                    │   AirportService                 │
                    │   AircraftService                │
                    └──────────┬──────────────────────┘
                               │ Port (Output Port)
                    ┌──────────▼──────────────────────┐
                    │         Adapter (Out)            │
                    │   AmadeusFlightAdapter            │
                    │   AmadeusAirportAdapter           │
                    │   OpenSkyAircraftAdapter          │
                    │   InMemoryFlightCacheAdapter      │
                    │   FallbackFlightAdapter           │
                    └─────────────────────────────────┘
```

- **Domain Layer** — 순수 도메인 모델과 포트 인터페이스 정의 (외부 의존성 없음)
- **Application Layer** — 유스케이스 구현, 포트를 통해 외부 어댑터 호출
- **Adapter Layer (In)** — REST API 컨트롤러 (WebFlux)
- **Adapter Layer (Out)** — 외부 API 클라이언트, 캐시, 폴백 데이터

## 디렉토리 구조

```
flight-track/
├── build.gradle.kts                          # Gradle 빌드 설정 (Kotlin DSL)
├── settings.gradle.kts
├── gradlew / gradlew.bat                     # Gradle Wrapper
├── docs/
│   └── images/                               # README 스크린샷
├── src/
│   ├── main/
│   │   ├── kotlin/org/shjoo/flighttrack/
│   │   │   ├── FlightTrackApplication.kt     # Spring Boot 진입점
│   │   │   │
│   │   │   ├── domain/                       # 도메인 레이어
│   │   │   │   ├── model/
│   │   │   │   │   ├── Flight.kt             # 항공편 도메인 모델
│   │   │   │   │   ├── Airport.kt            # 공항 도메인 모델
│   │   │   │   │   ├── Aircraft.kt           # 항공기 상태 스냅샷
│   │   │   │   │   └── Track.kt              # 비행 경로 웨이포인트
│   │   │   │   └── port/
│   │   │   │       ├── in/                   # 인바운드 포트 (UseCase)
│   │   │   │       │   ├── FlightUseCase.kt
│   │   │   │       │   ├── AirportUseCase.kt
│   │   │   │       │   └── AircraftUseCase.kt
│   │   │   │       └── out/                  # 아웃바운드 포트
│   │   │   │           ├── FlightPort.kt
│   │   │   │           ├── AirportPort.kt
│   │   │   │           └── AircraftPort.kt
│   │   │   │
│   │   │   ├── application/                  # 애플리케이션 레이어
│   │   │   │   └── service/
│   │   │   │       ├── FlightService.kt      # 항공편 검색 + 캐시 + 폴백
│   │   │   │       ├── AirportService.kt     # 공항 검색 + 좌표 기반 조회
│   │   │   │       └── AircraftService.kt    # 실시간 항공기 + 경로 추적
│   │   │   │
│   │   │   ├── adapter/                      # 어댑터 레이어
│   │   │   │   ├── in/web/                   # 인바운드 어댑터 (REST)
│   │   │   │   │   ├── FlightController.kt
│   │   │   │   │   ├── AirportController.kt
│   │   │   │   │   └── TrackingController.kt
│   │   │   │   └── out/                      # 아웃바운드 어댑터
│   │   │   │       ├── amadeus/
│   │   │   │       │   ├── AmadeusAuthAdapter.kt      # OAuth2 토큰 관리
│   │   │   │       │   ├── AmadeusFlightAdapter.kt    # 항공편 검색 API
│   │   │   │       │   ├── AmadeusAirportAdapter.kt   # 공항 검색 API
│   │   │   │       │   └── dto/
│   │   │   │       │       └── AmadeusApiResponse.kt  # API 응답 DTO
│   │   │   │       ├── opensky/
│   │   │   │       │   ├── OpenSkyAircraftAdapter.kt  # 실시간 항공기 위치
│   │   │   │       │   └── dto/
│   │   │   │       │       └── OpenSkyResponse.kt     # API 응답 DTO
│   │   │   │       ├── cache/
│   │   │   │       │   └── InMemoryFlightCacheAdapter.kt  # 인메모리 캐시
│   │   │   │       └── fallback/
│   │   │   │           └── FallbackFlightAdapter.kt   # 정적 폴백 데이터
│   │   │   │
│   │   │   └── config/                       # Spring 설정
│   │   │       ├── AmadeusConfig.kt          # Amadeus API 설정
│   │   │       ├── WebClientConfig.kt        # WebClient 타임아웃 설정
│   │   │       ├── WebConfig.kt              # CORS 설정
│   │   │       └── OpenApiConfig.kt          # Swagger/OpenAPI 설정
│   │   │
│   │   └── resources/
│   │       └── application.yaml              # 애플리케이션 설정
│   │
│   └── test/kotlin/org/shjoo/flighttrack/
│       ├── FlightTrackApplicationTests.kt
│       └── service/
│           └── CacheServiceTest.kt           # 캐시 서비스 단위 테스트
└── README.md
```

## 캐싱 전략

| 대상 | TTL | 방식 | 설명 |
|------|-----|------|------|
| 항공편 검색 결과 | 10분 | `ConcurrentHashMap` | 만료된 엔트리 스케줄링 자동 제거 |
| 실시간 항공기 위치 | 30초 | In-memory + `Mutex` | 코루틴 Mutex로 동시성 제어 |
| 항공기 비행 경로 | 30초 | In-memory | 경로 데이터 임시 캐싱 |
| Amadeus 인증 토큰 | 만료 60초 전 갱신 | `AtomicReference` | 토큰 만료 전 사전 갱신 |

## API 엔드포인트

서버 실행 후 **Swagger UI**에서 전체 API 문서를 확인할 수 있습니다:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/flights` | 저가 항공편 목적지 검색 |
| `GET` | `/api/flights/detail` | 특정 노선 상세 정보 |
| `GET` | `/api/airports` | 공항 이름/코드 검색 |
| `GET` | `/api/airports/nearest` | 좌표 기준 최근접 공항 |
| `GET` | `/api/tracking/aircraft` | 실시간 항공기 위치 전체 조회 |
| `GET` | `/api/tracking/track` | 특정 항공기 비행 경로 |

## 시작하기

### 환경 변수

```bash
export AMADEUS_CLIENT_ID=your_client_id
export AMADEUS_CLIENT_SECRET=your_client_secret

# 운영 환경 (기본값: test API)
export AMADEUS_BASE_URL=https://api.amadeus.com
```

Amadeus API 키는 [developers.amadeus.com](https://developers.amadeus.com)에서 발급받을 수 있습니다.

### 실행

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew bootRun        # 개발 서버 (port 8080)
./gradlew build          # 빌드 + 테스트
./gradlew build -x test  # 테스트 제외 빌드
```

### 사용 예시

```bash
# 서울에서 출발하는 저가 항공편
curl "http://localhost:8080/api/flights?from=ICN&dateFrom=2026-04-01&dateTo=2026-04-30"

# ICN → NRT 노선 상세
curl "http://localhost:8080/api/flights/detail?from=ICN&to=NRT&departureDate=2026-04-01"

# 공항 검색
curl "http://localhost:8080/api/airports?query=seoul"

# 실시간 항공기 전체 조회
curl "http://localhost:8080/api/tracking/aircraft"

# 특정 항공기 경로
curl "http://localhost:8080/api/tracking/track?icao24=abc123"
```
