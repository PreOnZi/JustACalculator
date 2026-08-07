// Renders the iOS launch screen artwork: the app icon with rounded corners and
// the word LOADING beneath it, in the calculator's own font.
//
// Why this exists rather than a storyboard label:
//
//  * A launch screen is composited before the app process registers UIAppFonts,
//    so a UILabel asking for CalcDigit silently falls back to the system font.
//    Verified on the simulator — a built-in font renders, CalcDigit does not.
//  * Everything is baked into ONE image, so the storyboard has a single image
//    view. Nothing here is dynamic anyway; a launch screen is a still frame.
//
// Run from the repo root:
//   xcrun swift tools/render-launch-art.swift \
//       iconios.png \
//       app/src/commonMain/composeResources/font/calcdigit.ttf \
//       iosApp/iosApp/Assets.xcassets/LaunchArt.imageset
//
// Re-run it whenever the icon or the font changes.

import Foundation
import CoreText
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let iconPath = CommandLine.arguments[1]
let fontPath = CommandLine.arguments[2]
let outDir   = CommandLine.arguments[3]

// Layout, in points.
let iconSide: CGFloat  = 120
let gap: CGFloat       = 24
let ptSize: CGFloat    = 24
let cornerRatio: CGFloat = 0.2237   // Apple's icon corner radius, as a fraction of the side
let ink = CGColor(red: 45/255, green: 45/255, blue: 45/255, alpha: 1)  // #2D2D2D, the calculator's dark grey

func die(_ msg: String) -> Never {
    FileHandle.standardError.write("render-launch-art: \(msg)\n".data(using: .utf8)!)
    exit(1)
}

// MARK: font

guard CTFontManagerRegisterFontsForURL(URL(fileURLWithPath: fontPath) as CFURL, .process, nil) else {
    die("could not register \(fontPath)")
}
let font = CTFontCreateWithName("CalcDigit" as CFString, ptSize, nil)
// Fail loudly at build time rather than silently shipping a fallback font.
guard CTFontCopyPostScriptName(font) as String == "CalcDigit" else {
    die("font registered but resolved to \(CTFontCopyPostScriptName(font) as String)")
}

let line = CTLineCreateWithAttributedString(NSAttributedString(string: "LOADING", attributes: [
    NSAttributedString.Key(kCTFontAttributeName as String): font,
    NSAttributedString.Key(kCTForegroundColorAttributeName as String): ink,
    NSAttributedString.Key(kCTKernAttributeName as String): 2.0,
]))
var ascent: CGFloat = 0, descent: CGFloat = 0, leading: CGFloat = 0
let textW = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, &leading))
let textH = ceil(ascent + descent)

// MARK: icon

guard let iconSrc = CGImageSourceCreateWithURL(URL(fileURLWithPath: iconPath) as CFURL, nil),
      let icon = CGImageSourceCreateImageAtIndex(iconSrc, 0, nil) else {
    die("could not read \(iconPath)")
}

// MARK: compose

let canvasW = ceil(max(iconSide, textW)) + 8   // slack so kerning never clips
let canvasH = iconSide + gap + textH

for scale in [1, 2, 3] {
    let pw = Int(canvasW) * scale, ph = Int(canvasH) * scale
    guard let ctx = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0,
                              space: CGColorSpaceCreateDeviceRGB(),
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
        die("could not create a \(pw)x\(ph) context")
    }
    ctx.scaleBy(x: CGFloat(scale), y: CGFloat(scale))
    ctx.setAllowsAntialiasing(true)
    ctx.setShouldSmoothFonts(true)

    // CoreGraphics is y-up, so the icon sits at the TOP of the canvas.
    let iconRect = CGRect(x: (canvasW - iconSide) / 2, y: canvasH - iconSide, width: iconSide, height: iconSide)
    ctx.saveGState()
    let r = iconSide * cornerRatio
    ctx.addPath(CGPath(roundedRect: iconRect, cornerWidth: r, cornerHeight: r, transform: nil))
    ctx.clip()
    ctx.draw(icon, in: iconRect)
    ctx.restoreGState()

    ctx.textPosition = CGPoint(x: (canvasW - textW) / 2, y: descent)
    CTLineDraw(line, ctx)

    guard let img = ctx.makeImage() else { die("could not snapshot the context") }
    let suffix = scale == 1 ? "" : "@\(scale)x"
    let url = URL(fileURLWithPath: "\(outDir)/LaunchArt\(suffix).png")
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        die("could not open \(url.path) for writing")
    }
    CGImageDestinationAddImage(dest, img, nil)
    guard CGImageDestinationFinalize(dest) else { die("could not write \(url.path)") }
    print("  \(url.lastPathComponent)  \(pw)x\(ph)")
}
print("  logical size: \(Int(canvasW))x\(Int(canvasH)) pt")
