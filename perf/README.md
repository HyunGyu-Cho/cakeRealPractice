# perf/ — 부하 측정 재현 패키지

10만 건 데이터에서 커뮤니티 병목을 재현하고, V4 인덱스 + mapper 리라이트의 개선 효과를
**개선 전/후 동일 부하**로 측정하는 패키지.

> ⚠️ 이 폴더는 개인 측정용이다. 팀 공유가 필요하면 개인 `feature/*` 브랜치에만 커밋하고,
> `result_*.json` 같은 측정 산출물은 커밋하지 않는 것을 권장.
> `seed_load_data.sql` 은 **부하용 DB 전용** — 개발 DB(`cakeshop`)에 절대 실행하지 말 것.

## 현재 세팅 상태 (2026-07-30 기준, 이 PC)

| 항목 | 값 |
|---|---|
| DB | MariaDB 12.2.2, `127.0.0.1:3307` |
| 부하용 스키마 | `cakeshop_performance` (Flyway V1~V4 + `R__dev_seed` 적용 완료) |
| 부하 데이터 | posts 10만 · comments 30만 · post_likes 10만 · orders 10만 · 부하회원 200 |
| Python | `C:\Users\user\anaconda3\python.exe` (3.12.3) + `pymysql` 2.2.8 |
| mysql CLI | `C:\Program Files\MariaDB 12.2\bin\mysql.exe` (PATH에는 없음) |

**세팅은 이미 끝나 있다.** 아래 "실행" 항목만 돌리면 된다. 다른 PC에서 처음 세팅한다면
"처음부터 세팅하기"를 따른다.

> `.env` 의 `LOCAL_DB_DATABASE` 가 현재 **`cakeshop_performance`** 로 되어 있다.
> 이 상태로 `bootRun` 하면 앱이 개발 DB(`cakeshop`)가 아니라 부하용 DB를 본다.
> 평소 개발로 돌아갈 때는 `.env` 를 `cakeshop` 으로 되돌릴 것.

## 실행

모든 실행은 `perf/run_load.ps1` 을 통한다. 이 스크립트가 `.env` 의 `LOCAL_DB_*` 를 읽어
`load_sim.py` 에 넘기므로, `.env` 만 맞으면 포트·DB명·계정을 따로 손댈 필요가 없다.
(`load_sim.py` 자체의 기본값은 `3306` / `cake_performance` 라 이 PC와 다르다 — 직접 호출하지 말 것.)

```powershell
# 병목 증거 — EXPLAIN + 인덱스 현황
.\perf\run_load.ps1 -Explain

# 개선 전 측정 (V4 인덱스가 없는 상태에서)
.\perf\run_load.ps1 -Label baseline

# 개선 후 측정 (mapper 리라이트와 동일한 SQL로)
.\perf\run_load.ps1 -Label improved -Rewrite

# 부하 데이터 재적재 (수 초 소요, 기존 부하 데이터는 지워짐)
.\perf\run_load.ps1 -Seed
```

- 기본 부하: 16커넥션 × 15초. `-Threads` / `-Duration` 으로 변경.
- 결과는 콘솔 JSON + `perf/result_<label>.json` 에 저장.
- `-Seed` 는 DB 이름에 `performance` 가 들어갈 때만 동작한다(개발 DB 오적재 방지 안전장치).

### 개선 전/후를 제대로 비교하려면

`-Rewrite` 는 SQL만 바꿀 뿐 **인덱스 상태는 DB에 달려 있다.** 4가지 조합이 가능하다:

| DB 인덱스 | `-Rewrite` | 의미 |
|---|---|---|
| V4 없음 | 없음 | 진짜 baseline (dev 브랜치 그대로) |
| V4 없음 | 있음 | 리라이트만 — 인덱스 없이는 효과 제한적 |
| V4 있음 | 없음 | 인덱스만 — **이것만으로는 부족하다** (아래 참고) |
| V4 있음 | 있음 | 최종 개선 상태 |

V4를 적용/해제하려면 `feature/community-performance` 브랜치를 체크아웃한 뒤 앱을 1회 부팅해
Flyway가 V4를 적용하게 한다. 되돌리려면 인덱스를 직접 DROP하고
`flyway_schema_history` 에서 V4 행을 지운다.

## 실측 결과 (이 PC, 16커넥션 × 15초)

리포트의 샌드박스 수치(2vCPU)와 절대값은 다르지만 **배율과 병목 패턴은 그대로 재현된다.**

