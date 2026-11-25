#!/usr/bin/env bash
# 운영(또는 로컬) 스모크 확인. 로그인·저장이 CORS 설정 때문에 막히는 장애(#105)를 빨리 알아채기 위한 것이다.
# 읽기 요청과 "없는 계정으로 실패하는 로그인 시도"만 보내며 비밀값은 쓰지 않는다.
#
# 사용법:
#   bash scripts/prod-smoke.sh [옵션] <API 주소> <웹 주소> [추가로 허용된 출처...]
#   bash scripts/prod-smoke.sh https://api.toogeduler.com https://app.toogeduler.com https://toogeduler.com
#   bash scripts/prod-smoke.sh http://localhost:8080 http://localhost:3000
#
# 옵션:
#   --retry-for <초>  실패하면 30초 간격으로 전체 확인을 다시 돌린다(배포 반영 대기용). 기본 0.
#   --self-test       허용되지 않은 출처(https://evil.example)로 사전 요청 검사를 돌려 실패하는지 확인한다.
#                     검사가 실패를 잡아내면 exit 0, 통과해 버리면 exit 1.
#
# 실패하면 무엇이 깨졌는지 한 줄로 출력하고 exit 1.
set -euo pipefail

EVIL_ORIGIN="https://evil.example"
RETRY_FOR=0
SELF_TEST=0

usage() {
  sed -n '5,13p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --retry-for)
      RETRY_FOR="${2:?--retry-for 뒤에 초를 적어주세요}"
      shift 2
      ;;
    --self-test)
      SELF_TEST=1
      shift
      ;;
    -h | --help) usage ;;
    -*) usage ;;
    *) break ;;
  esac
done

[[ $# -ge 2 ]] || usage
API="${1%/}"
WEB="${2%/}"
shift 2
EXTRA_ORIGINS=("$@")

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# request <메서드> <URL> [curl 인자...] → 상태 코드를 출력하고, 헤더·본문은 $TMP 에 남긴다.
request() {
  local method="$1" url="$2"
  shift 2
  : >"$TMP/body"
  : >"$TMP/headers"
  # 연결이 안 되면 curl 이 000 을 출력하고 실패한다. 상태 코드로 판단하므로 종료 코드는 무시한다.
  curl -sS -X "$method" -o "$TMP/body" -D "$TMP/headers" -w '%{http_code}' \
    --max-time 20 "$@" "$url" 2>"$TMP/curl-error" || true
}

# header <이름> → 마지막 응답의 헤더 값(대소문자 무시, 없으면 빈 문자열)
header() {
  awk -v name="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" '
    { line = $0; sub(/\r$/, "", line) }
    tolower(substr(line, 1, index(line, ":") - 1)) == name {
      value = substr(line, index(line, ":") + 1); sub(/^[ \t]+/, "", value)
    }
    END { print value }' "$TMP/headers"
}

fail() {
  echo "FAIL $*"
  return 1
}

ok() {
  echo "ok   $*"
}

check_health() {
  local code
  code="$(request GET "$API/actuator/health")"
  [[ "$code" == "200" ]] || fail "API health: $API/actuator/health → $code (200 기대) $(head -c 200 "$TMP/curl-error")" || return 1
  grep -q '"UP"' "$TMP/body" || fail "API health: 본문에 UP 이 없음 → $(head -c 200 "$TMP/body")" || return 1
  ok "API health 200 UP"
}

check_web() {
  local code
  code="$(request GET "$WEB/")"
  [[ "$code" == "200" ]] || fail "웹: $WEB/ → $code (200 기대) $(head -c 200 "$TMP/curl-error")" || return 1
  ok "웹 $WEB/ 200"
}

# 브라우저가 로그인 POST 전에 보내는 CORS 사전 요청. 여기서 막히면 로그인·저장이 모두 막힌다.
check_preflight() {
  local origin="$1" code acao acac
  code="$(request OPTIONS "$API/api/auth/login" \
    -H "Origin: $origin" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: content-type,x-xsrf-token")"
  [[ "$code" =~ ^2 ]] || fail "CORS 사전 요청($origin): $code (2xx 기대) $(head -c 100 "$TMP/body")" || return 1
  acao="$(header Access-Control-Allow-Origin)"
  [[ "$acao" == "$origin" ]] || fail "CORS 사전 요청($origin): Access-Control-Allow-Origin='$acao'" || return 1
  acac="$(header Access-Control-Allow-Credentials)"
  [[ "$acac" == "true" ]] || fail "CORS 사전 요청($origin): Access-Control-Allow-Credentials='$acac'" || return 1
  ok "CORS 사전 요청 $origin 허용"
}

# /api/auth/login 은 SecurityConfig.CSRF_EXEMPT 에 있어 CSRF 토큰 없이도 컨트롤러까지 간다.
# 없는 계정이면 401 이 정상이다. 403 "Invalid CORS request" 면 #105 와 같은 장애다.
check_login() {
  local origin="$1" email code acao
  email="smoke-$(date +%s)-$RANDOM$RANDOM@example.invalid"
  code="$(request POST "$API/api/auth/login" \
    -H "Origin: $origin" \
    -H "Content-Type: application/json" \
    --data "{\"email\":\"$email\",\"password\":\"smoke-not-a-password\"}")"
  if [[ "$code" == "403" ]] && grep -q "Invalid CORS request" "$TMP/body"; then
    fail "로그인 POST($origin): 403 Invalid CORS request — 운영 CORS 설정(WEB_URL·CORS_EXTRA_ORIGINS) 확인"
    return 1
  fi
  [[ "$code" == "401" ]] || fail "로그인 POST($origin): $code (없는 계정이라 401 기대) $(head -c 200 "$TMP/body")" || return 1
  acao="$(header Access-Control-Allow-Origin)"
  [[ "$acao" == "$origin" ]] || fail "로그인 POST($origin): Access-Control-Allow-Origin='$acao'" || return 1
  ok "로그인 POST $origin → 401 (없는 계정, CORS 통과)"
}

run_all() {
  local failed=0 origin
  check_health || failed=1
  check_web || failed=1
  for origin in "$WEB" ${EXTRA_ORIGINS[@]+"${EXTRA_ORIGINS[@]}"}; do
    check_preflight "$origin" || failed=1
  done
  check_login "$WEB" || failed=1
  return "$failed"
}

if [[ "$SELF_TEST" == 1 ]]; then
  echo "self-test: 허용되지 않은 출처 $EVIL_ORIGIN 로 사전 요청 검사를 돌립니다 (실패해야 정상)"
  if check_preflight "$EVIL_ORIGIN"; then
    echo "self-test FAIL: $EVIL_ORIGIN 사전 요청이 통과했습니다. 검사가 장애를 잡지 못하거나 CORS 가 너무 넓습니다."
    exit 1
  fi
  echo "self-test ok: 검사가 exit 1 로 실패를 잡았습니다"
  exit 0
fi

deadline=$((SECONDS + RETRY_FOR))
while true; do
  if run_all; then
    echo "스모크 확인 통과: $API, $WEB"
    exit 0
  fi
  if ((SECONDS + 30 > deadline)); then
    echo "스모크 확인 실패: $API, $WEB"
    exit 1
  fi
  echo "30초 후 다시 확인합니다 (배포 반영 대기, 남은 $((deadline - SECONDS))초)"
  sleep 30
done
