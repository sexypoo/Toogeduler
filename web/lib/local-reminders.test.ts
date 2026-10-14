import {test} from "node:test";
import assert from "node:assert/strict";
import {planReminders,reminderBody,type ReminderSource} from "./local-reminders.ts";

// 시각은 모두 기기 로컬 시간대로 만들어, 어느 시간대에서 돌려도 같은 결과가 나오게 합니다.
const ME=1;
const now=new Date(2026,9,14,9,0);// 2026-10-14(수) 오전 9:00
const at=(day:number,hour:number,minute=0)=>new Date(2026,9,day,hour,minute);
function event(overrides:Partial<ReminderSource>&{start:Date}):ReminderSource{
 const {start,...rest}=overrides;
 return {id:10,title:"스터디",location:"",startAt:start.toISOString(),endAt:new Date(start.getTime()+3600000).toISOString(),allDay:false,recurrenceRule:"",reminderMinutes:10,owner:{id:ME},...rest};
}

test("앞으로의 일정은 알림 시각·제목·본문·링크로 예약된다",()=>{
 const start=at(15,15);
 const [reminder,...rest]=planReminders([event({start,location:"강남역 2번 출구"})],ME,now);
 assert.equal(rest.length,0);
 assert.equal(reminder.at.getTime(),start.getTime()-10*60000);
 assert.equal(reminder.title,"스터디");
 assert.equal(reminder.body,"10분 전 · 오후 3:00 · 강남역 2번 출구");
 assert.equal(reminder.link,`/?event=10&at=${start.toISOString()}`);
 assert.ok(Number.isInteger(reminder.id)&&reminder.id>0&&reminder.id<=0x7fffffff);
});

test("과거 일정, 알림 시각이 이미 지난 일정, reminderMinutes=0, 14일 밖 일정은 예약하지 않는다",()=>{
 const reminders=planReminders([
  event({id:1,start:at(13,15)}),// 어제
  event({id:2,start:at(14,9,5)}),// 5분 뒤 시작이라 10분 전 알림은 이미 지남
  event({id:3,start:at(16,15),reminderMinutes:0}),
  event({id:4,start:at(29,10)}),// 15일 뒤
 ],ME,now);
 assert.deepEqual(reminders,[]);
});

test("매주 반복 일정은 14일 안의 회차마다 하나씩 예약된다",()=>{
 // 9월 30일(수)부터 매주 수요일 오후 7시. 14일 창(10월 28일 오전 9시까지) 안에는 10월 14일·21일 회차만 있다.
 const series=event({start:at(14,19),recurrenceRule:"FREQ=WEEKLY",reminderMinutes:30});
 series.startAt=new Date(2026,8,30,19).toISOString();series.endAt=new Date(2026,8,30,20).toISOString();
 const reminders=planReminders([series],ME,now);
 assert.deepEqual(reminders.map(r=>r.at.getTime()),[at(14,18,30),at(21,18,30)].map(d=>d.getTime()));
 assert.equal(new Set(reminders.map(r=>r.id)).size,2);
 assert.ok(reminders[1].link.endsWith(`at=${at(21,19).toISOString()}`));
});

test("서버가 회차로 펼쳐 준 목록이어도 같은 회차를 두 번 예약하지 않는다",()=>{
 const seriesStart=new Date(2026,8,30,19).toISOString(),seriesEnd=new Date(2026,8,30,20).toISOString();
 const instances=[14,21].map(day=>({...event({start:at(day,19),recurrenceRule:"FREQ=WEEKLY"}),seriesStartAt:seriesStart,seriesEndAt:seriesEnd}));
 assert.equal(planReminders(instances,ME,now).length,2);
});

test("비공개 일정도 본인 기기에는 실제 제목으로 예약되고, 남의 일정은 예약하지 않는다",()=>{
 const mine={...event({id:1,start:at(16,15),title:"병원 예약"}),visibility:"PRIVATE"};
 const others=event({id:2,start:at(16,16),title:"친구 일정",owner:{id:2}});
 const reminders=planReminders([mine,others],ME,now);
 assert.deepEqual(reminders.map(r=>r.title),["병원 예약"]);
});

test("하루 전 알림과 종일 일정은 날짜를 함께 쓴다",()=>{
 assert.equal(reminderBody(at(15,15),1440,false),"하루 전 · 10월 15일(목) 오후 3:00");
 assert.equal(reminderBody(at(15,0),1440,true),"하루 전 · 10월 15일(목) 종일");
 assert.equal(reminderBody(at(15,12),60,false),"1시간 전 · 오후 12:00");
});
