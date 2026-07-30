#!/usr/bin/env python3
"""
cakeshop 부하 시뮬레이터 (DB 계층) — dev 브랜치 mapper SQL 재현.

10가지 사용자 행동을 가중치대로 섞어 동시 N커넥션으로 실행하고,
작업별 p50/p95와 MariaDB GLOBAL STATUS 증분을 출력한다.

사용법 (프로젝트 루트에서):
  pip install pymysql
  python perf/load_sim.py 16 15 baseline                  # 개선 전
  set REWRITE=1 && python perf/load_sim.py 16 15 improved # 개선 후 (PowerShell: $env:REWRITE="1")

접속 정보는 환경변수로 덮어쓸 수 있다 (기본값: localhost:3306 / cake_performance):
  PERF_DB_HOST, PERF_DB_PORT, PERF_DB_USER, PERF_DB_PASSWORD, PERF_DB_NAME
"""
import datetime, json, os, random, sys, threading, time
import pymysql

try:
    sys.stdout.reconfigure(encoding="utf-8")  # Windows cp949 콘솔 대비
except Exception:
    pass

REWRITE = os.environ.get("REWRITE") == "1"
DB = dict(host=os.environ.get("PERF_DB_HOST", "127.0.0.1"),
          port=int(os.environ.get("PERF_DB_PORT", "3306")),
          user=os.environ.get("PERF_DB_USER", "root"),
          password=os.environ.get("PERF_DB_PASSWORD", ""),
          database=os.environ.get("PERF_DB_NAME", "cake_performance"),
          autocommit=False, charset="utf8mb4")

MIN_POST = MAX_POST = None
HOT_POSTS, MEMBER_IDS = [], []

# 통계 대시보드용 최근 30일 구간
TO_DATE = datetime.date.today() + datetime.timedelta(days=1)
FROM_DATE = TO_DATE - datetime.timedelta(days=31)

SUMMARY_COLS = """
SELECT p.id, p.member_id, c.code AS category_code, c.name AS category_name,
       p.title, p.like_count,
       (SELECT COUNT(*) FROM comments cm
         WHERE cm.post_id = p.id AND cm.status = 'ACTIVE') AS comment_count,
       p.created_at
"""
ORIG_FROM = " FROM posts p JOIN post_categories c ON c.id = p.category_id WHERE p.status='ACTIVE' "
RW_FROM = """
  FROM ({inner}) t
  JOIN posts p ON p.id = t.id
  JOIN post_categories c ON c.id = p.category_id
"""

def setup():
    global MIN_POST, MAX_POST
    conn = pymysql.connect(**DB); cur = conn.cursor()
    cur.execute("SELECT MIN(id), MAX(id) FROM posts WHERE status='ACTIVE'")
    MIN_POST, MAX_POST = cur.fetchone()
    if MIN_POST is None:
        sys.exit("posts 테이블이 비어 있습니다 — perf/seed_load_data.sql 을 먼저 적용하세요.")
    cur.execute("SELECT id FROM posts WHERE status='ACTIVE' ORDER BY RAND(7) LIMIT 20")
    HOT_POSTS[:] = [r[0] for r in cur.fetchall()]
    cur.execute("SELECT id FROM members WHERE email LIKE 'loadtest%'")
    MEMBER_IDS[:] = [r[0] for r in cur.fetchall()]
    conn.close()

def rand_post():
    # 트래픽의 80%가 인기글 20개에 몰리는 Zipf 근사
    return random.choice(HOT_POSTS) if random.random() < 0.8 else random.randint(MIN_POST, MAX_POST)

# ---------------- 커뮤니티 ----------------
def op_home_popular(cur):
    """HomeService → findPopularPosts: 메인 페이지 인기 글 4건"""
    if REWRITE:
        cur.execute(SUMMARY_COLS + RW_FROM.format(
            inner="SELECT id FROM posts WHERE status='ACTIVE' ORDER BY like_count DESC, id DESC LIMIT 4")
            + " ORDER BY p.like_count DESC, p.id DESC")
    else:
        cur.execute(SUMMARY_COLS + ORIG_FROM + " ORDER BY p.like_count DESC, p.id DESC LIMIT 4")
    cur.fetchall()

def op_list_page1(cur):
    """CommunityService.getPostPage: 카운트 + 1페이지"""
    if REWRITE:
        cur.execute("SELECT COUNT(*) FROM posts WHERE status='ACTIVE'"); cur.fetchall()
        cur.execute(SUMMARY_COLS + RW_FROM.format(
            inner="SELECT id FROM posts WHERE status='ACTIVE' ORDER BY id DESC LIMIT 10 OFFSET 0")
            + " ORDER BY p.id DESC")
    else:
        cur.execute("SELECT COUNT(*) FROM posts p JOIN post_categories c ON c.id=p.category_id WHERE p.status='ACTIVE'"); cur.fetchall()
        cur.execute(SUMMARY_COLS + ORIG_FROM + " ORDER BY p.id DESC LIMIT 10 OFFSET 0")
    cur.fetchall()

