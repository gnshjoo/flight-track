# Vercel 배포 정의 (Container Images + VCR)

flight-track(Spring Boot, 8080 포트)을 Vercel에 컨테이너로 배포하는 절차 정의.

Vercel의 컨테이너 배포는 두 부분으로 나뉜다:

- **Vercel Container Registry (VCR)**: `vcr.vercel.com`에 이미지를 저장하는 Docker 호환 레지스트리. 이미지 참조 형식은 `vcr.vercel.com/<team-slug>/<project-name>/<repository>:<tag>`.
- **Vercel Functions Container Images**: 저장된 OCI 이미지를 Vercel Function으로 실행. 트래픽 기반 자동 스케일, Active CPU 과금.

## 권장 방식: `Dockerfile.vercel` 자동 배포

레포 루트에 `Dockerfile.vercel`을 두면 Vercel이 자동 감지해서, 배포(빌드) 단계에서 이미지를 빌드해 VCR에 푸시하고 모든 트래픽을 컨테이너로 라우팅한다. VCR에 직접 푸시할 필요가 없다.

### 1. 프로젝트 연결

```bash
vercel link
```

### 2. 환경 변수 설정 (Vercel 프로젝트 설정 → Environment Variables)

| 변수 | 값 | 비고 |
| --- | --- | --- |
| `PORT` | `8080` | Vercel 기본 라우팅 포트는 80. 앱이 8080을 쓰므로 반드시 지정 |

CLI로도 가능: `vercel env add PORT production`. 데이터 소스(adsb.lol/adsb.fi/hexdb.io)는 인증이 없어 다른 변수는 필요 없다.

> OpenSky 는 클라우드(AWS 등) IP 대역의 접속을 네트워크 레벨에서 차단해 Vercel 에서 사용할 수 없어 adsb.lol 로 전환했다 (TCP connect timeout, 로컬에서는 정상 — 2026-09 확인).

### 3. 배포

```bash
vercel deploy          # preview
vercel deploy --prod   # production
```

Git 연동을 걸어두면 push만으로 동일하게 배포된다. 로컬 테스트는 `vercel dev`(Docker 데몬 필요).

## 대안: VCR에 수동 푸시

이미지를 직접 빌드해서 올리고 싶을 때 (CI 분리, 이미지 재사용 등):

```bash
vercel link
vercel vcr login docker            # 12시간짜리 OIDC 토큰으로 docker login
vercel vcr build docker . --push   # 기본 linux/amd64, zstd 압축(Buildx 필요)
```

- 기본 태그: `vcr.vercel.com/<team-slug>/flight-track/flight-track:latest`
- 태그 지정: `vercel vcr build docker . flight-track:1.0.0 --push`
- 기본 플랫폼이 `linux/amd64`라서 별도의 `Dockerfile.amd64` / `--platform` 지정이 필요 없다.
- Vercel CLI 없이 쓰려면: Account Token으로 `docker login vcr.vercel.com --username <TEAM_ID> --password-stdin` 후 일반 `docker push`. GitHub Actions에서는 Vercel 공식 OIDC 로그인 액션 사용.

## 기존 `Dockerfile` 대비 `Dockerfile.vercel`에서 바뀐 점

- **`ARG`/`ENV`로 자격증명을 굽지 않는다.** 데이터 소스 인증이 없어 환경 변수 주입 자체가 불필요하다.
- **`java` 를 절대 경로(`/opt/java/openjdk/bin/java`)로 실행한다.** Vercel 런타임은 이미지의 `PATH` ENV 를 적용하지 않아 exec-form ENTRYPOINT 의 `java` 를 찾지 못한다.

## 운영 시 유의사항

- **Scale-to-zero**: 프로덕션에서 5분(프리뷰 30초) 동안 트래픽이 없으면 인스턴스가 내려간다. JVM 특성상 콜드 스타트가 수 초 걸리고, **인메모리 ADS-B 캐시도 함께 사라진다.**
- **종료 처리**: scale-down 시 `SIGTERM` 후 30초 유예. Spring Boot 기본 graceful shutdown으로 충분.
- **과금**: Active CPU 모델 — I/O 대기 중에는 CPU 과금이 없다 (외부 ADS-B API 대기 시간에 유리).
- **미지원**: Secure Compute, Static IP는 컨테이너 이미지에서 아직 미지원.
- **로그/메트릭**: stdout/stderr가 Vercel Runtime Logs로 수집되고 Observability 메트릭도 일반 Function과 동일하게 제공.
