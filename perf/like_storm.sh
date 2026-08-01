#!/usr/bin/env bash
# 좋아요 동시성 결함(이슈 #51)을 앱 계층에서 재현하는 부하 생성기.
#
# http_load.py 는 익명 GET 화면만 요청하므로 이 결함이 잡히지 않는다.
# 이 스크립트는 회원 N명으로 로그인한 뒤 **같은 글 하나**에 좋아요를 몰아친다.
# 인기 글에 좋아요가 집중되는 상황을 그대로 만든 것이고, 실패는 500 으로 나가므로
# Grafana 대시보드의 5xx 에러율 패널에 그대로 잡힌다.
#
# curl 과 bash 만 쓴다(이 PC에는 Python 이 없다).
#
# 전제: perf/seed_like_storm.sql 로 부하용 회원·글을 만들어 둔다.
#
# 사용법:
#   ./perf/like_storm.sh [동시수] [초] [글ID]
#   ./perf/like_storm.sh 20 60 6
set -uo pipefail

USERS=${1:-20}
DURATION=${2:-60}
POST_ID=${3:-}
BASE=${LOAD_BASE_URL:-http://localhost:8080}
PASSWORD=${STORM_PASSWORD:-Admin1234!}

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if [ -z "$POST_ID" ]; then
    echo "글 ID를 지정하세요: ./perf/like_storm.sh $USERS $DURATION <글ID>" >&2
    echo "(perf/seed_like_storm.sql 실행 시 마지막에 출력됩니다)" >&2
    exit 1
fi

echo "좋아요 폭주: $BASE / 글 #$POST_ID / ${USERS}명 동시 / ${DURATION}초"

# meta 태그에서 CSRF 토큰을 뽑는다. app 은 _csrf / _csrf_header 로 내려준다.
extract_meta() {
    grep -o "name=\"$2\" content=\"[^\"]*\"" "$1" | head -1 | sed 's/.*content="//; s/"$//'
}

worker() {
    local idx=$1
    local jar="$WORK/cookies.$idx"
    local page="$WORK/page.$idx"
    local email="storm${idx}@cakeshop.local"

    # 1) 로그인 페이지의 CSRF 로 폼 로그인
    curl -s -c "$jar" -b "$jar" -o "$page" "$BASE/login" || { echo "setup" > "$WORK/fail.$idx"; touch "$WORK/ready.$idx"; return; }
    local login_csrf
    login_csrf=$(grep -o 'name="_csrf" value="[^"]*"' "$page" | head -1 | sed 's/.*value="//; s/"$//')
    [ -z "$login_csrf" ] && login_csrf=$(extract_meta "$page" "_csrf")

    curl -s -c "$jar" -b "$jar" -o /dev/null -L \
        --data-urlencode "email=$email" \
        --data-urlencode "password=$PASSWORD" \
        --data-urlencode "_csrf=$login_csrf" \
        "$BASE/login"

    # 2) 로그인된 세션 기준으로 좋아요 POST 에 쓸 토큰을 다시 읽는다
    curl -s -c "$jar" -b "$jar" -o "$page" "$BASE/community"
    local token header
    token=$(extract_meta "$page" "_csrf")
    header=$(extract_meta "$page" "_csrf_header")
    if [ -z "$token" ] || [ -z "$header" ]; then
        echo "csrf" > "$WORK/fail.$idx"
        touch "$WORK/ready.$idx"
        return
    fi

    # 3) 전원이 준비될 때까지 대기했다가 동시에 출발
    #    (로그인이 순차적으로 끝나면 부하가 계단식으로 들어가 순간 동시성이 안 나온다)
    touch "$WORK/ready.$idx"
    while [ ! -f "$WORK/go" ]; do sleep 0.05; done

    local deadline=$(( $(date +%s) + DURATION ))
    local body="$WORK/body.$idx" code
    while [ "$(date +%s)" -lt "$deadline" ]; do
        code=$(curl -s -b "$jar" -c "$jar" -o "$body" \
            -w '%{http_code}' \
            -X POST -H "$header: $token" \
            "$BASE/community/api/posts/$POST_ID/like")
        echo "$code" >> "$WORK/codes.$idx"
        # 실패 응답은 본문을 남겨 원인(1020/1213)을 확인할 수 있게 한다
        if [ "$code" -ge 500 ] 2>/dev/null; then
            head -c 300 "$body" >> "$WORK/failbody"
            echo "" >> "$WORK/failbody"
        fi
    done
}

for i in $(seq 0 $((USERS - 1))); do
    worker "$i" &
done

echo -n "로그인 대기"
for _ in $(seq 1 600); do
    ready=$(ls "$WORK"/ready.* 2>/dev/null | wc -l)
    [ "$ready" -ge "$USERS" ] && break
    echo -n "."
    sleep 0.2
done
echo ""

failed=$(ls "$WORK"/fail.* 2>/dev/null | wc -l)
[ "$failed" -gt 0 ] && echo "  주의: ${failed}명이 준비에 실패해 부하에서 빠집니다"

start=$(date +%s)
touch "$WORK/go"
wait
elapsed=$(( $(date +%s) - start ))

echo ""
echo "=== 결과 (${elapsed}초) ==="
cat "$WORK"/codes.* 2>/dev/null | sort | uniq -c | sort -rn | awk '{printf "  HTTP %s : %s건\n", $2, $1}'

total=$(cat "$WORK"/codes.* 2>/dev/null | wc -l)
fail5xx=$(cat "$WORK"/codes.* 2>/dev/null | awk '$1 >= 500' | wc -l)
echo ""
echo "  총 요청 : $total"
if [ "$total" -gt 0 ]; then
    echo "  5xx     : $fail5xx ($(awk "BEGIN{printf \"%.1f\", 100*$fail5xx/$total}")%)"
    echo "  처리량  : $(awk "BEGIN{printf \"%.1f\", $total/($elapsed==0?1:$elapsed)}") req/s"
fi

if [ -s "$WORK/failbody" ]; then
    echo ""
    echo "=== 실패 응답 예시 ==="
    sort "$WORK/failbody" | uniq -c | sort -rn | head -3
fi
