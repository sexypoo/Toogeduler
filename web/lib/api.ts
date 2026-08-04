const API=process.env.NEXT_PUBLIC_API_URL||"/backend";
const OAUTH_ORIGIN=process.env.NEXT_PUBLIC_OAUTH_URL||(process.env.NODE_ENV==="production"?"https://api.toogeduler.com":API);
export function token(){return typeof window==="undefined"?null:localStorage.getItem("toogeduler_token");}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{
  const headers=new Headers(options.headers);if(options.body)headers.set("Content-Type","application/json");const value=token();if(value)headers.set("Authorization",`Bearer ${value}`);
  const res=await fetch(`${API}${path}`,{...options,headers});if(!res.ok){const body=await res.json().catch(()=>({}));throw new Error(body.message||"요청을 처리하지 못했습니다.");}if(res.status===204)return undefined as T;return res.json();
}
export {API,OAUTH_ORIGIN};
