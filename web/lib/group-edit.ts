// 그룹 관리 서랍의 "그룹 정보" 구역에서 쓰는 순수 로직. 화면 없이 테스트할 수 있게 컴포넌트와 나눠 둡니다.

export const GROUP_NAME_MAX = 40;

/** 이름·색상 입력은 그룹장에게만 보입니다. 멤버에게는 비활성 입력도 두지 않습니다. */
export function canEditGroup(group: { ownerId: number }, meId: number) {
  return group.ownerId === meId;
}

/**
 * PATCH /api/groups/{id} 에 보낼 본문. 바뀐 값만 담고, 저장할 수 없으면(공백 이름, 바뀐 것 없음) null 입니다.
 * 저장 버튼의 disabled 도 이 값으로 정합니다.
 */
export function groupPatch(current: { name: string; color: string }, name: string, color: string) {
  const trimmed = name.trim();
  if (!trimmed || trimmed.length > GROUP_NAME_MAX) return null;
  const patch: { name?: string; color?: string } = {};
  if (trimmed !== current.name) patch.name = trimmed;
  if (color.toUpperCase() !== current.color.toUpperCase()) patch.color = color;
  return Object.keys(patch).length ? patch : null;
}
