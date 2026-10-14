import {App} from "@capacitor/app";
import {Capacitor} from "@capacitor/core";
import type {LocalNotificationsPlugin} from "@capacitor/local-notifications";
import {api} from "@/lib/api";
import {planReminders,REMINDER_HORIZON_DAYS} from "@/lib/local-reminders";
import type {CalendarEvent} from "@/lib/types";

// 네이티브 앱에서만 휴대폰 로컬 알림을 예약합니다. 웹 브라우저에서는 모든 함수가 아무것도 하지 않고,
// 플러그인도 동적 import 라 웹 번들에서 실행되지 않습니다.
// 앱 셸은 원격 사이트를 로드하므로 이 플러그인은 플러그인이 포함된 새 앱 빌드에서만 동작합니다.

// 권한은 알림이 있는 일정을 처음 저장할 때 한 번만 묻습니다. 거부해도 다시 묻지 않습니다.
const ASKED_KEY="toogeduler_reminder_permission_asked";
// 플러그인 프록시는 모든 속성을 네이티브 호출로 바꾸므로 Promise 결과로 바로 넘기면 .then()을 불러 iOS에서 실패한다. 모듈째 들고 있다가 꺼내 쓴다.
let plugin:Promise<{LocalNotifications:LocalNotificationsPlugin}>|null=null;
let queue:Promise<unknown>=Promise.resolve();

function native(){return Capacitor.isNativePlatform()}
function notifications(){return plugin??=import("@capacitor/local-notifications")}
// 지우고 다시 예약하는 작업이 겹치면 순서가 섞이므로 한 줄로 세워 실행합니다.
function serial(task:()=>Promise<void>){const next=queue.then(task).catch(e=>console.error("[reminders]",e));queue=next;return next}
async function cancelPending(ln:LocalNotificationsPlugin){const {notifications:pending}=await ln.getPending();if(pending.length)await ln.cancel({notifications:pending.map(n=>({id:n.id}))})}

// 기존 예약을 모두 지우고 앞으로 14일 안의 내 일정 알림을 다시 예약합니다. 권한이 없으면 지우기만 합니다.
// (schedule 은 권한이 없으면 스스로 권한 창을 띄우므로 허용된 경우에만 부릅니다.)
export function syncReminders(meId:number){
 if(!native())return Promise.resolve();
 return serial(async()=>{
  const ln=(await notifications()).LocalNotifications;await cancelPending(ln);
  if((await ln.checkPermissions()).display!=="granted")return;
  const now=new Date();const to=new Date(now.getTime()+REMINDER_HORIZON_DAYS*86400000);
  const events=await api<CalendarEvent[]>(`/api/events?from=${now.toISOString()}&to=${to.toISOString()}`);
  const planned=planReminders(events,meId,now);if(!planned.length)return;
  await ln.schedule({notifications:planned.map(r=>({id:r.id,title:r.title,body:r.body,schedule:{at:r.at,allowWhileIdle:true},extra:{link:r.link}}))});
 });
}

// 로그아웃·계정 삭제 때 이 기기에 예약된 알림을 모두 지웁니다.
export function cancelAllReminders(){
 if(!native())return Promise.resolve();
 return serial(async()=>cancelPending((await notifications()).LocalNotifications));
}

// 알림이 있는 일정을 저장한 직후 부릅니다. "denied" 는 이번에 처음 거부를 확인한 경우에만 돌려주므로 안내는 한 번만 나갑니다.
export async function askReminderPermissionOnce():Promise<"granted"|"denied"|"skipped">{
 if(!native())return "skipped";
 try{
  const ln=(await notifications()).LocalNotifications;const {display}=await ln.checkPermissions();
  if(display==="granted")return "granted";
  if(localStorage.getItem(ASKED_KEY))return "skipped";
  localStorage.setItem(ASKED_KEY,"1");
  if(display==="denied")return "denied";
  return (await ln.requestPermissions()).display==="granted"?"granted":"denied";
 }catch(e){console.error("[reminders] 권한 확인 실패",e);return "skipped"}
}

// 권한을 한 번 물었는데 허용되지 않은 상태인지(=일정 모달에 안내 문구를 보여야 하는지) 확인합니다.
export async function remindersBlocked(){
 if(!native())return false;
 try{const {display}=await (await notifications()).LocalNotifications.checkPermissions();return display==="denied"||(display!=="granted"&&Boolean(localStorage.getItem(ASKED_KEY)))}
 catch(e){console.error("[reminders] 권한 확인 실패",e);return false}
}

// 앱이 다시 앞으로 올 때마다 부릅니다. 설정에서 알림을 켠 경우도 이때 반영됩니다.
export function onAppForeground(callback:()=>void){
 if(!native())return ()=>{};
 const listener=App.addListener("appStateChange",({isActive})=>{if(isActive)callback()});
 return ()=>{listener.then(handle=>handle.remove())};
}

// 예약 알림을 탭하면 담아 둔 일정 링크(/?event=ID&at=회차시작)를 엽니다.
export function onReminderTap(open:(link:string)=>void){
 if(!native())return ()=>{};
 const listener=notifications().then(m=>m.LocalNotifications.addListener("localNotificationActionPerformed",({notification})=>{const link=notification.extra?.link;if(typeof link==="string"&&link.startsWith("/?event="))open(link)}));
 return ()=>{listener.then(handle=>handle.remove()).catch(()=>{})};
}
