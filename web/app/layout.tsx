import type {Metadata} from "next";
import "./globals.css";
export const metadata:Metadata={title:"Toogeduler — 함께 맞추는 시간",description:"그룹 일정을 공유하고 모두가 되는 시간을 찾아보세요."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="ko"><body>{children}</body></html>}

