// "함께 되는 시간"은 서버가 한국 시간 기준으로 계산합니다. 화면도 기기 시간대와 상관없이 한국 시간으로 보여줘야
// 해외에 있는 멤버에게 "오전 9시–오후 10시"로 찾았는데 "02:00"이 나오는 일이 없습니다.
export const SEOUL="Asia/Seoul";

const clockFormat=new Intl.DateTimeFormat("en-CA",{timeZone:SEOUL,year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"});

/** 한국 시간 기준 날짜(YYYY-MM-DD)와 그날 0시부터 지난 분. */
export function seoulClock(iso:string){
 const parts=Object.fromEntries(clockFormat.formatToParts(new Date(iso)).map(p=>[p.type,p.value]));
 return {day:`${parts.year}-${parts.month}-${parts.day}`,minutes:Number(parts.hour)*60+Number(parts.minute)};
}

/** 한국 시간으로 날짜·시각을 씁니다. 예) seoulLabel(iso,{hour:"numeric",minute:"2-digit"}) → "오후 3:00" */
export function seoulLabel(iso:string,options:Intl.DateTimeFormatOptions){
 return new Intl.DateTimeFormat("ko-KR",{timeZone:SEOUL,...options}).format(new Date(iso));
}

/** "HH:mm" → 0시부터 지난 분 */
export const clockMinutes=(value:string)=>{const[h,m]=value.split(":").map(Number);return h*60+m};

/** from부터 to까지(양 끝 포함) 날짜 문자열. 각 날짜의 한국 시간 정오 시각도 함께 줘서 요일 표시에 씁니다. */
export function seoulDays(from:string,to:string){
 const days:{day:string;noon:string}[]=[];
 for(let t=Date.parse(`${from}T12:00:00+09:00`),end=Date.parse(`${to}T12:00:00+09:00`);t<=end&&days.length<62;t+=86400000){
  const noon=new Date(t).toISOString();days.push({day:seoulClock(noon).day,noon});
 }
 return days;
}
