import type { Metadata } from "next";
import { fetchInvitePreview, inviteMetadata } from "@/lib/invite-meta";

// page.tsx 가 클라이언트 컴포넌트라 미리보기 카드용 메타데이터는 여기서 만듭니다.
// 미리보기를 못 가져와도 대체 문구로 넘어가고, 페이지 렌더링은 막지 않습니다.
const API_ORIGIN = process.env.API_ORIGIN || "http://localhost:8080";

export async function generateMetadata({ params }: { params: Promise<{ token: string }> }): Promise<Metadata> {
  const { token } = await params;
  return inviteMetadata(await fetchInvitePreview(API_ORIGIN, token));
}

export default function InviteLayout({ children }: { children: React.ReactNode }) {
  return children;
}
