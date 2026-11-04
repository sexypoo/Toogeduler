"use client";
import { COLOR_NAMES, EVENT_COLORS } from "@/lib/colors";
import { Check } from "lucide-react";

// 그룹 만들기와 그룹 관리 서랍이 함께 쓰는 큰 색상 팔레트. 선택은 체크 아이콘과 테두리로도 보여 색에만 의존하지 않습니다.
export function ColorPicker({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  return (
    <div className="large-color-picker">
      {EVENT_COLORS.map((c) => {
        const selected = value.toUpperCase() === c;
        return (
          <button
            type="button"
            aria-label={COLOR_NAMES[c]}
            aria-pressed={selected}
            key={c}
            className={selected ? "selected" : ""}
            style={{ background: c }}
            onClick={() => onChange(c)}
          >
            {selected && <Check />}
          </button>
        );
      })}
    </div>
  );
}