def op_slice_keyset(cur):
    """무한스크롤 keyset (임의 커서 지점)"""
    cursor = random.randint(MIN_POST, MAX_POST)
    if REWRITE:
        cur.execute(SUMMARY_COLS + RW_FROM.format(
            inner="SELECT id FROM posts WHERE status='ACTIVE' AND id < %s ORDER BY id DESC LIMIT 11")
            + " ORDER BY p.id DESC", (cursor,))
    else:
        cur.execute(SUMMARY_COLS + ORIG_FROM + " AND p.id < %s ORDER BY p.id DESC LIMIT 11", (cursor,))
    cur.fetchall()

def op_detail(cur):
    """상세보기 트랜잭션: 상세 + 조회수 UPDATE + 댓글 + 좋아요 여부"""
    pid, mid = rand_post(), random.choice(MEMBER_IDS)
    cur.execute("""SELECT p.id,p.member_id,c.code,c.name,p.title,p.content,p.view_count,p.like_count,p.status,p.created_at
                     FROM posts p JOIN post_categories c ON c.id=p.category_id WHERE p.id=%s""", (pid,))
    if not cur.fetchone():
        return
    cur.execute("UPDATE posts SET view_count = view_count + 1 WHERE id=%s", (pid,))
    cur.execute("""SELECT id,member_id,parent_comment_id,content,status,created_at
                     FROM comments WHERE post_id=%s ORDER BY COALESCE(parent_comment_id,id), id""", (pid,))
    cur.fetchall()
    cur.execute("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id=%s AND member_id=%s)", (pid, mid))
    cur.fetchall()

def op_like_toggle(cur):
    """좋아요 토글 — 인기글 5개에 집중 (핫로우·데드락 관찰용)"""
    pid, mid = random.choice(HOT_POSTS[:5]), random.choice(MEMBER_IDS)
    cur.execute("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id=%s AND member_id=%s)", (pid, mid))
    if cur.fetchone()[0]:
        cur.execute("DELETE FROM post_likes WHERE post_id=%s AND member_id=%s", (pid, mid))
        cur.execute("UPDATE posts SET like_count = like_count - 1 WHERE id=%s", (pid,))
    else:
        cur.execute("INSERT IGNORE INTO post_likes (post_id, member_id) VALUES (%s,%s)", (pid, mid))
        cur.execute("UPDATE posts SET like_count = like_count + 1 WHERE id=%s", (pid,))
    cur.execute("SELECT like_count FROM posts WHERE id=%s", (pid,)); cur.fetchall()

def op_admin_post_search(cur):
    """관리자 게시글 제목 검색: LIKE '%키워드%' (비인덱스)"""
    kw = random.choice(["주문", "후기", "케이크", "게시글 55"])
    cur.execute("""SELECT p.id,p.member_id,c.name,p.title,p.status,p.created_at
                     FROM posts p JOIN post_categories c ON c.id=p.category_id
                    WHERE 1=1 AND p.title LIKE CONCAT('%%', %s, '%%')
                    ORDER BY p.id DESC LIMIT 10 OFFSET 0""", (kw,))
    cur.fetchall()

# ---------------- 주문 ----------------
ORDER_COLS = "id, order_number, member_id, status, final_amount, pickup_at, created_at"

def op_mypage_orders(cur):
    """마이페이지: 진행 중 주문 + 최근 완료 5건"""
    mid = random.choice(MEMBER_IDS)
    cur.execute(f"SELECT {ORDER_COLS} FROM orders WHERE member_id=%s AND status IN ('PAID','UNDER_REVIEW','IN_PRODUCTION','READY_FOR_PICKUP') ORDER BY id DESC", (mid,))
    cur.fetchall()
    cur.execute(f"SELECT {ORDER_COLS} FROM orders WHERE member_id=%s AND status='PICKED_UP' ORDER BY id DESC LIMIT 5", (mid,))
    cur.fetchall()

def op_admin_order_page(cur):
    """관리자 주문 목록: 카운트 + 1페이지"""
    cur.execute("SELECT COUNT(*) FROM orders"); cur.fetchall()
    cur.execute(f"SELECT {ORDER_COLS} FROM orders ORDER BY id DESC LIMIT 10 OFFSET 0"); cur.fetchall()

