import type { Metadata, Viewport } from "next";
import "./globals.css";
import { NativeDeepLink } from "@/components/native-deep-link";
import { PwaRegister } from "@/components/pwa-register";
export const metadata: Metadata = {
  title: "Toogeduler — 함께 맞추는 시간",
  description: "그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요.",
  // 홈 화면에 추가했을 때 주소창 없이 열리게 합니다. 앱 바탕이 흰색이라 상태바는 default(흰 바탕+검정 글자)를 씁니다.
  appleWebApp: { capable: true, title: "Toogeduler", statusBarStyle: "default" },
  icons: {
    icon: [
      { url: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { url: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
    apple: { url: "/icons/apple-touch-icon.png", sizes: "180x180" },
  },
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
        <PwaRegister />
        {children}
      </body>
    </html>
  );
}
