// 초대 링크(/invite/{token})를 메신저에 붙였을 때 뜨는 미리보기 카드 문구입니다.
// 미리보기 API 가 이미 공개하는 그룹 이름·멤버 수·그룹장 이름만 씁니다.
// fetch 를 인자로 받으므로 `npm test` 로 네트워크 없이 검증합니다.
import type { Metadata } from "next";

export type InvitePreview = { name: string; memberCount: number; ownerName: string };

export const INVITE_FALLBACK_TITLE = "Toogeduler 그룹 초대";
const FALLBACK_DESCRIPTION = "링크를 열어 그룹에 참여하고 함께 되는 시간을 맞춰 보세요. Toogeduler";
const OG_IMAGE = "/icons/icon-512.png";
// 메신저 크롤러가 오래 기다리지 않도록, 이 시간 안에 답이 없으면 대체 문구를 씁니다.
export const PREVIEW_TIMEOUT_MS = 2000;

// preview 가 없으면(404·410·네트워크 오류·시간 초과) 공통 대체 문구를 돌려줍니다.
export function inviteMetadata(preview: InvitePreview | null): Metadata {
  const title = preview ? `"${preview.name}" 그룹에 초대받았어요` : INVITE_FALLBACK_TITLE;
  const description = preview ? describe(preview) : FALLBACK_DESCRIPTION;
  return {
    title,
    description,
    openGraph: { title, description, images: [OG_IMAGE], type: "website" },
    twitter: { card: "summary", title, description, images: [OG_IMAGE] },
  };
}

function describe(p: InvitePreview): string {
  const others = p.memberCount - 1;
  // 그룹장 혼자인 그룹에 "외 0명"이라고 쓰지 않습니다.
  const who = others > 0 ? `${p.ownerName}님 외 ${others}명이` : `${p.ownerName}님이`;
  return `${who} 함께 되는 시간을 맞추고 있어요. Toogeduler`;
}

// 서버에서 미리보기 API 를 부릅니다. 200 이 아니거나 응답 모양이 다르면 null 을 돌려주고, 예외를 던지지 않습니다.
export async function fetchInvitePreview(
  apiOrigin: string,
  token: string,
  fetchImpl: typeof fetch = fetch,
): Promise<InvitePreview | null> {
  try {
    const res = await fetchImpl(`${apiOrigin}/api/public/invites/${encodeURIComponent(token)}`, {
      cache: "no-store",
      signal: AbortSignal.timeout(PREVIEW_TIMEOUT_MS),
    });
    if (res.status !== 200) return null;
    const body = (await res.json()) as Partial<InvitePreview>;
    if (typeof body.name !== "string" || typeof body.ownerName !== "string" || typeof body.memberCount !== "number") {
      return null;
    }
    return { name: body.name, memberCount: body.memberCount, ownerName: body.ownerName };
  } catch {
    return null;
  }
}
