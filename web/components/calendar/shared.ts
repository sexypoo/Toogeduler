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
