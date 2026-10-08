import {Capacitor} from "@capacitor/core";
import {OAUTH_ORIGIN} from "@/lib/api";

// 소셜 로그인은 토큰 대신 일회용 코드로 돌아옵니다. 그 코드는 로그인을 시작한 이 기기의
// verifier 가 있어야만 토큰으로 바꿀 수 있어, 남이 보낸 콜백 링크로는 로그인되지 않습니다.
// 앱에서는 시스템 브라우저에서 로그인한 뒤 WebView 로 돌아오므로 탭 단위인 sessionStorage 대신 localStorage 에 둡니다.
const VERIFIER_KEY="toogeduler_oauth_verifier";

const base64url=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");

export async function startOAuth(provider:"kakao"|"google"){
 const verifier=base64url(crypto.getRandomValues(new Uint8Array(32)));
 const challenge=base64url(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(verifier))));
 localStorage.setItem(VERIFIER_KEY,verifier);
 const client=Capacitor.isNativePlatform()?"mobile":"web";
 window.location.assign(`${OAUTH_ORIGIN}/api/auth/oauth/${provider}?client=${client}&challenge=${challenge}`);
}

/** 한 번 꺼내면 지웁니다. 코드와 마찬가지로 verifier 도 한 번만 씁니다. */
export function takeVerifier(){const value=localStorage.getItem(VERIFIER_KEY);localStorage.removeItem(VERIFIER_KEY);return value}
