// 앱 아이콘과 같은 생각: 두 사람의 시간을 투명한 필름 두 장으로 겹치면, 겹친 곳이 진해집니다.
export function BrandMark(){
 return <span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 40 40"><rect x="5" y="5" width="20" height="20" rx="5" fill="var(--film-cyan)"/><rect x="15" y="15" width="20" height="20" rx="5" fill="var(--film-magenta)" style={{mixBlendMode:"multiply"}}/></svg></span>
}
