import SwiftUI

/// A segmented choice with pixel-font labels; the selected segment is amber.
struct PixelSegmented<Value: Hashable>: View {
    struct Option {
        let value: Value
        let title: String
        var spoken: String?
    }

    let options: [Option]
    let selection: Value?
    let onSelect: (Value) -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // At accessibility sizes the segments stack, so labels never get cut.
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 4)) : AnyLayout(HStackLayout(spacing: 4))
        layout {
            ForEach(options, id: \.value) { option in
                let selected = option.value == selection
                Button {
                    onSelect(option.value)
                } label: {
                    PixelText(option.title, scale: 2, color: selected ? Palette.accentInk : Palette.text, centered: true)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(selected ? Palette.accent : Color.clear, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(option.spoken ?? option.title))
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Palette.background, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }
}
