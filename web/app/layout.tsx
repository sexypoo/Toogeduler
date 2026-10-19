import type { Metadata, Viewport } from "next";
import "./globals.css";
import { NativeDeepLink } from "@/components/native-deep-link";
export const metadata: Metadata = {
  title: "Toogeduler — 함께 맞추는 시간",
  description: "그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요.",
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#ffffff",
};
// 본문 서체. 숫자·한글 모두 또렷하고 자간이 단정한 원티드 산스(OFL)를 씁니다.
const WANTED_SANS =
  "https://cdn.jsdelivr.net/gh/wanteddev/wanted-sans@v1.0.3/packages/wanted-sans/fonts/webfonts/variable/split/WantedSansVariable.min.css";
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="" />
        <link rel="stylesheet" href={WANTED_SANS} />
      </head>
      <body>
        <NativeDeepLink />
        {children}
      </body>
    </html>
  );
}
