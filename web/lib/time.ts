import {addDays,startOfDay} from "date-fns";

// 하루 칸에 걸치는 일정인지 확인합니다. 자정을 넘기는 일정도 이어지는 날에 보입니다.
export function overlapsDay(startAt:string,endAt:string,day:Date){const from=startOfDay(day),to=addDays(from,1);return new Date(startAt)<to&&new Date(endAt)>from}

// 주간 뷰의 표시 구간(firstHour부터 hours시간) 안으로 일정을 잘라 top/height 비율(%)을 돌려줍니다.
// 구간 밖에서 시작하거나 끝나는 일정도 가장자리에 최소 30분 높이로 보이게 합니다.
export function blockPosition(startAt:string,endAt:string,day:Date,firstHour:number,hours:number){
 const total=hours*60,minLength=30;const base=new Date(day);base.setHours(firstHour,0,0,0);
 const minutes=(value:string)=>(new Date(value).getTime()-base.getTime())/60000;
 const start=Math.min(Math.max(minutes(startAt),0),total-minLength);const end=Math.min(Math.max(minutes(endAt),start+minLength),total);
 return {top:start/total*100,height:(end-start)/total*100};
}

// 반복 일정의 한 회차를 옮기거나 고친 만큼 시리즈의 기준 시작/종료 시각을 함께 옮깁니다.
// 회차 시각을 그대로 보내면 시리즈 기준점이 그 회차로 바뀌어 이전 회차들이 사라집니다.
export function shiftSeries(event:{startAt:string;endAt:string;instanceStart?:string;seriesStartAt?:string;seriesEndAt?:string},startAt:string,endAt:string){
 if(!event.seriesStartAt||!event.seriesEndAt)return {startAt,endAt};
 const occurrenceStart=new Date(event.instanceStart||event.startAt).getTime();const occurrenceEnd=occurrenceStart+(new Date(event.seriesEndAt).getTime()-new Date(event.seriesStartAt).getTime());
 return {startAt:new Date(new Date(event.seriesStartAt).getTime()+new Date(startAt).getTime()-occurrenceStart).toISOString(),endAt:new Date(new Date(event.seriesEndAt).getTime()+new Date(endAt).getTime()-occurrenceEnd).toISOString()};
}

// 주간 시간표에 그릴 시간 범위. 기본은 9시–19시이고, 그 주에 더 이르거나 늦은 일정이 있으면 그 시각까지 넓힌다.
// 자정을 넘기는 일정은 날마다 잘라서 본다(전날 밤 → 0시, 다음 날 새벽 ← 24시).
export function weekHourRange(events:{startAt:string;endAt:string;allDay:boolean}[],days:Date[],defaultFirst=9,defaultLast=19){
 let first=defaultFirst,last=defaultLast;
 for(const e of events){
  if(e.allDay)continue;
  for(const d of days){
   if(!overlapsDay(e.startAt,e.endAt,d))continue;
   const dayStart=startOfDay(d).getTime(),dayEnd=addDays(startOfDay(d),1).getTime();
   const s=Math.max(new Date(e.startAt).getTime(),dayStart),end=Math.min(new Date(e.endAt).getTime(),dayEnd);
   first=Math.min(first,Math.floor((s-dayStart)/3600000));last=Math.max(last,Math.ceil((end-dayStart)/3600000));
  }
 }
 return {first:Math.max(0,first),last:Math.min(24,Math.max(last,first+1))};
}

// 같은 날 겹치는 일정을 시간표처럼 나란히 놓을 열을 정한다. 서로 이어서 겹치는 일정 묶음마다 필요한 열 수를 세고,
// 각 일정은 비어 있는 가장 왼쪽 열에 들어간다. 반환: 일정 순서대로 {col, cols, cluster}. cluster는 겹침 묶음 번호.
export function sideBySide(items:{startAt:string;endAt:string}[]){
 const order=items.map((e,i)=>({i,s:new Date(e.startAt).getTime(),e:new Date(e.endAt).getTime()})).sort((a,b)=>a.s-b.s||b.e-a.e);
 const result:{col:number;cols:number;cluster:number}[]=items.map(()=>({col:0,cols:1,cluster:0}));
 let cluster:typeof order=[],colEnds:number[]=[],clusterEnd=-Infinity,clusterNo=-1;
 const close=()=>{for(const x of cluster)result[x.i].cols=colEnds.length;cluster=[];colEnds=[];clusterNo++};
 for(const x of order){
  if(x.s>=clusterEnd)close();
  let col=colEnds.findIndex(end=>end<=x.s);if(col<0){col=colEnds.length;colEnds.push(x.e)}else colEnds[col]=x.e;
  result[x.i].col=col;result[x.i].cluster=clusterNo;cluster.push(x);clusterEnd=Math.max(clusterEnd,x.e);
 }
 close();
 return result;
}

// 한 묶음이 max열을 넘으면 앞의 max-1열만 그대로 그리고, 나머지는 마지막 열의 "+N" 하나로 접는다.
// 8명이 같은 시간에 일정을 넣어도 한 열이 읽을 수 없을 만큼 좁아지지 않게 한다.
export function capLanes(lanes:{col:number;cols:number;cluster:number}[],max=3){
 return lanes.map(l=>l.cols<=max?{...l,hidden:false}:{...l,cols:max,hidden:l.col>=max-1});
}

// "함께 되는 시간"에서 먼저 보여줄 구간: 되는 사람이 가장 많은 구간, 같으면 더 이른 구간.
// 큰 그룹은 모두 되는 시간이 없을 때가 많아서, 그때 가장 이른 아무 구간(3명만 되는 시간)을 내세우지 않게 한다.
export function bestBlock<T extends {startAt:string;availableCount:number}>(blocks:T[]):T|null{
 let best:T|null=null;
 for(const b of blocks)if(!best||b.availableCount>best.availableCount||(b.availableCount===best.availableCount&&b.startAt<best.startAt))best=b;
 return best;
}
