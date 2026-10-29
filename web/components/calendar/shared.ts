import { format } from "date-fns";
import type { Visibility } from "@/lib/types";

export const visibilityLabel: Record<Visibility, string> = {
  PRIVATE: "나만 보기",
  GROUP: "그룹 공개",
  FRIENDS: "친구 공개",
  PUBLIC: "전체 공개",
};
export const fmtDate = (d: Date) => format(d, "yyyy-MM-dd");
export const REMINDERS_DENIED = "알림이 꺼져 있어요. 휴대폰 설정에서 Toogeduler 알림을 켜면 일정 전에 알려드려요.";
export const offsetIso = (value: string) => new Date(value).toISOString();
// 초대 링크 만료 한 줄. 24시간 안에 끝나거나 이미 지났으면 문구를 바꾸고 soon 으로 표시합니다(색만으로 구분하지 않습니다).
export function inviteExpiry(iso: string, now = Date.now()) {
  const left = new Date(iso).getTime() - now;
  if (left <= 0) return { text: "링크가 만료됐어요", soon: true, expired: true };
  if (left <= 24 * 60 * 60 * 1000) return { text: "링크 만료: 오늘", soon: true, expired: false };
  return { text: `링크 만료: ${format(new Date(iso), "M월 d일")}`, soon: false, expired: false };
}
