import {test} from "node:test";
import assert from "node:assert/strict";
import {sideBySide,weekHourRange} from "./time.ts";

const week=Array.from({length:7},(_,i)=>new Date(2026,9,25+i));
const at=(d:number,h:number,m=0)=>new Date(2026,9,d,h,m).toISOString();

test("일정이 없거나 9–19시 안에 있으면 기본 범위",()=>{
 assert.deepEqual(weekHourRange([],week),{first:9,last:19});
 assert.deepEqual(weekHourRange([{startAt:at(27,10),endAt:at(27,12),allDay:false}],week),{first:9,last:19});
});

test("새벽 1시 일정이 있으면 1시부터 그린다",()=>{
 assert.deepEqual(weekHourRange([{startAt:at(27,1),endAt:at(27,2),allDay:false}],week),{first:1,last:19});
});

test("밤 늦은 일정은 끝나는 시각을 올림해 아래로 늘린다",()=>{
 assert.deepEqual(weekHourRange([{startAt:at(28,21),endAt:at(28,22,30),allDay:false}],week),{first:9,last:23});
});

test("자정을 넘기는 일정은 다음 날 새벽 쪽도 넓힌다",()=>{
 assert.deepEqual(weekHourRange([{startAt:at(28,23),endAt:at(29,1),allDay:false}],week),{first:0,last:24});
});

test("종일 일정과 다른 주의 일정은 범위에 영향이 없다",()=>{
 assert.deepEqual(weekHourRange([{startAt:at(27,0),endAt:at(28,0),allDay:true},{startAt:at(20,2),endAt:at(20,3),allDay:false}],week),{first:9,last:19});
});

test("겹치지 않는 일정은 각자 한 열 전체",()=>{
 assert.deepEqual(sideBySide([{startAt:at(27,9),endAt:at(27,10)},{startAt:at(27,10),endAt:at(27,11)}]),[{col:0,cols:1},{col:0,cols:1}]);
});

test("겹치는 두 일정은 두 열로 나란히",()=>{
 assert.deepEqual(sideBySide([{startAt:at(27,11),endAt:at(27,13)},{startAt:at(27,10,30),endAt:at(27,12)}]),[{col:1,cols:2},{col:0,cols:2}]);
});

test("이어서 겹치는 묶음은 같은 열 수, 빈 열은 다시 쓴다",()=>{
 // 9–11, 10–12, 11–13: 첫째와 셋째는 겹치지 않아 0열을 함께 쓰고, 묶음 전체는 2열
 assert.deepEqual(sideBySide([{startAt:at(27,9),endAt:at(27,11)},{startAt:at(27,10),endAt:at(27,12)},{startAt:at(27,11),endAt:at(27,13)}]),[{col:0,cols:2},{col:1,cols:2},{col:0,cols:2}]);
});

test("묶음이 끝나면 다음 일정은 다시 한 열",()=>{
 assert.deepEqual(sideBySide([{startAt:at(27,9),endAt:at(27,10)},{startAt:at(27,9,30),endAt:at(27,10,30)},{startAt:at(27,14),endAt:at(27,15)}]),[{col:0,cols:2},{col:1,cols:2},{col:0,cols:1}]);
});