| 작업 | ① baseline p50 | ② V4+리라이트 p50 | 완료 수 ①→② |
|---|---|---|---|
| 메인 인기 글 | 2,049.8ms | **0.92ms** | 48 → 866 |
| 커뮤니티 목록 1페이지 | 2,066.6ms | **14.6ms** | 51 → 619 |
| 무한스크롤 (keyset) | 776.8ms | **1.1ms** | 49 → 682 |
| 상세보기 | 3.78ms | 3.72ms | 68 → 1,059 |
| 좋아요 토글 | 3.43ms | 3.21ms | 25 → 358 |
| 관리자 게시글 검색 (LIKE) | 53.2ms (5건만 완료) | 2,073ms | 5 → 139 |
| 마이페이지 주문 | 2.67ms | 2.95ms | 16 → 360 |
| 통계 대시보드 | 91.9ms | 81.5ms | 2 → 94 |

**전체 처리량 17.1 → 275.3 ops/s (16.1배)**

읽는 법:
- baseline에서 완료 수가 적은 작업은 "빠른 것"이 아니라 **커뮤니티 쿼리가 서버를 포화시켜
  순서가 오지 않은 것**이다. 관리자 검색이 ①에서 53ms로 보이는 건 5건만 운 좋게 끝난 결과다.
  ②의 2,073ms가 이 작업의 실제 비용이다 — 개선 후 최대 병목이 여기로 옮겨갔다.
- 상세·좋아요·마이페이지는 p50이 거의 그대로다. 원래 인덱스가 있던 경로라 정상이다.
  달라진 건 같은 시간에 **처리한 건수**(15→16배)다.

### 병목의 증거 — EXPLAIN

`-Explain` 이 출력하는 원본 쿼리(findPopularPosts)의 실행계획:

```
① V4 적용 전
  c  ALL  key=NULL                Extra: Using temporary; Using filesort
  p  ref  key=fk_posts_category   rows≈15,151 (×4 카테고리)
  cm DEPENDENT SUBQUERY (fk_comments_post)

② V4 적용 후 — 같은 원본 쿼리
  p  range key=idx_posts_status_id  rows≈90,910  Extra: Using temporary; Using filesort
  c  ALL                            Extra: Using join buffer (flat, BNL join)
  cm DEPENDENT SUBQUERY  key=idx_comments_post_status  Extra: Using index
```

②가 핵심이다. **인덱스를 넣어도 원본 쿼리는 여전히 9만 행을 임시테이블에 모아 정렬한다.**
옵티마이저가 4행짜리 `post_categories` 조인에 BNL을 택하는 순간 정렬이 인덱스를 못 타기 때문.
그래서 인덱스만으로는 부족하고 mapper 리라이트(late row lookup)가 함께 필요하다 —
파생테이블에서 id만 LIMIT 건수까지 좁힌 뒤 확정된 소수 행에만 JOIN·댓글수 서브쿼리를 실행한다.

### 동시성 오류

②에서 18건의 오류가 잡혔다 (`like_toggle`, `detail`):

```
(1020, "Record has changed since last read in table 'post_likes'")
```

리포트가 기록한 데드락(1213)과는 **다른 에러 코드**다. 이 PC(MariaDB 12.2)에서는 1213이
재현되지 않았고 대신 1020이 나왔다. 다만 원인 구조는 같다 — `SELECT EXISTS` 로 확인한 뒤
`INSERT`/`DELETE` 하는 check-then-act 경쟁이며, 인기글 5개에 토글이 집중될 때 발생한다.
①에서 0건이었던 건 안전해서가 아니라 좋아요 토글이 25건밖에 못 돌았기 때문이다(②는 358건).

해결은 동일하다: `SELECT EXISTS` 를 없애고 `INSERT IGNORE` 의 영향 행 수로 분기한다.

## 앱 계층 모니터링

```powershell
.\gradlew.bat bootRun "--args=--spring.profiles.active=local,monitor"
```

- 서비스: `http://localhost:8080`
- 지표: `http://localhost:9090/actuator/prometheus` (관리 포트 분리 — 8080에서는 지표가 안 나온다)

확인된 동작:
- 8080 `/actuator/prometheus` → 로그인 페이지 (노출 없음) ✅
- 9090 `/actuator/prometheus` → 지표 평문, Spring Security 헤더 포함 (`MonitoringSecurityConfig` 정상 동작) ✅
- HikariCP(`hikaricp_connections_*`)·JVM 힙·`http_server_requests_seconds` 모두 수집됨 ✅

### 백분위 설정 주의 (실측으로 확인한 함정)

