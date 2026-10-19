"use client";
import { BrandMark } from "@/components/brand-mark";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, OAUTH_READY } from "@/lib/api";
import { startOAuth } from "@/lib/oauth";
const OAUTH_ERRORS: Record<string, string> = {
  email: "소셜 계정에서 이메일 제공에 동의해주세요.",
  email_unverified: "소셜 계정의 이메일이 아직 인증되지 않았어요. 이메일을 인증한 뒤 다시 시도해주세요.",
  password_account: "이 이메일은 비밀번호로 가입된 계정이에요. 이메일과 비밀번호로 로그인해주세요.",
  state: "로그인 요청이 만료되었어요. 이 화면에서 다시 시도해주세요.",
  expired: "로그인 링크가 만료되었어요. 다시 시도해주세요.",
};
// 이 기기에서 로그인한 적이 있다는 증표. 누군가 비밀번호를 일부러 틀려 계정을 잠가도 이 기기에서는 로그인할 수 있습니다.
// 로그아웃해도 지우지 않습니다(액세스 토큰과 달리 이것만으로는 아무 API 도 호출할 수 없습니다).
const DEVICE_KEY = "toogeduler_device";
export default function Login() {
  const demo = process.env.NEXT_PUBLIC_SHOW_DEMO === "true",
    router = useRouter();
  const [register, setRegister] = useState(false),
    [email, setEmail] = useState(demo ? "minji@toogeduler.app" : ""),
    [password, setPassword] = useState(demo ? "password123!" : ""),
    [name, setName] = useState(""),
    [error, setError] = useState(""),
    [show, setShow] = useState(false),
    [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get("deleted") === "1") {
      setNotice("계정이 삭제되었습니다. 그동안 이용해 주셔서 감사합니다.");
      return;
    }
    if (params.get("mode") === "register") setRegister(true);
    if (params.get("session") === "expired")
      setNotice("로그인이 만료되었어요. 다시 로그인하면 이어서 참여할 수 있어요.");
    const code = params.get("oauthError");
    if (code) setError(OAUTH_ERRORS[code] ?? `소셜 로그인에 실패했어요 (${code}). 잠시 후 다시 시도해주세요.`);
  }, []);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const deviceToken = localStorage.getItem(DEVICE_KEY);
      const result = await api<{ token: string; deviceToken?: string }>(
        `/api/auth/${register ? "register" : "login"}`,
        {
          method: "POST",
          body: JSON.stringify(register ? { email, password, name } : { email, password, deviceToken }),
        },
      );
      localStorage.setItem("toogeduler_token", result.token);
      if (result.deviceToken) localStorage.setItem(DEVICE_KEY, result.deviceToken);
      const invite = sessionStorage.getItem("pending_invite");
      if (invite) {
        sessionStorage.removeItem("pending_invite");
        router.push(`/invite/${invite}`);
      } else router.push("/");
    } catch (e) {
      setError(e instanceof Error ? e.message : "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }
  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand brand-light">
          <BrandMark />
          Toogeduler
        </div>
        <div className="story-copy">
          <h1>
            우리가 만날
            <br />
            시간을 한눈에.
          </h1>
          <p>
            각자의 일정은 필요한 만큼만 나누고,
            <br />
            모두가 되는 순간은 빠르게 찾으세요.
          </p>
        </div>
        <div className="hero-timeline" aria-hidden="true">
          <p>토요일 오후</p>
          <div className="ht-grid">
            <div className="ht-scale">
              {[1, 2, 3, 4, 5, 6, 7, 8].map((h) => (
                <span key={h}>{h}시</span>
              ))}
            </div>
            <div className="ht-row">
              <b>민지</b>
              <div className="ht-track">
                <i className="ht-film cyan" style={{ left: "0%", width: "57.14%" }}>
                  1–5시
                </i>
              </div>
            </div>
            <div className="ht-row">
              <b>서준</b>
              <div className="ht-track">
                <i className="ht-film magenta" style={{ left: "28.57%", width: "71.43%" }}>
                  3–8시
                </i>
              </div>
            </div>
            <div className="ht-row together">
              <b>함께</b>
              <div className="ht-track">
                <i className="ht-film both" style={{ left: "28.57%", width: "28.57%" }}>
                  3–5시
                </i>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section className="login-panel">
        <form onSubmit={submit}>
          <div className="mobile-brand">
            <BrandMark />
            Toogeduler
          </div>
          <h2>{register ? "새 계정 만들기" : "내 캘린더로 돌아가기"}</h2>
          <p className="muted">{register ? "몇 초면 준비가 끝나요." : "일정과 사람들을 이어서 확인하세요."}</p>
          {register && (
            <label>
              이름
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="어떻게 불러드릴까요?"
                required
                minLength={2}
              />
            </label>
          )}
          <label>
            이메일
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            비밀번호
            <span className="password-wrap">
              <input
                type={show ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
              <button type="button" onClick={() => setShow(!show)} aria-label="비밀번호 보기">
                {show ? <EyeOff /> : <Eye />}
              </button>
            </span>
          </label>
          {notice && <p className="form-notice">{notice}</p>}
          {error && <p className="form-error">{error}</p>}
          <button className="primary full" disabled={loading}>
            {loading ? "확인 중..." : register ? "계정 만들기" : "로그인"}
          </button>
          {OAUTH_READY && (
            <>
              <div className="or">
                <span>또는</span>
              </div>
              <div className="social-row">
                <button type="button" onClick={() => startOAuth("kakao")} className="social kakao">
                  <svg viewBox="0 0 18 18" aria-hidden="true">
                    <path
                      fill="#000"
                      d="M9 1.6C4.6 1.6 1 4.4 1 7.86c0 2.24 1.48 4.2 3.72 5.31l-.95 3.47c-.08.3.26.54.52.37l4.15-2.74c.18.02.37.03.56.03 4.4 0 8-2.8 8-6.27S13.4 1.6 9 1.6z"
                    />
                  </svg>
                  <span>카카오로 계속하기</span>
                </button>
                <button type="button" onClick={() => startOAuth("google")} className="social google">
                  <svg viewBox="0 0 48 48" aria-hidden="true">
                    <path
                      fill="#EA4335"
                      d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
                    />
                    <path
                      fill="#4285F4"
                      d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
                    />
                    <path
                      fill="#34A853"
                      d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
                    />
                  </svg>
                  <span>Google로 계속하기</span>
                </button>
              </div>
            </>
          )}
          <button type="button" className="switch-auth" onClick={() => setRegister(!register)}>
            {register ? "이미 계정이 있나요? 로그인" : "처음인가요? 계정 만들기"}
          </button>
          {demo && <p className="demo-hint">데모: minji@toogeduler.app / password123!</p>}
          <p className="legal-links">
            <a href="/terms">이용약관</a>
            <span aria-hidden="true">·</span>
            <a href="/privacy">개인정보처리방침</a>
          </p>
        </form>
      </section>
    </main>
  );
}
