import {test} from "node:test";
import assert from "node:assert/strict";
import {clockMinutes,seoulClock,seoulDays,seoulLabel} from "./seoul-time.ts";

test("서버가 준 시각을 기기 시간대와 상관없이 한국 시간으로 읽는다",()=>{
 // 한국 10월 18일 오전 9시 = UTC 10월 18일 0시. 유럽 기기에서는 02:00으로 보이던 값이다.
 assert.deepEqual(seoulClock("2026-10-18T00:00:00Z"),{day:"2026-10-18",minutes:9*60});
 assert.deepEqual(seoulClock("2026-10-17T15:30:00Z"),{day:"2026-10-18",minutes:30});
});

test("한국 시간 라벨",()=>{
 assert.equal(seoulLabel("2026-10-18T13:00:00Z",{hour:"numeric",minute:"2-digit"}),"오후 10:00");
});

test("HH:mm을 분으로 바꾼다",()=>{
 assert.equal(clockMinutes("09:00"),540);
 assert.equal(clockMinutes("22:30"),1350);
});

test("기간의 날짜를 양 끝 포함해 나열한다",()=>{
 const days=seoulDays("2026-10-30","2026-11-02");
 assert.deepEqual(days.map(d=>d.day),["2026-10-30","2026-10-31","2026-11-01","2026-11-02"]);
 assert.equal(seoulLabel(days[2].noon,{weekday:"short"}),"일");
});
