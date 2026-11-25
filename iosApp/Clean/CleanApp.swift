import SwiftUI
import Shared

/// The iPhone app: one SwiftUI window around the Kotlin screen (`MainViewController`, in the `Shared` framework).
@main
struct CleanApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Launch work that has to happen before the app has finished launching (the notification delegate and the background task), and the orientations the screen may take.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        IosHost.shared.didFinishLaunching()
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }

    /// Portrait, except while a full-screen chart asks for landscape (`Platform.landscape`).
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        IosHost.shared.landscapeOn ? .landscape : .portrait
    }
}

/// Home-screen quick actions, which SwiftUI does not pass on.
final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem {
            IosHost.shared.handleShortcut(type: item.type)
        }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        IosHost.shared.handleShortcut(type: shortcutItem.type)
        completionHandler(true)
    }
}
