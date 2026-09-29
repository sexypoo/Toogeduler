import type {CapacitorConfig} from '@capacitor/cli';

// 네이티브 셸은 검증된 운영 HTTPS 사이트를 로드합니다.
// 잘못된 주소로 빌드되어 흰 화면이 나가는 것을 막기 위해 주소를 반드시 명시하게 합니다.
//   CAPACITOR_SERVER_URL=https://app.example.com npx cap sync
const serverUrl=process.env.CAPACITOR_SERVER_URL;
if(!serverUrl){
  throw new Error(
    'CAPACITOR_SERVER_URL 이 필요합니다. 운영 웹 주소를 지정한 뒤 다시 실행하세요.\n' +
    '  예) CAPACITOR_SERVER_URL=https://app.toogeduler.com npm run mobile:sync'
  );
}
if(!serverUrl.startsWith('https://')){
  throw new Error('CAPACITOR_SERVER_URL 은 https:// 로 시작해야 합니다. App Store 는 평문 HTTP 로딩을 허용하지 않습니다.');
}

const config: CapacitorConfig = {
  appId: 'app.toogeduler.mobile',
  appName: 'Toogeduler',
  // 원격 사이트를 로드하더라도 Capacitor 는 로컬 webDir 이 필요합니다.
  // 이 디렉터리의 오프라인 안내 페이지가 네트워크 실패 시 흰 화면을 대신합니다.
  webDir: 'native-shell',
  server: {
    url: serverUrl,
    androidScheme: 'https',
    // 원격 사이트 로딩 실패 시 흰 화면 대신 번들된 안내 페이지를 보여줍니다.
    errorPath: 'error.html'
  }
};

export default config;
