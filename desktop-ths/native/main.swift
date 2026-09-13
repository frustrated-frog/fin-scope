import AppKit
import ApplicationServices

// Read-only: no activation, navigation, writes, screenshots, or permission prompts.
let capturedAt = ISO8601DateFormatter().string(from: Date())
func emit(_ value: [String: Any]) {
    if let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]) {
        print(String(decoding: data, as: UTF8.self))
    }
}
func fail(_ status: String) -> Never {
    emit(["status": status, "capturedAt": capturedAt, "nodes": []])
    exit(0)
}
func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
    var value: CFTypeRef?
    guard AXUIElementCopyAttributeValue(element, name as CFString, &value) == .success else {
        return nil
    }
    return value
}
guard AXIsProcessTrusted() else { fail("PERMISSION_REQUIRED") }
guard let app = NSRunningApplication.runningApplications(withBundleIdentifier: "cn.com.10jqka.macstock").first else {
    fail("APP_NOT_RUNNING")
}
let root = AXUIElementCreateApplication(app.processIdentifier)
AXUIElementSetMessagingTimeout(root, 0.25)
var nodes: [[String: Any]] = []
var visited = Set<AXUIElement>()
var truncated = false
let started = Date()
func walk(_ element: AXUIElement, parent: Int?, depth: Int) {
    if nodes.count >= 6000 || depth > 30 || Date().timeIntervalSince(started) > 12 {
        truncated = true
        return
    }
    if visited.contains(element) { return }
    visited.insert(element)
    let role = attribute(element, "AXRole") as? String ?? ""
    if role == "AXMenu" { return }
    let id = nodes.count
    var node: [String: Any] = ["id": id, "role": role, "parent": parent as Any? ?? NSNull()]
    for (key, name) in [("title", "AXTitle"), ("value", "AXValue"), ("description", "AXDescription")] {
        if let value = attribute(element, name) as? String, !value.isEmpty {
            node[key] = String(value.prefix(3000))
        }
    }
    if role == "AXButton", let subrole = attribute(element, "AXSubrole") as? String {
        node["subrole"] = subrole
    }
    node["selected"] = role == "AXRow" && ((attribute(element, "AXSelected") as? Bool) ?? false)
    nodes.append(node)
    var childElements = attribute(element, "AXChildren") as? [AXUIElement] ?? []
    if role == "AXTable" {
        // Only the selected stock, heading row and up to five seat rows are needed.
        let selected = attribute(element, "AXSelectedRows") as? [AXUIElement] ?? []
        var rows: [AXUIElement] = []
        var headings: [AXUIElement] = []
        for child in childElements {
            if attribute(child, "AXRole") as? String == "AXRow" { rows.append(child) }
            else { headings.append(child) }
        }
        childElements = headings + (selected.isEmpty ? Array(rows.prefix(5)) : Array(rows.prefix(1)) + selected)
    }
    for child in childElements {
        walk(child, parent: id, depth: depth + 1)
    }
}
// THS 5.3.4 can omit its main window from AXWindows and AXChildren.
// Try read-only hit testing of THS window bounds; accept only non-menu roots.
let windows = CGWindowListCopyWindowInfo([.optionAll, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
var roots: [AXUIElement] = attribute(root, "AXWindows") as? [AXUIElement] ?? []
for window in windows where window[kCGWindowOwnerPID as String] as? Int == Int(app.processIdentifier) {
    guard window[kCGWindowLayer as String] as? Int == 0,
          let bounds = window[kCGWindowBounds as String] as? NSDictionary,
          let rect = CGRect(dictionaryRepresentation: bounds), rect.width >= 600, rect.height >= 400 else { continue }
    var hit: AXUIElement?
    if AXUIElementCopyElementAtPosition(root, Float(rect.midX), Float(rect.midY), &hit) == .success, let hit = hit {
        if let top = attribute(hit, "AXTopLevelUIElement"), CFGetTypeID(top) == AXUIElementGetTypeID() {
            let candidate = unsafeBitCast(top, to: AXUIElement.self)
            let candidateRole = attribute(candidate, "AXRole") as? String ?? ""
            if candidateRole != "AXMenuBar" && candidateRole != "AXApplication" {
                roots.append(candidate)
            }
        } else {
            roots.append(hit)
        }
    }
    if roots.count >= 3 { break }
}
if roots.isEmpty { fail("NO_WINDOW") }
for window in roots { walk(window, parent: nil, depth: 0) }
emit(["status": nodes.count > 1 ? "OK" : "NO_WINDOW", "capturedAt": capturedAt,
      "nodes": nodes, "truncated": truncated])
