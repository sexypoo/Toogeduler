"use client";
import {useCallback,useEffect,useState} from "react";
import {useRouter} from "next/navigation";
import {addDays,format,parseISO,startOfDay} from "date-fns";
import {ko} from "date-fns/locale";
import {ArrowLeft,CalendarDays,Clock3,Globe2,MapPin,Search,UserRound} from "lucide-react";
import {api,token} from "@/lib/api";
import type {CalendarEvent} from "@/lib/types";

export default function PublicCalendarPage(){
 const router=useRouter();const[events,setEvents]=useState<CalendarEvent[]>([]);const[query,setQuery]=useState("");const[loading,setLoading]=useState(true);const[error,setError]=useState("");
 const load=useCallback(async(q="")=>{setLoading(true);setError("");try{const from=startOfDay(new Date()),to=addDays(from,90);setEvents(await api<CalendarEvent[]>(`/api/public/events?from=${from.toISOString()}&to=${to.toISOString()}&q=${encodeURIComponent(q)}`))}catch(e){setError(e instanceof Error?e.message:"공개 일정을 불러오지 못했습니다.")}finally{setLoading(false)}},[]);
 useEffect(()=>{load()},[load]);
 return <main className="public-feed"><header><button className="back-link" onClick={()=>router.push(token()?"/":"/login")}><ArrowLeft/>{token()?"내 캘린더":"로그인"}</button><div className="brand"><span className="brand-mark"><CalendarDays/></span>Toogeduler</div><button className="feed-login" onClick={()=>router.push(token()?"/mypage":"/login")}>{token()?"마이페이지":"로그인"}</button></header><section className="public-hero"><div><p className="eyebrow">OPEN CALENDAR</p><h1>모두에게 열린 일정</h1><p>로그인하지 않아도 볼 수 있도록 공개한 일정을 모았습니다.</p></div><span className="globe-orbit"><Globe2/><i/><i/></span></section><form className="public-search" onSubmit={e=>{e.preventDefault();load(query)}}><Search/><input aria-label="공개 일정 검색" value={query} onChange={e=>setQuery(e.target.value)} placeholder="일정 제목이나 설명으로 검색"/><button>검색</button></form><section className="public-feed-content"><div className="feed-title"><h2>앞으로 90일</h2><span>{events.length}개의 공개 일정</span></div>{loading?<div className="center-state small"><span className="loader"/>일정을 찾고 있어요</div>:error?<div className="feed-empty"><Globe2/><b>공개 일정을 불러오지 못했어요</b><span>{error}</span></div>:events.length===0?<div className="feed-empty"><CalendarDays/><b>조건에 맞는 공개 일정이 없어요</b><span>다른 검색어로 찾아보세요.</span></div>:<div className="public-event-grid">{events.map(event=><button key={`${event.id}-${event.startAt}`} className="public-event-card" onClick={()=>router.push(`/public/${event.publicToken}`)}><span className="event-date"><b>{format(parseISO(event.startAt),"d")}</b><small>{format(parseISO(event.startAt),"M월 EEE",{locale:ko})}</small></span><span className="event-card-body"><i style={{background:event.color}}/><em>전체 공개</em><strong>{event.title}</strong><span><Clock3/>{event.allDay?"종일":`${format(parseISO(event.startAt),"HH:mm")} – ${format(parseISO(event.endAt),"HH:mm")}`}</span>{event.location&&<span><MapPin/>{event.location}</span>}<span><UserRound/>{event.owner.name}</span></span></button>)}</div>}</section></main>
}