`application-monitor.yml` 참고. 두 가지가 조용히 실패할 수 있다:

1. **Map 키에 점(.)이 있으면 대괄호로 감싸야 한다.** `http.server.requests:` 로 쓰면
   Spring이 중첩 키로 해석해 설정이 무시된다. → `"[http.server.requests]":`
2. **`percentiles-histogram` 과 `percentiles` 는 배타적이다.**
   - `percentiles-histogram: true` → `*_bucket` 만 나오고 `quantile` 라인은 **사라진다**.
     Prometheus 서버에서 `histogram_quantile()` 로 계산하며 인스턴스 간 합산이 가능하다.
   - `percentiles` (현재 설정) → `*{quantile="0.5"}` 이 스크레이프에 바로 찍힌다.
     Prometheus 없이도 읽히지만 합산은 안 된다.

   아직 Prometheus·Grafana 서버가 없으므로 "바로 읽히는" 쪽을 기본값으로 두었다.

실제 출력 예 (`/community` 25회 요청 후):

```
http_server_requests_seconds{...,uri="/community",quantile="0.5"}  0.019922944
http_server_requests_seconds{...,uri="/community",quantile="0.95"} 0.0262144
http_server_requests_seconds{...,uri="/community",quantile="0.99"} 0.317718528
```

### 보안 — 배포 전 반드시 처리할 것

현재 `monitor` 프로필은 9090을 **인증 없이 모든 인터페이스(`::`)에 연다.**
노출 목록에 `heapdump` 가 들어 있어, 접근 가능한 사람은 누구나 JVM 메모리 전체 스냅샷
(세션 토큰·DB 비밀번호·회원 개인정보 포함)을 내려받을 수 있다. 로컬 실험에서는 괜찮지만
배포 전에는 최소한 다음을 적용해야 한다:

- `management.endpoints.web.exposure.include` 에서 `heapdump` 제거
- `management.server.address: 127.0.0.1` 로 바인딩 제한 (또는 방화벽/보안그룹으로 내부망 한정)

## 대시보드 (Prometheus + Grafana)

`/actuator/prometheus` 는 사람이 아니라 **Prometheus 가 긁어가라고 만든 기계용 텍스트**다.
브라우저로 열면 지표 200줄이 그대로 보이는 게 정상이고, 사람이 보는 건 Grafana 쪽이다.

```powershell
# 1) 앱 (지표를 내보내는 쪽)
.\gradlew.bat bootRun "--args=--spring.profiles.active=local,monitor"

# 2) 수집·시각화 스택
.\perf\monitoring\start_monitoring.ps1

# 상태 확인 / 종료
.\perf\monitoring\start_monitoring.ps1 -Status
.\perf\monitoring\start_monitoring.ps1 -Stop
```

- 대시보드: <http://localhost:3000/d/cakeshop-overview>
- 수집 상태: <http://localhost:9091/targets>

### 포트 배치

| 포트 | 용도 |
|---|---|
| 8080 | 앱 서비스 |
| 9090 | 앱 actuator (지표 노출) |
| 9091 | Prometheus — **기본값 9090이 actuator와 겹쳐 옮겼다** |
| 3000 | Grafana |

### 구성 파일

| 파일 | 역할 |
|---|---|
| `monitoring/prometheus.yml` | 수집 대상·주기(5초). 부하가 15초짜리라 기본 15초로는 표본이 부족하다 |
| `monitoring/provisioning/datasources/` | Grafana 데이터소스 자동 등록 (UI에서 손으로 추가할 필요 없음) |
| `monitoring/provisioning/dashboards/` | 대시보드 폴더 자동 등록. 기동 시 절대경로가 치환된다 |
| `monitoring/dashboards/cakeshop.json` | 대시보드 정의. 수정은 이 파일을 고친다(UI 수정은 저장되지 않음) |
| `monitoring/start_monitoring.ps1` | 기동/종료/상태 |

바이너리는 저장소가 아니라 `%USERPROFILE%\tools\monitoring` 에 둔다(무설치 zip).
지우려면 그 폴더만 삭제하면 된다.

### 대시보드 패널

| 패널 | 답하는 질문 |
|---|---|
| 처리량 · p95 응답시간 · 5xx 에러율 · DB 커넥션 대기 | 지금 건강한가 (헤드라인 4개) |
| 화면별 p95 응답시간 | **어느 화면이 느린가** — 커뮤니티 목록·메인이 솟으면 그게 병목 |
| 화면별 처리량 | 느린 화면이 자주 불리는가 (위 패널과 같이 본다) |
| DB 커넥션풀 | 대기가 0보다 크면 풀(기본 10)이 병목 |
| JVM 힙 | 톱니가 정상. 회수 후 바닥이 계속 오르면 누수 의심 |

