import SwiftUI
import TrainingApp

final class AppModel: ObservableObject {
    let store = SQLiteDocumentStore()
    lazy var bridge = IosAppBridge(store: store)
}

@main
struct PersonalTrainingApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup { TrainingRoot(model: model).ignoresSafeArea().preferredColorScheme(.light) }
    }
}

struct TrainingRoot: UIViewControllerRepresentable {
    let model: AppModel
    func makeUIViewController(context: Context) -> UIViewController {
        model.bridge.makeViewController { PoseCameraController(bridge: model.bridge) }
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
