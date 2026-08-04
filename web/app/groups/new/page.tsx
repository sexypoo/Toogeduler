"use client";
import {useEffect,useState} from "react";
import {useRouter} from "next/navigation";
import {ArrowLeft,CalendarDays,Check,Copy,Link2,UsersRound} from "lucide-react";
import {api,token} from "@/lib/api";
import type {Group} from "@/lib/types";

const COLORS=["#19B7B1","#168CF2","#8B6FF7","#F0648C","#F29E38"];

export default function NewGroupPage(){
 const router=useRouter();const[name,setName]=useState("");const[color,setColor]=useState(COLORS[0]);const[group,setGroup]=useState<Group|null>(null);const[error,setError]=useState("");const[loading,setLoading]=useState(false);const[copied,setCopied]=useState(false);
 useEffect(()=>{if(!token())router.replace("/login")},[router]);
 async function submit(e:React.FormEvent){e.preventDefault();setLoading(true);setError("");try{const created=await api<Group>("/api/groups",{method:"POST",body:JSON.stringify({name:name.trim(),color})});sessionStorage.setItem("toogeduler_group",String(created.id));setGroup(created)}catch(e){setError(e instanceof Error?e.message:"그룹을 만들지 못했습니다.")}finally{setLoading(false)}}
 function copy(){if(!group)return;navigator.clipboard.writeText(group.inviteUrl);setCopied(true);setTimeout(()=>setCopied(false),1800)}
 return <main className="focused-page"><header className="focused-header"><button className="back-link" onClick={()=>router.push("/")}><ArrowLeft/>캘린더</button><div className="brand"><span className="brand-mark"><CalendarDays/></span>Toogeduler</div><span/></header><section className="group-create-layout"><div className="group-create-story"><span className="story-icon"><UsersRound/></span><p className="eyebrow">NEW CIRCLE</p><h1>함께 볼 시간을<br/>한곳에 모아요.</h1><p>그룹을 만든 뒤 초대 링크 하나만 보내세요. 모든 멤버가 서로의 그룹 공개 일정을 확인하고, 함께 되는 시간을 찾을 수 있어요.</p><div className="orbit" aria-hidden="true"><i/><i/><i/></div></div>{group?<article className="create-card success-card"><span className="success-mark"><Check/></span><p className="eyebrow">GROUP READY</p><h2>{group.name}을 만들었어요</h2><p>이 링크를 공유하면 바로 그룹에 참여할 수 있어요.</p><div className="created-invite"><Link2/><input readOnly value={group.inviteUrl}/><button onClick={copy}>{copied?<Check/>:<Copy/>}{copied?"복사됨":"복사"}</button></div><button className="primary full" onClick={()=>router.push("/")}>그룹 캘린더 열기</button></article>:<form className="create-card" onSubmit={submit}><p className="eyebrow">GROUP DETAILS</p><h2>새 그룹 만들기</h2><label>그룹 이름<input autoFocus required minLength={2} maxLength={40} value={name} onChange={e=>setName(e.target.value)} placeholder="예: 주말 탐험대"/><small>{name.length}/40</small></label><fieldset><legend>그룹 색상</legend><div className="large-color-picker">{COLORS.map(c=><button type="button" aria-label={`${c} 색상`} key={c} className={color===c?"selected":""} style={{background:c}} onClick={()=>setColor(c)}>{color===c&&<Check/>}</button>)}</div></fieldset><div className="group-preview"><i style={{background:color}}/><span><b>{name||"그룹 이름"}</b><small>나 · 그룹장</small></span></div>{error&&<p className="form-error">{error}</p>}<button className="primary full" disabled={loading}>{loading?"만드는 중...":"그룹 만들기"}</button><p className="form-note">생성 후 모든 멤버가 초대 링크를 공유할 수 있어요.</p></form>}</section></main>
}
