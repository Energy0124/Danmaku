// Deterministic vector artwork rendered into the App Store icon asset.
import AppKit
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024,
                            bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false,
                            isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
let background = NSGradient(starting: NSColor(red: 0.16, green: 0.12, blue: 0.48, alpha: 1),
                            ending: NSColor(red: 0.38, green: 0.30, blue: 0.94, alpha: 1))!
background.draw(in: NSRect(x: 0, y: 0, width: 1024, height: 1024), angle: 45)
NSColor.white.setFill()
let play = NSBezierPath()
play.move(to: NSPoint(x: 420, y: 320)); play.line(to: NSPoint(x: 420, y: 704)); play.line(to: NSPoint(x: 720, y: 512)); play.close(); play.fill()
NSColor.white.withAlphaComponent(0.7).setFill()
for (x, y, width) in [(180.0, 770.0, 300.0), (260, 220, 250), (150, 470, 160)] {
    NSBezierPath(roundedRect: NSRect(x: x, y: y, width: width, height: 40), xRadius: 20, yRadius: 20).fill()
}
NSGraphicsContext.restoreGraphicsState()
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
