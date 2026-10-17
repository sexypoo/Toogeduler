"use client";
import {BrandMark} from "@/components/brand-mark";
import {useCallback,useEffect,useRef,useState} from "react";import {useParams,useRouter} from "next/navigation";import {UsersRound} from "lucide-react";import {api,ApiError,OAUTH_READY,token} from "@/lib/api";import {startOAuth} from "@/lib/oauth";import type {Group} from "@/lib/types";
// 링크만 있으면 누구나 보는 정보라 이름·색·멤버 수·그룹장 이름만 옵니다. 열기만 해서는 가입하지 않고, "참여하기"를 눌러야 POST 합니다.
type Preview={name:string;color:string;memberCount:number;ownerName:string};
type Phase="loading"|"ready"|"joining"|"joined"|"member"|"invalid"|"failed";
export default function Invite(){
 const params=useParams<{token:string}>(),router=useRouter(),invite=params.token;
 const[phase,setPhase]=useState<Phase>("loading"),[preview,setPreview]=useState<Preview|null>(null),[signedIn,setSignedIn]=useState(false),[groupId,setGroupId]=useState<number|null>(null),[joinError,setJoinError]=useState("");
 const focusRef=useRef<HTMLElement|null>(null);
 // 이미 멤버인지는 내 그룹 목록의 초대 링크로 판단합니다. 미리보기 응답에 그룹 ID 를 넣지 않기 위해서입니다.
 // 저장된 토큰이 만료(401)됐으면 비로그인 화면을 보여줍니다.
 const load=useCallback(async()=>{setPhase("loading");try{const[p,mine]=await Promise.all([api<Preview>(`/api/public/invites/${invite}`),token()?api<Group[]>("/api/groups").catch(e=>{if(e instanceof ApiError&&e.status===401){localStorage.removeItem("toogeduler_token");return null}return [] as Group[]}):Promise.resolve(null)]);setPreview(p);setSignedIn(mine!==null);const joined=mine?.find(g=>g.inviteUrl.endsWith(`/invite/${invite}`));if(joined){setGroupId(joined.id);setPhase("member")}else setPhase("ready")}catch(e){setPhase(e instanceof ApiError&&e.status===404?"invalid":"failed")}},[invite]);
 useEffect(()=>{load()},[load]);
 // 상태가 바뀌면 새로 나온 제목이나 오류로 포커스를 옮겨 스크린리더가 바로 읽게 합니다.
 useEffect(()=>{if(phase!=="loading"&&phase!=="joining")focusRef.current?.focus()},[phase,joinError]);
 const focusTarget=(el:HTMLElement|null)=>{focusRef.current=el};
 const goGroup=()=>router.replace(groupId?`/?group=${groupId}`:"/");
 function toLogin(register:boolean){sessionStorage.setItem("pending_invite",invite);router.push(register?"/login?mode=register":"/login")}
 function social(provider:"kakao"|"google"){sessionStorage.setItem("pending_invite",invite);startOAuth(provider)}
 async function join(){setJoinError("");setPhase("joining");try{const g=await api<Group>(`/api/groups/join/${invite}`,{method:"POST"});setGroupId(g.id);setPhase("joined");setTimeout(()=>router.replace(`/?group=${g.id}`),900)}catch(e){if(e instanceof ApiError&&e.status===401){localStorage.removeItem("toogeduler_token");sessionStorage.setItem("pending_invite",invite);router.replace("/login?session=expired");return}setJoinError(e instanceof Error?e.message:"참여하지 못했어요. 잠시 후 다시 시도해주세요.");setPhase("ready")}}
 let body:React.ReactNode;
 if(phase==="loading")body=<div role="status" aria-live="polite"><span className="loader"/><span className="sr-only">초대 정보를 불러오고 있어요</span></div>;
 else if(phase==="invalid")body=<div role="alert"><h1 ref={focusTarget} tabIndex={-1}>초대 링크를 확인해주세요</h1><p>이 링크는 사용할 수 없어요. 그룹장에게 새 링크를 요청하세요.</p><button type="button" className="switch-auth" onClick={()=>router.replace("/")}>홈으로</button></div>;
 else if(phase==="failed"||!preview)body=<div role="alert"><h1 ref={focusTarget} tabIndex={-1}>초대 정보를 불러오지 못했어요</h1><p>잠시 후 다시 시도해주세요.</p><button type="button" className="primary full" onClick={load}>다시 시도</button></div>;
 else if(phase==="joined")body=<div role="status" aria-live="polite"><span className="invite-icon"><UsersRound/></span><h1 ref={focusTarget} tabIndex={-1}>그룹에 참여했어요</h1><p>{preview.name} 캘린더로 이동합니다.</p><span className="loader"/><button type="button" className="switch-auth" onClick={goGroup}>바로 이동</button></div>;
 else if(phase==="member")body=<div role="status" aria-live="polite"><span className="invite-icon"><UsersRound/></span><h1 ref={focusTarget} tabIndex={-1}>이미 참여 중이에요</h1><p>{preview.name}</p><button type="button" className="primary full" onClick={goGroup}>그룹으로 이동</button></div>;
 else body=<section aria-labelledby="invite-group-name"><span className="public-badge">초대장</span><div className="group-preview invite-group"><i aria-hidden="true" style={{background:preview.color}}/><span><h1 id="invite-group-name">{preview.name}</h1><span className="invite-owner">{preview.ownerName}님이 초대했어요</span><small>멤버 {preview.memberCount}명</small></span></div>
  {joinError&&<p className="form-error" role="alert" ref={focusTarget} tabIndex={-1}>{joinError}</p>}
  {signedIn?<><button type="button" className="primary full" onClick={join} disabled={phase==="joining"}>{phase==="joining"?"참여하는 중...":"참여하기"}</button><button type="button" className="switch-auth" onClick={()=>router.replace("/")}>나중에 할게요</button></>
  :<><button type="button" className="primary full" onClick={()=>toLogin(true)}>가입하고 참여하기</button>{OAUTH_READY&&<><div className="or"><span>또는</span></div><div className="social-row"><button type="button" onClick={()=>social("kakao")} className="social kakao" aria-label="Kakao로 계속하고 그룹 참여"><b>K</b><span>Kakao로 계속</span></button><button type="button" onClick={()=>social("google")} className="social google" aria-label="Google로 계속하고 그룹 참여"><b>G</b><span>Google로 계속</span></button></div></>}<button type="button" className="switch-auth" onClick={()=>toLogin(false)}>이미 계정이 있어요 · 로그인</button></>}
  <p className="form-note">참여하면 그룹 멤버에게 내 이름이 보여요.</p></section>;
 return <main className="invite-page"><div className="invite-card"><BrandMark/>{body}</div></main>
}
