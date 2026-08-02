"use client";
import {Suspense,useEffect} from "react";import {useRouter,useSearchParams} from "next/navigation";
function CallbackContent(){const router=useRouter(),params=useSearchParams();useEffect(()=>{const value=params.get("token");if(value){localStorage.setItem("toogeduler_token",value);const invite=sessionStorage.getItem("pending_invite");if(invite){sessionStorage.removeItem("pending_invite");router.replace(`/invite/${invite}`)}else router.replace("/")}else router.replace("/login")},[params,router]);return <main className="center-state">로그인을 마무리하고 있어요…</main>}
export default function Callback(){return <Suspense fallback={<main className="center-state">로그인을 마무리하고 있어요…</main>}><CallbackContent/></Suspense>}
