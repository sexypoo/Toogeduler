"use client";
import {useEffect,useId,useRef} from "react";

const FOCUSABLE='a[href],button:not([disabled]),input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

/**
 * 모달·서랍·팝오버가 함께 쓰는 키보드 처리.
 * - Escape 로 닫습니다.
 * - modal 이면 Tab 포커스가 창 밖으로 빠져나가지 않게 가둡니다(팝오버는 가두지 않음).
 * - 열릴 때 창 안으로 포커스를 옮기고, 닫히면 열기 전에 있던 버튼으로 돌려줍니다.
 * 돌려준 ref 는 창의 바깥 요소에, titleId 는 제목(h2)의 id 와 aria-labelledby 에 씁니다.
 */
export function useDialog<T extends HTMLElement>(onClose:()=>void,{modal=true}:{modal?:boolean}={}){
 const ref=useRef<T>(null);const titleId=useId();
 const close=useRef(onClose);close.current=onClose;
 // 열기 직전에 포커스가 있던 요소. 창 안의 autoFocus 가 먼저 포커스를 가져가므로 첫 렌더 때 기억해 둔다.
 const opener=useRef<HTMLElement|null|undefined>(undefined);
 if(opener.current===undefined&&typeof document!=="undefined")opener.current=document.activeElement instanceof HTMLElement?document.activeElement:null;
 useEffect(()=>{
  const node=ref.current;if(!node)return;
  const previous=opener.current;
  if(!node.contains(document.activeElement))(node.querySelector<HTMLElement>(FOCUSABLE)??node).focus({preventScroll:true});
  function onKey(e:KeyboardEvent){
   if(e.key==="Escape"){e.preventDefault();close.current();return}
   if(!modal||e.key!=="Tab"||!node)return;
   const items=[...node.querySelectorAll<HTMLElement>(FOCUSABLE)].filter(x=>x.offsetParent!==null);
   if(!items.length){e.preventDefault();return}
   const first=items[0],last=items[items.length-1];
   if(e.shiftKey&&(document.activeElement===first||!node.contains(document.activeElement))){e.preventDefault();last.focus()}
   else if(!e.shiftKey&&(document.activeElement===last||!node.contains(document.activeElement))){e.preventDefault();first.focus()}
  }
  document.addEventListener("keydown",onKey);
  return()=>{document.removeEventListener("keydown",onKey);if(previous&&document.contains(previous))previous.focus({preventScroll:true})};
 },[modal]);
 return {ref,titleId};
}
