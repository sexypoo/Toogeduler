const API=process.env.NEXT_PUBLIC_API_URL||"/backend";

// 소셜 로그인은 세션 쿠키를 API 도메인에 심어야 하므로 프록시(/backend)로 시작할 수 없습니다.
// 반드시 API의 실제 출처(origin)를 NEXT_PUBLIC_OAUTH_URL로 지정해야 합니다.
// 값이 없으면 빈 문자열이 되고, 로그인 화면이 소셜 버튼을 비활성화합니다.
const configuredOauth=(process.env.NEXT_PUBLIC_OAUTH_URL||"").replace(/\/+$/,"");
const OAUTH_ORIGIN=configuredOauth||(process.env.NODE_ENV==="production"?"":API);
const OAUTH_READY=OAUTH_ORIGIN!=="";

export function token(){return typeof window==="undefined"?null:localStorage.getItem("toogeduler_token");}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{
  const headers=new Headers(options.headers);if(options.body)headers.set("Content-Type","application/json");const value=token();if(value)headers.set("Authorization",`Bearer ${value}`);
  const res=await fetch(`${API}${path}`,{...options,headers});if(!res.ok){const body=await res.json().catch(()=>({}));throw new Error(body.message||"요청을 처리하지 못했습니다.");}if(res.status===204)return undefined as T;return res.json();
}
export {API,OAUTH_ORIGIN,OAUTH_READY};
