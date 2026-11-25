import UIKit
import SwiftUI
import ComposeApp

// Minimal host. No UI is implemented in Swift.
@MainActor
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(
        context: UIViewControllerRepresentableContext<ComposeView>
    ) -> UIViewController {
        Main_iosKt.MainViewController()
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: UIViewControllerRepresentableContext<ComposeView>
    ) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
