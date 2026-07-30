#!/usr/bin/env python3
"""
cakeshop HTTP 부하 생성기 — 앱 계층(8080)을 통과하는 부하.

load_sim.py 는 DB에 직접 붙어 SQL만 잰다. 이 스크립트는 실제 화면을 요청하므로
HikariCP 커넥션 대기, 트랜잭션 프록시, Thymeleaf 렌더까지 포함된 값이 나오고,
그 결과가 actuator → Prometheus → Grafana 대시보드에 그대로 잡힌다.

표준 라이브러리만 쓴다(추가 설치 불필요).

사용법:
  python perf/http_load.py [동시수] [초] [라벨]
  python perf/http_load.py 12 60 after-v4

환경변수:
  LOAD_BASE_URL  기본 http://localhost:8080
"""
import json, os, random, sys, threading, time, urllib.error, urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE = os.environ.get("LOAD_BASE_URL", "http://localhost:8080").rstrip("/")

# (이름, 경로생성함수, 가중치) — 익명으로 200이 나오는 화면만 넣는다.
SCENARIOS = [
    ("home",          lambda: "/",                                              20),
    ("community",     lambda: "/community",                                     25),
    ("community_page",lambda: f"/community?page={random.randint(0, 40)}",       15),
    ("scroll",        lambda: f"/community/scroll?cursor={random.randint(1, 99000)}", 15),
    ("api_posts",     lambda: f"/community/api/posts?cursor={random.randint(1, 99000)}", 10),
    ("products",      lambda: "/products",                                      15),
]
WEIGHTED = [s for s in SCENARIOS for _ in range(s[2])]

results = {name: [] for name, _, _ in SCENARIOS}
status_counts = {}
errors = {"count": 0, "samples": []}
lock = threading.Lock()


def worker(stop_at):
    local = {name: [] for name, _, _ in SCENARIOS}
    codes, errs = {}, []
    opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "cakeshop-load/1.0"),
                         ("Connection", "keep-alive")]
    while time.time() < stop_at:
        name, path_fn, _ = random.choice(WEIGHTED)
        url = BASE + path_fn()
        t0 = time.perf_counter()
        try:
            with opener.open(url, timeout=60) as resp:
                resp.read()
                code = resp.status
            local[name].append((time.perf_counter() - t0) * 1000)
            codes[code] = codes.get(code, 0) + 1
        except urllib.error.HTTPError as e:
            codes[e.code] = codes.get(e.code, 0) + 1
            errs.append(f"{name}: HTTP {e.code}")
        except Exception as e:
            errs.append(f"{name}: {type(e).__name__} {e}")
    with lock:
        for k in local:
            results[k].extend(local[k])
        for c, n in codes.items():
            status_counts[c] = status_counts.get(c, 0) + n
        errors["count"] += len(errs)
        errors["samples"] = (errors["samples"] + errs)[:3]


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0


if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 12
    dur = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    label = sys.argv[3] if len(sys.argv) > 3 else "http"

    print(f"HTTP 부하: {BASE} / {n}동시 / {dur}초 / label={label}", flush=True)
    t0 = time.time()
    stop = t0 + dur
    threads = [threading.Thread(target=worker, args=(stop,)) for _ in range(n)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    elapsed = time.time() - t0

    total = sum(len(v) for v in results.values())
    out = {
        "label": label,
        "threads": n,
        "duration_s": round(elapsed, 1),
        "total_requests": total,
        "throughput_rps": round(total / elapsed, 1),
        "status_counts": {str(k): v for k, v in sorted(status_counts.items())},
        "errors": errors["count"],
        "error_samples": errors["samples"],
        "scenarios": {
            k: {"count": len(v),
                "p50_ms": round(pct(v, .5), 1),
                "p95_ms": round(pct(v, .95), 1),
                "max_ms": round(max(v), 1) if v else 0}
            for k, v in results.items()
        },
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))
    try:
        with open(f"perf/result_http_{label}.json", "w", encoding="utf-8") as fp:
            json.dump(out, fp, ensure_ascii=False, indent=1)
        print(f"\n저장됨: perf/result_http_{label}.json")
    except OSError:
        pass
