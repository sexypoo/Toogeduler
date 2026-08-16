import type {Metadata,Viewport} from "next";
import "./globals.css";
import {NativeDeepLink} from "@/components/native-deep-link";
export const metadata:Metadata={title:"Toogeduler — 함께 맞추는 시간",description:"그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요."};
export const viewport:Viewport={width:"device-width",initialScale:1,viewportFit:"cover"};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="ko"><body><NativeDeepLink/>{children}</body></html>}
