// "함께 되는 시간"은 보는 사람 기기의 시간대로 찾고 보여준다. 캘린더도 기기 시간대라서, 둘이 같아야
// 내 비공개 일정(오후 2–4시)이 패널에서도 오후 2–4시 "안 돼요"로 보인다.
export const viewerZone=()=>Intl.DateTimeFormat().resolvedOptions().timeZone||"Asia/Seoul";

/** zone 기준 날짜(YYYY-MM-DD)와 그날 0시부터 지난 분. */
export function zonedClock(iso:string,zone:string){
 const parts=Object.fromEntries(new Intl.DateTimeFormat("en-CA",{timeZone:zone,year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(new Date(iso)).map(p=>[p.type,p.value]));
 return {day:`${parts.year}-${parts.month}-${parts.day}`,minutes:Number(parts.hour)*60+Number(parts.minute)};
}

/** zone 기준으로 날짜·시각을 쓴다. 예) zonedLabel(iso,{hour:"numeric",minute:"2-digit"},zone) → "오후 3:00" */
export function zonedLabel(iso:string,options:Intl.DateTimeFormatOptions,zone:string){
 return new Intl.DateTimeFormat("ko-KR",{timeZone:zone,...options}).format(new Date(iso));
}

/** "HH:mm" → 0시부터 지난 분 */
export const clockMinutes=(value:string)=>{const[h,m]=value.split(":").map(Number);return h*60+m};

/** from부터 to까지(양 끝 포함) 달력 날짜. noon은 그 날짜의 UTC 정오라 요일을 시간대와 상관없이 구할 수 있다(timeZone:"UTC"로 표시). */
export function calendarDays(from:string,to:string){
 const days:{day:string;noon:string}[]=[];
 for(let t=Date.parse(`${from}T12:00:00Z`),end=Date.parse(`${to}T12:00:00Z`);t<=end&&days.length<62;t+=86400000){
  const noon=new Date(t).toISOString();days.push({day:noon.slice(0,10),noon});
 }
 return days;
}
