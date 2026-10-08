"use client";
import {useState} from "react";
import type {User} from "@/lib/types";

const PERSON_COLORS=["#168CF2","#19B7B1","#8B6FF7","#F0648C","#F29E38"];
/** 같은 사람은 어느 화면에서나 같은 색이 되도록 사용자 id 로 색을 고릅니다. */
export const personColor=(id:number)=>PERSON_COLORS[Math.abs(id)%PERSON_COLORS.length];

/** 그룹 캘린더의 일정에 붙는 아주 작은 작성자 얼굴. 사진이 없거나 깨지면 이름 마지막 글자를 보여줍니다. */
export function OwnerAvatar({owner}:{owner:Pick<User,"id"|"name"|"avatarUrl">}){
 const[broken,setBroken]=useState(false);
 const photo=owner.avatarUrl&&!broken;
 return <span className="owner-avatar" title={`${owner.name}님의 일정`} aria-hidden="true" style={{background:photo?"#fff":personColor(owner.id)}}>{photo?<img src={owner.avatarUrl} alt="" loading="lazy" referrerPolicy="no-referrer" onError={()=>setBroken(true)}/>:owner.name.slice(-1)}</span>;
}
