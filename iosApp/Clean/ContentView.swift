import SwiftUI
import Shared

/// Hosts the Compose screen. It draws under the status bar and the home indicator itself (the Kotlin side reads the safe areas), as the Android app is edge to edge.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(platform: IosHost.shared.platform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .ignoresSafeArea(.keyboard)
            // clean:// links, and a templates file opened with Clean from Files or a share sheet.
            .onOpenURL { url in
                IosHost.shared.handleUrl(url: url)
            }
    }
}
