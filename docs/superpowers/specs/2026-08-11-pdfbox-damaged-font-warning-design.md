# PDFBox 损坏字体告警收敛设计

## 背景

产业链异步研究会通过 `DefaultResearchSourceReader` 使用 PDFBox 3.0.7 抽取 PDF 文本。部分外部 PDF 内嵌的 Cambria Math 子集字体缺少 OpenType 必需的 `head` 表，PDFBox 因而由 `PDCIDFontType2` 输出包含完整堆栈的 WARN 日志。

PDFBox 会在该分支捕获字体解析异常，将内嵌字体标记为损坏，并寻找替代字体继续处理。该日志不表示 PDF 文档加载或文本抽取必然失败；真正的抽取失败仍会由 FinScope 的研究证据降级链路处理。

## 目标

- 消除 `PDCIDFontType2` 对可恢复损坏字体产生的 WARN 堆栈噪声。
- 保持 PDFBox 的字体替代和文本抽取行为不变。
- 保留 PDFBox ERROR 日志以及 FinScope 自身真正的 PDF 抽取失败处理。

## 非目标

- 不修复或重写外部 PDF 的内嵌字体数据。
- 不引入第二套 PDF 解析器、OCR 或自定义字体映射。
- 不改变研究证据的全文、摘要降级与状态语义。

## 方案

在 `finscope-web` 的 `application.yml` 中，将日志类别
`org.apache.pdfbox.pdmodel.font.PDCIDFontType2` 配置为 `ERROR`。

该类别范围仅对应 PDFBox 的 CID TrueType 字体实现。配置不会改变 PDFBox 控制流，也不会吞掉从 `DefaultResearchSourceReader` 抛出的解析异常；它只阻止该类别低于 ERROR 的第三方日志输出。

不采用自定义 Logback Filter，因为为一条可按类别隔离的第三方告警增加过滤器、配置文件和生命周期代码会扩大维护面。不采用替换 PDF 解析器或 OCR，因为现有异常属于 PDFBox 已处理的字体回退路径。

## 测试

新增 Web 模块配置测试，只读取 `application.yml` 的 `logging` 片段并断言 `PDCIDFontType2` 的日志级别为 `ERROR`。测试不使用会在 DEBUG 日志中展开整份 YAML 的加载器，避免固定凭据进入测试输出。

测试按 TDD 执行：先在未增加配置时运行并观察断言失败，再添加最小配置使测试通过。随后运行 `finscope-web` 相关测试及全部后端测试，确认配置加载和既有行为没有回归。

## 风险与边界

该类的其他 WARN 日志也会被收敛，因此排查特定 PDF 字体替代问题时，需要临时将该日志类别恢复为 WARN。PDFBox ERROR 级别事件和应用层解析失败仍会保留，业务失败不会因此被伪装为成功。
