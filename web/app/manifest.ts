import type { MetadataRoute } from "next";

// 홈 화면 설치용 웹 앱 매니페스트(/manifest.webmanifest). 색은 앱 바탕(--paper)과 viewport.themeColor 에 맞춥니다.
// 아이콘은 iOS 앱 아이콘에서 만들었고, maskable 은 글리프를 안전영역 안으로 줄인 별도 파일입니다("any maskable" 로 합치지 않습니다).
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Toogeduler",
    short_name: "Toogeduler",
    description: "그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요.",
    lang: "ko",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#ffffff",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
