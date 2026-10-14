---
name: toogeduler-team
description: Toogeduler 에이전트 팀(기획 · 디자인 · 개발 · QA)을 한 사이클 돌린다. 백로그에서 이슈 하나를 골라 구현 → 검증 → 조건을 만족하면 자동 머지하고 슬랙 #toogeduler-team에 보고한다. 예약 작업이 주 3회 호출하며, 사용자가 /toogeduler-team 으로 직접 돌릴 수도 있다. 인자로 "plan"을 주면 기획만, 이슈 번호를 주면 그 이슈를 처리한다.
---

# Toogeduler 팀 사이클

사용자(고은)는 이 사이클을 자리에 없을 때 돌린다. 질문하지 말고 끝까지 진행하되, 판단이 사람 몫이면 `needs-human`으로 넘기고 다음으로 간다. 보고는 한국어로 쉽게.

## 상수
- 작업 디렉터리: `/Users/goeun/Documents/Toogeduler-agent` (사용자 작업 폴더와 분리된 git worktree). 없으면
  `git -C /Users/goeun/Documents/Toogeduler worktree add --detach /Users/goeun/Documents/Toogeduler-agent origin/main`
- 슬랙 채널: `#toogeduler-team` = `C0C90NT3STU`
- 운영 상태 확인: `https://app.toogeduler.com/backend/actuator/health` (`"UP"`), `https://app.toogeduler.com` (200)
- 서브에이전트: `.claude/agents/`의 `pm`, `designer`, `developer`, `qa`. 각 호출에 작업 디렉터리 절대경로를 넘긴다. 서브에이전트는 포그라운드로(결과를 기다려서) 호출한다.

## 0. 준비
1. 작업 디렉터리로 가서 `git fetch origin && git switch --detach origin/main && git clean -fd`
2. 운영 상태 확인. 다운이면 → 슬랙에 🚨 경보(reply_broadcast)만 남기고, 최근 에이전트 머지가 원인으로 보이면 6단계의 되돌리기를 수행한 뒤 **사이클 종료**.
3. 슬랙 부모 메시지를 올리고 `ts`를 기억한다:
   `🛠️ **Toogeduler 팀 사이클 시작** (<날짜 요일>)` + 이번 순서 한 줄. 이후 역할 보고는 모두 이 스레드에.
   슬랙이 안 되면 슬랙 없이 진행하고 마지막 요약을 PR/이슈 코멘트로 남긴다.

## 1. 남은 일 먼저 (WIP 1)
`gh pr list --label agent --state open --json number,title,labels`
- `needs-human`이 아닌 열린 에이전트 PR이 있으면 새 이슈를 시작하지 않고 그 PR로 4단계(QA)부터 한다.
- `needs-human` PR이 3개 이상 쌓였으면 새 개발을 멈추고 슬랙에 "리뷰 대기 중인 PR이 쌓였어요"와 목록만 보고하고 종료한다 (사람이 감당 못 할 속도로 쌓지 않기).

## 2. 기획 (필요할 때만)
다음 중 하나면 `pm` 호출: 월요일 사이클이다 / `agent:ready` 이슈가 2개 미만이다 / 인자가 `plan`이다.
PM 보고를 스레드에 📋로 올린다. 인자가 `plan`이면 여기서 7단계로.

## 3. 이슈 선택 → 디자인 → 개발
1. 인자로 이슈 번호가 왔으면 그것, 아니면 `agent:ready` 중 P0 → P1 → P2, 같은 우선순위면 오래된 순으로 하나. `owner-todo`, `needs-human` 라벨은 건너뛴다.
   없으면 슬랙에 "준비된 이슈 없음" 보고 후 7단계로.
2. `gh issue edit <n> --remove-label agent:ready --add-label agent:in-progress`
3. `ui` 라벨이고 디자이너 스펙 코멘트가 아직 없으면 `designer` 호출 → 🎨 보고.
4. `developer` 호출 → 💻 보고. `BLOCKED`면 이슈에 이유를 코멘트하고 `needs-human` 라벨, `agent:in-progress` 제거 후 7단계로.

## 4. QA (최대 2라운드)
`qa`에 PR 번호를 준다 → 🔍 보고.
- `FIX`이고 1라운드였으면 → `developer`에게 같은 PR과 수정 요청 목록을 주고 다시 QA.
- 2라운드에도 `FIX`면 `HUMAN`으로 취급.
- `HUMAN`이면 PR과 이슈에 `needs-human` 라벨, 이유를 PR 코멘트로. 7단계로.

## 5. 자동 머지 (PASS일 때만)
머지 직전 오케스트레이터가 직접 다시 확인한다 (QA를 그대로 믿지 않는다):
- `gh pr checks <n>` 전부 pass
- `gh pr diff <n> --name-only`에 `qa.md`의 보호 경로가 없음
- additions+deletions ≤ 400, `needs-human` 라벨 없음
- 이번 사이클 자동 머지 0건 (사이클당 최대 1건)

모두 만족하면 `gh pr merge <n> --squash --delete-branch`. 이슈는 `Closes #`로 닫힌다. 하나라도 어긋나면 `needs-human`.

## 6. 배포 확인
머지했으면 6분 기다린 뒤(필요하면 여러 번 나눠서) 운영 상태를 확인한다. 최대 15분까지 재시도.
- 정상이면 ✅ 보고.
- 비정상이면 즉시 되돌린다: `git switch -C agent/revert-<n> origin/main && git revert --no-edit -m 1 <머지커밋>` 또는 squash면 `git revert --no-edit <커밋>` → push → PR(`revert` 라벨) → CI 통과 시 머지. 슬랙에 🚨 reply_broadcast로 알리고 원래 이슈를 `needs-human`으로 다시 연다.

## 7. 마무리 보고
스레드에 `reply_broadcast: true`로 요약:
```
✅ 사이클 완료
• 처리: #이슈 → PR #n (머지됨 | 고은님 확인 필요 | 막힘)
• 운영 상태: 정상/이상
• 고은님 확인 필요한 PR: <needs-human 목록 + 한 줄 이유>
• 고은님 할 일: <owner-todo 이슈 상위 3개>   ← 월요일 사이클에만
• 다음 사이클 예정: #이슈 제목
```
작업 디렉터리를 `git switch --detach origin/main`으로 돌려놓는다.

## 규칙
- 비밀값(.env, 토큰)을 읽거나 슬랙/PR에 쓰지 않는다.
- Railway·Vercel·Apple·Google 콘솔, 결제, DNS는 건드리지 않는다. 필요하면 `owner-todo` 이슈로.
- 팀 규칙 자체(`.claude/`, `.github/`)를 바꾸는 변경은 항상 `needs-human`.
