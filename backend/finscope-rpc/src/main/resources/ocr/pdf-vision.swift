import Foundation
import AppKit
import PDFKit
import Vision

// Local PDF bytes only. No URLs, shell commands or network requests are accepted.
guard CommandLine.arguments.count == 3,
      let limit = Int(CommandLine.arguments[2]), limit > 0,
      let document = PDFDocument(url: URL(fileURLWithPath: CommandLine.arguments[1])),
      document.pageCount > 0, document.pageCount <= limit else {
    exit(64)
}
do {
var pages: [String] = []
for index in 0..<document.pageCount {
    let text: String = try autoreleasepool {
        guard let page = document.page(at: index) else { exit(65) }
        let bounds = page.bounds(for: .mediaBox)
        let scale = min(3.0, 2600.0 / max(bounds.width, bounds.height))
        let thumbnail = page.thumbnail(of: NSSize(width: bounds.width * scale, height: bounds.height * scale), for: .mediaBox)
        guard let image = thumbnail.cgImage(forProposedRect: nil, context: nil, hints: nil) else { exit(65) }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["zh-Hans", "en-US"]
        request.usesLanguageCorrection = false
        try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        return (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
    }
    pages.append(text)
}
let result = pages.joined(separator: "\n\n")
guard result.count >= 20, let bytes = result.data(using: .utf8), bytes.count <= 1_048_576 else { exit(65) }
FileHandle.standardOutput.write(bytes)

} catch {
    FileHandle.standardError.write(Data("Local PDF OCR failed\n".utf8))
    exit(66)
}
