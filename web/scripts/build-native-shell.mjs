// Capacitor 네이티브 셸에 함께 묶이는 로컬 오프라인 안내 페이지를 생성합니다.
// 원격 운영 사이트 로딩이 실패하면 WebView 가 흰 화면 대신 이 페이지를 보여줍니다.
// (capacitor.config.ts 의 server.errorPath 로 연결됩니다.)
import {mkdir, writeFile} from "node:fs/promises";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const serverUrl = process.env.CAPACITOR_SERVER_URL;

if (!serverUrl || !serverUrl.startsWith("https://")) {
  console.error(
    "CAPACITOR_SERVER_URL 이 https:// 주소로 설정되어야 합니다.\n" +
      "  예) CAPACITOR_SERVER_URL=https://app.toogeduler.com npm run mobile:sync"
  );
  process.exit(1);
}

const target = JSON.stringify(serverUrl);
const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"/>
<title>Toogeduler</title>
<style>
  :root{color-scheme:light dark}
  *{box-sizing:border-box}
  html,body{height:100%;margin:0}
  body{display:grid;place-items:center;padding:calc(24px + env(safe-area-inset-top)) 24px calc(24px + env(safe-area-inset-bottom));
    font:400 15px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;
    background:#f7fbfb;color:#0b2637}
  main{max-width:340px;text-align:center}
  .mark{width:64px;height:64px;margin:0 auto 22px;border-radius:19px;display:grid;place-items:center;
    background:linear-gradient(140deg,#19b7b1,#168cf2);box-shadow:0 12px 28px #19b7b133}
  .mark svg{width:34px;height:34px}
  h1{margin:0 0 10px;font-size:20px;font-weight:800;letter-spacing:-.01em}
  p{margin:0 0 26px;color:#4c6b7a}
  button{appearance:none;border:0;width:100%;padding:15px 20px;border-radius:14px;cursor:pointer;
    font:inherit;font-weight:700;color:#fff;background:#19b7b1}
  button:active{transform:translateY(1px)}
  small{display:block;margin-top:18px;color:#8aa3af;font-size:12px;word-break:break-all}
  @media (prefers-color-scheme:dark){
    body{background:#08181f;color:#e8f3f5}
    p{color:#96b2bd}
    small{color:#5f7d89}
  }
</style>
</head>
<body>
<main>
  <div class="mark" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
      <rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 11h18"/>
    </svg>
  </div>
  <h1>연결할 수 없어요</h1>
  <p>네트워크 상태를 확인한 뒤 다시 시도해 주세요.</p>
  <button type="button" id="retry">다시 시도</button>
  <small id="host"></small>
</main>
<script>
  var target = ${target};
  try { document.getElementById("host").textContent = new URL(target).host; } catch (e) {}
  document.getElementById("retry").addEventListener("click", function () {
    window.location.replace(target);
  });
  // 온라인 상태가 회복되면 자동으로 복귀합니다.
  window.addEventListener("online", function () {
    window.location.replace(target);
  });
</script>
</body>
</html>
`;

const dir = resolve(root, "native-shell");
await mkdir(dir, {recursive: true});
await writeFile(resolve(dir, "error.html"), html, "utf8");
await writeFile(resolve(dir, "index.html"), html, "utf8");
console.log("native-shell/error.html 생성 완료 (" + serverUrl + ")");
