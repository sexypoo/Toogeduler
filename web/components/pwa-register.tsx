"use client";
import { useEffect } from "react";
import { Capacitor } from "@capacitor/core";
import { registerServiceWorker } from "@/lib/pwa";

// 홈 화면 설치용 서비스 워커를 등록합니다. 운영 빌드의 일반 브라우저에서만 등록하고, Capacitor 앱 안에서는 하지 않습니다.
export function PwaRegister() {
  useEffect(() => {
    registerServiceWorker({
      native: Capacitor.isNativePlatform(),
      production: process.env.NODE_ENV === "production",
      serviceWorker: "serviceWorker" in navigator ? navigator.serviceWorker : undefined,
    });
  }, []);
  return null;
}
