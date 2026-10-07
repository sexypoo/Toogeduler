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
