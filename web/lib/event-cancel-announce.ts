// 일정 편집 모달의 "그룹 멤버에게 취소 알리기" 줄을 보일지와 삭제 요청 주소를 정하는 순수 로직. 화면 없이 테스트할 수 있게 컴포넌트와 나눠 둡니다.

type Saved = { id?: number; visibilities?: string[]; visibility?: string; owner?: { id: number } };

/**
 * 저장된 기존 일정이고, 내 일정이며, 저장된 공개 범위에 GROUP 이 있을 때만 true 입니다.
 * 편집 중인 초안이 아니라 모달을 연 시점의 값으로 판단합니다. 서버에 남은 일정이 그룹 공개여야 알릴 대상이 있기 때문입니다.
 */
export function shouldOfferCancelAnnounce(saved: Saved, meId: number) {
  if (!saved.id) return false;
  if (saved.owner && saved.owner.id !== meId) return false;
  const visibilities = saved.visibilities?.length ? saved.visibilities : [saved.visibility || "PRIVATE"];
  return visibilities.includes("GROUP");
}

/** 취소 알리기를 고르면 `?announce=true` 를 붙이고, 아니면 지금처럼 쿼리 없이 지웁니다. */
export function deleteEventUrl(id: number, announce: boolean) {
  return `/api/events/${id}${announce ? "?announce=true" : ""}`;
}
