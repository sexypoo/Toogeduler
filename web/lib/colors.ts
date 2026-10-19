// 일정·그룹·사람에 쓰는 다섯 가지 색. 일정 색은 이 값 그대로 DB에 저장되므로 값을 바꾸면 기존 일정과 어긋난다.
export const EVENT_COLORS = ["#168CF2", "#19B7B1", "#8B6FF7", "#F0648C", "#F29E38"];

export const COLOR_NAMES: Record<string, string> = {
  "#168CF2": "파랑",
  "#19B7B1": "청록",
  "#8B6FF7": "보라",
  "#F0648C": "분홍",
  "#F29E38": "주황",
};

/** 새 그룹을 만들 때 처음 골라 두는 색 */
export const DEFAULT_GROUP_COLOR = "#19B7B1";
