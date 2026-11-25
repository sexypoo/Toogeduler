import AuthenticationServices
import Capacitor
import UIKit

/// 소셜 로그인을 앱 안의 로그인 시트(ASWebAuthenticationSession)에서 진행합니다.
///
/// WebView 가 API 주소로 이동하면 Capacitor 는 그 주소를 Safari 로 넘깁니다. 그러면 로그인은 Safari 에서 끝나고,
/// 마지막 `toogeduler://` 복귀는 Safari 가 막거나(사용자 동작 없는 리다이렉트, 카카오톡 앱을 다녀온 뒤 등) 묻는 창을 띄워
/// 사용자가 앱으로 돌아오지 못한 채 웹에 남게 됩니다. 로그인 시트는 콜백 스킴을 직접 받아 앱으로 돌려주므로 이 문제가 없습니다.
@objc(NativeAuthPlugin)
public class NativeAuthPlugin: CAPPlugin, CAPBridgedPlugin, ASWebAuthenticationPresentationContextProviding {
    public let identifier = "NativeAuthPlugin"
    public let jsName = "NativeAuth"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise)
    ]

    private var session: ASWebAuthenticationSession?

    @objc func start(_ call: CAPPluginCall) {
        guard let raw = call.getString("url"), let url = URL(string: raw), url.scheme == "https" else {
            call.reject("https 로 시작하는 url 이 필요합니다.")
            return
        }
        guard let scheme = call.getString("callbackScheme"), !scheme.isEmpty else {
            call.reject("callbackScheme 이 필요합니다.")
            return
        }
        DispatchQueue.main.async {
            self.session?.cancel()
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: scheme) { [weak self] callback, error in
                self?.session = nil
                if let callback {
                    call.resolve(["url": callback.absoluteString])
                } else if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    call.reject("로그인을 취소했어요.", "CANCELED")
                } else {
                    call.reject(error?.localizedDescription ?? "로그인을 마치지 못했어요.", "FAILED")
                }
            }
            session.presentationContextProvider = self
            // Safari 의 카카오·구글 로그인 상태를 이어 써서 매번 아이디를 다시 입력하지 않게 합니다.
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            if !session.start() {
                self.session = nil
                call.reject("로그인 창을 열지 못했어요.", "FAILED")
            }
        }
    }

    public func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        bridge?.viewController?.view.window ?? ASPresentationAnchor()
    }
}

/// 앱에만 있는 플러그인을 등록하는 브리지 화면입니다.
class AppViewController: CAPBridgeViewController {
    override open func capacitorDidLoad() {
        bridge?.registerPluginInstance(NativeAuthPlugin())
    }
}
