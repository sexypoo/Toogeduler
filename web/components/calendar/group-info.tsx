"use client";
import { ColorPicker } from "@/components/color-picker";
import { ApiError, api } from "@/lib/api";
import { GROUP_NAME_MAX, groupPatch } from "@/lib/group-edit";
import type { Group } from "@/lib/types";
import { useRef, useState } from "react";

// 그룹 관리 서랍의 "그룹 정보" 구역. 그룹장에게만 렌더링하며, 저장해도 서랍은 닫지 않습니다(초대·멤버 관리를 이어서 할 수 있게).
export function GroupInfo({
  group,
  close,
  show,
  reload,
}: {
  group: Group;
  close: () => void;
  show: (m: string) => void;
  reload: () => Promise<void>;
}) {
  const [name, setName] = useState(group.name);
  const [color, setColor] = useState(group.color);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const patch = groupPatch(group, name, color);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!patch) return;
    setSaving(true);
    setError("");
    try {
      await api(`/api/groups/${group.id}`, { method: "PATCH", body: JSON.stringify(patch) });
      setName(name.trim());
      await reload();
      show("그룹 정보를 바꿨어요.");
      // 저장 버튼은 다시 disabled 가 되므로 포커스를 이름 입력으로 돌립니다.
      input.current?.focus();
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) setError(e.message);
      else if (e instanceof ApiError && e.status === 403) {
        // 그 사이 그룹장이 바뀌었으면 다시 불러와 이 구역을 감춥니다.
        setError("그룹장만 바꿀 수 있어요.");
        await reload().catch(() => undefined);
      } else if (e instanceof ApiError && e.status === 404) {
        show("그룹을 찾을 수 없어요.");
        await reload().catch(() => undefined);
        close();
      } else setError("저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <form className="group-info" onSubmit={save}>
        <div className="settings-title">
          <label htmlFor="group-info-name">그룹 이름</label>
          <span>
            {name.length}/{GROUP_NAME_MAX}
          </span>
        </div>
        <input
          id="group-info-name"
          ref={input}
          maxLength={GROUP_NAME_MAX}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="예: 주말 탐험대"
        />
        <fieldset>
          <legend>그룹 색상</legend>
          <ColorPicker value={color} onChange={setColor} />
        </fieldset>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <button className="primary full" disabled={!patch || saving}>
          {saving ? "저장하는 중..." : "저장"}
        </button>
      </form>
    </section>
  );
}
