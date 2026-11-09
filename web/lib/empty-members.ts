// 함께 되는 시간: 기간 안에 일정이 하나도 없는 멤버를 알려주는 안내 문구. 아직 캘린더를 안 채운 사람을 그룹장이 알 수 있게 한다.
// 순수 함수라 `npm test` 로 브라우저 없이 검증합니다.
import type { Group } from "./types.ts";

/** 안내가 필요 없으면(0명이거나 그룹에 없는 id 뿐) null. 본인은 본인만 해당할 때만 따로 알린다. */
export function emptyMembersNotice(
  memberIds: number[],
  members: Pick<Group["members"][number], "id" | "name">[],
  meId?: number,
): string | null {
  const others = members.filter((m) => m.id !== meId && memberIds.includes(m.id)).map((m) => m.name);
  if (others.length === 0) {
    if (meId !== undefined && memberIds.includes(meId))
      return "이 기간에 내 일정이 하나도 없어요. 일정을 넣으면 더 정확해져요.";
    return null;
  }
  const who = others.length <= 2 ? others.map((n) => `${n}님`).join(", ") : `${others[0]}님 외 ${others.length - 1}명`;
  return `${who}은 이 기간에 일정이 하나도 없어요. 아직 캘린더를 안 채웠을 수 있어요.`;
}
