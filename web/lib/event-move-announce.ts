// 일정 수정 모달의 "바뀐 시간을 그룹 멤버에게 알리기" 줄을 보일지 정하는 순수 로직. 화면 없이 테스트할 수 있게 컴포넌트와 나눠 둡니다.

type Moment = { id?: number; startAt?: string; endAt?: string };
type Draft = Moment & { visibilities?: string[]; groupIds?: number[] };

/**
 * 기존 일정이고, 그룹 공개(그룹을 하나 이상 고름)이며, 시작이나 종료가 처음 연 값과 다른 순간일 때만 true 입니다.
 * 문자열이 아니라 시각으로 비교하므로 오프셋 표기만 다른 같은 순간은 바뀐 것으로 보지 않습니다.
 */
export function shouldOfferMoveAnnounce(original: Moment, draft: Draft) {
  if (!original.id || !draft.id) return false;
  if (!draft.visibilities?.includes("GROUP") || !draft.groupIds?.length) return false;
  return !sameInstant(original.startAt, draft.startAt) || !sameInstant(original.endAt, draft.endAt);
}

function sameInstant(a?: string, b?: string) {
  if (!a || !b) return a === b;
  return Date.parse(a) === Date.parse(b);
}
