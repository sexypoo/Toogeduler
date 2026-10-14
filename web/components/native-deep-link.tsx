"use client";
import {useEffect} from "react";
import {App} from "@capacitor/app";
import {Capacitor} from "@capacitor/core";
import {onReminderTap} from "@/lib/native-reminders";

// 네이티브 셸은 운영 웹사이트를 그대로 로드하므로 window.location.origin 이 곧 운영 주소입니다.
// 별도 환경변수 없이 항상 현재 출처로 복귀시켜 죽은 도메인으로 새는 것을 막습니다.
export function NativeDeepLink(){
  useEffect(()=>{
    if(!Capacitor.isNativePlatform())return;
    const listener=App.addListener("appUrlOpen",({url})=>{
      let callback:URL;
      try{callback=new URL(url);}catch{return;}
      if(callback.protocol!=="toogeduler:")return;
      const path=`/${callback.host}${callback.pathname}`.replace(/\/{2,}/g,"/");
      window.location.assign(`${window.location.origin}${path}${callback.search}`);
    });
    // 휴대폰 일정 알림을 탭하면 그 회차의 일정 모달을 엽니다(캘린더의 ?event=&at= 처리 재사용).
    // 로그인이 풀려 있으면 캘린더가 로그인 화면으로 보냅니다.
    const stopReminderTap=onReminderTap(link=>window.location.assign(`${window.location.origin}${link}`));
    return ()=>{listener.then(handle=>handle.remove());stopReminderTap();};
  },[]);
  return null;
}
