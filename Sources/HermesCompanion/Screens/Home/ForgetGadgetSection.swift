import GadgetKit
import SwiftUI

/// Forget this gadget (`phone forget`), after a confirmation that says what it does.
struct ForgetGadgetSection: View {
    @Environment(GadgetStore.self) private var store
    let name: String

    @State private var confirming = false
    @State private var isForgetting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            GadgetButton("Forget \(name)", kind: .destructive, isBusy: isForgetting) {
                confirming = true
            }
            .disabled(isForgetting || store.connectionLost)
            if let error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
            }
        }
        .confirmationDialog("Forget \(name)?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Forget \(name)", role: .destructive) { forget() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("\(name) removes every phone paired with it, not only this iPhone, and disconnects. To use the app with it again, open Phone app on the gadget and pair.")
        }
    }

    private func forget() {
        isForgetting = true
        error = nil
        Task {
            error = await store.forget()
            isForgetting = false
        }
    }
}