### 백분위 설정과의 관계

대시보드를 붙이면서 `application-monitor.yml` 을 `percentiles-histogram: true` 로 되돌렸다.
히스토그램(`*_bucket`)은 Prometheus 가 `histogram_quantile()` 로 임의 구간을 다시 계산할 수 있고
인스턴스 간 합산도 되지만, 클라이언트 백분위(`quantile="0.5"`)는 이미 계산된 값이라 둘 다 안 된다.
대신 히스토그램을 켜면 `/actuator/prometheus` 를 눈으로 볼 때 quantile 줄이 사라진다 —
Grafana 없이 엔드포인트만 볼 일이 있으면 설정을 뒤집는다.

> 클라이언트 백분위를 쓸 때 주의: 그 값은 **최근 약 2분 롤링 윈도**라 트래픽이 끊기면 0으로 떨어진다.
> `_count`/`_sum` 은 누적이라 줄지 않는다. "조용할 때 0" 은 고장이 아니다.

### 보안

Grafana 는 로컬 실험용이라 **익명 접속 + Admin 권한**으로 띄운다(`GF_AUTH_ANONYMOUS_ENABLED`).
Prometheus 도 인증이 없다. 둘 다 외부에 열지 말 것.

## 처음부터 세팅하기 (다른 PC)

1. **부하용 DB 생성**
   ```sql
   CREATE DATABASE cakeshop_performance;
   ```
2. **`.env` 의 `LOCAL_DB_DATABASE` 를 `cakeshop_performance` 로 변경**
3. **스키마 적용** — 앱을 1회 부팅하면 Flyway가 알아서 한다
   ```powershell
   .\gradlew.bat bootRun "--args=--spring.profiles.active=local"
   ```
   `R__dev_seed` 가 함께 적용되어 `post_categories` 4행 · `products` 6행이 생긴다
   (부하 시드 SQL의 전제 조건). 부팅 로그를 확인하고 `Ctrl+C`.
4. **Python 준비** — Anaconda가 있으면 그대로 쓴다
   ```powershell
   & "$env:USERPROFILE\anaconda3\python.exe" -m pip install pymysql
   ```
   Microsoft Store 스텁 `python.exe` 는 실행되지 않는다. `run_load.ps1` 이 이를 걸러낸다.
5. **부하 데이터 적재**
   ```powershell
   .\perf\run_load.ps1 -Seed
   ```
   마지막에 `posts=100000 comments=300000 orders=100000` 이 나오면 성공.
   (`seq_1_to_N` 은 MariaDB SEQUENCE 엔진 기능이라 MySQL에서는 동작하지 않는다.)
6. **측정** — 위 "실행" 참고.

## 파일

| 파일 | 역할 |
|---|---|
| `run_load.ps1` | 실행 진입점. `.env` 를 읽어 접속정보를 주입하고 시드·EXPLAIN·측정을 수행 |
| `load_sim.py` | 부하 시뮬레이터. 10가지 행동을 가중치대로 섞어 동시 실행하고 p50/p95를 집계 |
| `seed_load_data.sql` | 대량 시드 (재실행 가능) |
| `after__V4_and_mapper_rewrite.patch` | V4 인덱스 + CommunityMapper 리라이트 |
| `result_*.json` | 측정 산출물 (커밋하지 않음) |

### 알려진 한계

- **DB 계층만 측정한다.** `load_sim.py` 는 pymysql로 MariaDB에 직결하므로 HikariCP 풀 대기,
  트랜잭션 프록시, MyBatis 매핑, Thymeleaf 렌더가 빠져 있다. "16배"는 DB 계층 수치이고
  사용자 체감 개선폭은 이보다 작다. 앱 계층 수치는 위 actuator로 따로 본다.
- **`-Rewrite` 의 SQL은 mapper XML을 손으로 옮긴 복제본이다.** 원본과 갈라질 수 있다.
  실제로 10개 op 중 **카테고리 필터를 쓰는 것이 하나도 없어**,
  `idx_posts_category_status_id` 와 mapper의 `EXISTS` 리라이트는 아직 측정된 적이 없다.
- **리라이트 결과가 원본과 동일한지 검증하는 테스트가 없다.** V4가 머지돼도 누군가
  mapper를 되돌리면 조용히 회귀한다.