def op_order_number_search(cur):
    """관리자 주문번호 부분검색: LIKE '%…%'"""
    kw = f"{random.randint(100, 999)}"
    cur.execute(f"SELECT {ORDER_COLS} FROM orders WHERE order_number LIKE CONCAT('%%', %s, '%%') ORDER BY id DESC LIMIT 10", (kw,))
    cur.fetchall()

def op_stats_dashboard(cur):
    """통계: aggregateOrderStats + aggregateOrderTrend(최근 30일)"""
    f, t = str(FROM_DATE), str(TO_DATE)
    cur.execute("""SELECT (SELECT COUNT(*) FROM orders WHERE created_at >= %s AND created_at < %s),
                          (SELECT COUNT(*) FROM orders WHERE picked_up_at >= %s AND picked_up_at < %s),
                          (SELECT COUNT(*) FROM orders WHERE canceled_at >= %s AND canceled_at < %s),
                          (SELECT COALESCE(SUM(final_amount),0) FROM orders WHERE created_at >= %s AND created_at < %s)""",
                (f, t) * 4)
    cur.fetchall()
    cur.execute("""SELECT DATE_FORMAT(created_at,'%%m-%%d'), COUNT(*), COALESCE(SUM(final_amount),0)
                     FROM orders WHERE created_at >= %s AND created_at < %s
                    GROUP BY DATE_FORMAT(created_at,'%%m-%%d') ORDER BY MIN(created_at)""", (f, t))
    cur.fetchall()

OPS = {
    "home_popular":      (op_home_popular, 20),
    "list_page1":        (op_list_page1, 15),
    "slice_keyset":      (op_slice_keyset, 15),
    "detail":            (op_detail, 25),
    "like_toggle":       (op_like_toggle, 8),
    "admin_post_search": (op_admin_post_search, 3),
    "mypage_orders":     (op_mypage_orders, 8),
    "admin_order_page":  (op_admin_order_page, 2),
    "order_no_search":   (op_order_number_search, 2),
    "stats_dashboard":   (op_stats_dashboard, 2),
}
WEIGHTED = [n for n, (_, w) in OPS.items() for _ in range(w)]
results = {n: [] for n in OPS}
errors = {"count": 0, "samples": []}
lock = threading.Lock()

def worker(stop_at):
    conn = pymysql.connect(**DB); cur = conn.cursor()
    local = {n: [] for n in OPS}; errs = []
    while time.time() < stop_at:
        name = random.choice(WEIGHTED)
        t0 = time.perf_counter()
        try:
            OPS[name][0](cur); conn.commit()
            local[name].append((time.perf_counter() - t0) * 1000)
        except Exception as e:
            conn.rollback(); errs.append(f"{name}: {e}")
    conn.close()
    with lock:
        for k in local: results[k].extend(local[k])
        errors["count"] += len(errs)
        errors["samples"] = (errors["samples"] + errs)[:3]

def gstat(cur, keys):
    cur.execute("SHOW GLOBAL STATUS"); d = dict(cur.fetchall())
    return {k: int(d[k]) for k in keys if k in d}

def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0

if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 16
    dur = int(sys.argv[2]) if len(sys.argv) > 2 else 15
    label = sys.argv[3] if len(sys.argv) > 3 else ("improved" if REWRITE else "baseline")
    setup()
    mon = pymysql.connect(**DB); mcur = mon.cursor()
    KEYS = ["Queries", "Slow_queries", "Handler_read_rnd_next", "Innodb_row_lock_waits",
            "Innodb_row_lock_time", "Created_tmp_tables", "Select_scan"]
    before = gstat(mcur, KEYS)
    t0 = time.time(); stop = t0 + dur
    ts = [threading.Thread(target=worker, args=(stop,)) for _ in range(n)]
    [t.start() for t in ts]; [t.join() for t in ts]
    el = time.time() - t0
    after = gstat(mcur, KEYS); mon.close()
    total = sum(len(v) for v in results.values())
    out = {
        "label": label, "rewrite": REWRITE, "threads": n, "duration_s": round(el, 1),
        "total_ops": total, "throughput_ops_s": round(total / el, 1),
        "errors": errors["count"], "error_samples": errors["samples"],
        "ops": {k: {"count": len(v),
                    "p50_ms": round(pct(v, .5), 2),
                    "p95_ms": round(pct(v, .95), 2),
                    "max_ms": round(max(v), 2) if v else 0} for k, v in results.items()},
        "db_status_delta": {k: after[k] - before[k] for k in before},
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))
    fname = f"perf/result_{label}.json"
    try:
        with open(fname, "w", encoding="utf-8") as fp:
            json.dump(out, fp, ensure_ascii=False, indent=1)
        print(f"\n저장됨: {fname}")
    except OSError:
        pass
