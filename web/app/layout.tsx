import type {Metadata,Viewport} from "next";
import "./globals.css";
import {NativeDeepLink} from "@/components/native-deep-link";
export const metadata:Metadata={title:"Toogeduler — 함께 맞추는 시간",description:"그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요."};
export const viewport:Viewport={width:"device-width",initialScale:1,viewportFit:"cover",themeColor:"#eef3f5"};
const PRETENDARD="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css";
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="ko"><head><link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin=""/><link rel="stylesheet" href={PRETENDARD}/></head><body><NativeDeepLink/>{children}</body></html>}
