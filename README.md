# Flight Track

항공편 검색 및 실시간 항공기 추적 백엔드 API

## 기술 스택

- **Kotlin** + **Spring Boot 4.0.5**
- **Spring WebFlux** (Reactive / Non-blocking)
- **Kotlin Coroutines**
- **Amadeus API** — 항공편 검색
- **OpenSky Network** — 실시간 항공기 위치
- **springdoc-openapi** — Swagger UI

## 시작하기

### 환경 변수

```bash
export AMADEUS_CLIENT_ID=your_client_id
export AMADEUS_CLIENT_SECRET=your_client_secret

# 운영 환경 (기본값: test API)
export AMADEUS_BASE_URL=https://api.amadeus.com
```

Amadeus API 키는 [developers.amadeus.com](https://developers.amadeus.com) 에서 발급받을 수 있습니다.

### 실행

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew bootRun        # 개발 서버 (port 8080)
./gradlew build          # 빌드 + 테스트
./gradlew build -x test  # 테스트 제외 빌드
```

## API

서버 실행 후 Swagger UI에서 전체 API 문서를 확인할 수 있습니다.

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### 엔드포인트 요약

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/flights` | 저가 항공편 목적지 목록 |
| GET | `/api/flights/detail` | 특정 노선 상세 정보 |
| GET | `/api/airports` | 공항 검색 |
| GET | `/api/airports/nearest` | 좌표 기준 가장 가까운 공항 |
| GET | `/api/tracking/aircraft` | 실시간 항공기 위치 |
| GET | `/api/tracking/track` | 항공기 비행 경로 |

### 예시

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

## 아키텍처

```
controller/     REST API 엔드포인트
service/        비즈니스 로직 (Amadeus 인증, 항공편 검색, 캐시)
dto/            요청/응답 데이터 모델
config/         Spring 설정 (CORS, WebClient, OpenAPI)
fallback/       API 장애 시 정적 대체 데이터 (서울 출발 30개 노선)
```

### 캐싱 전략

| 대상 | TTL | 방식 |
|------|-----|------|
| 항공편 검색 결과 | 10분 | ConcurrentHashMap |
| 실시간 항공기 위치 | 30초 | In-memory + Mutex |
| 항공기 비행 경로 | 30초 | In-memory |
| Amadeus 인증 토큰 | 만료 60초 전 갱신 | AtomicReference |
