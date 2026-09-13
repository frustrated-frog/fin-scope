# 同花顺页面解读（第一版）

FinScope 左侧「同花顺解读」提供手动读取、当前股票卡、龙虎榜原始字段、席位原文、固定阅读指南及研究问题预填。页面入口：`http://localhost:5173/?view=desktopThs`。

## 启动

在 Mac 宿主机的项目根目录运行：

```sh
./desktop-ths/start.sh
```

需要系统 Swift 编译器和 Python 3，无第三方 Python 依赖。首次启动编译到被 Git 忽略的 `.build/ths-reader`，侧车仅监听 `127.0.0.1:18765`。保持该终端运行，Ctrl+C 停止。首次使用若提示辅助功能权限，请在系统设置中允许运行读取程序的终端；程序不会主动弹出授权或修改权限。

按原有方式启动 Java 后端和 Vite 前端。Java 默认访问 `http://127.0.0.1:18765`，属性为 `finscope.desktop-ths.base-url`。当前版本仅验证宿主机本地运行；不要把读取程序放进 Linux 容器。Docker 网络接入与远程部署不在本版范围。

可先点击「查看实测示例」预览完整界面。该样本来自 2026-09-14 的桌面工具读取，记录时间取页面时钟，榜单日期为 2026-09-11；界面明确标注非当前读取，不会作为实时读取的自动回退。

在同花顺打开龙虎榜并选中股票，展开主窗口；在 FinScope 点击「读取当前页面」。读取期间保持页面不变。应用窗口被关闭、最小化、处于另一桌面或未向原生接口暴露时，可能无法读取。页面明确显示失败，不会拿旧快照冒充新结果。

## 边界

- 只读同花顺的 Accessibility 属性；不截图、不导航、不读取账号配置、不交易。
- 手动一次调用最多 15 秒；限制节点数、深度、字段长度。没有后台轮询和自动模型调用。
- 只对完整对应的表头与选中行读取净买入；列数不一致时留空。
- 当前股票栏的价格时间未暴露，不能视为榜单日期的价格。保留榜单日期与采集时间。
- 席位明细保留页面原文，未可靠拆分的数值不冒充结构化买卖金额。
- 快照暂存在页面内存，离开页面即清除。继续研究会把固定快照预填到研究问题中，正式保存和执行仍走现有研究流程。
- `OK` 仅代表本版所需字段已获取，不保证全量数据或实时行情；`PARTIAL` 表示缺失字段。
- 本地侧车固定目标应用和端点，拒绝跨站 Origin 与不合法 Host；不提供任意命令执行接口。

## 测试

```sh
cd desktop-ths
python3 -m unittest discover -s tests -v
```

前端：`cd frontend && npm test -- --run src/features/desktop-ths/ThsDesktopView.test.tsx src/app/AppShell.test.tsx`。

后端（Java 21）：`mvn -f backend/pom.xml -pl finscope-web -am -Dtest=ThsDesktopClientTest,ThsDesktopControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`。

## 本机验证记录

2026-09-14：Swift 编译通过；Python 解析/错误分支、Java RPC/Controller、React 手动读取/研究预填/失败清空均已测试。前端生产构建通过。

同一台 Mac 的桌面工具已读取龙虎榜并与实测示例逐项核对。独立原生进程有一次取得 880 个真实节点和榜单日期，但在全表扫描达到时限前未取得当前股票；已优化为表头、选中行与前五席位读取。其余尝试因窗口未向原生接口暴露而返回 `NO_WINDOW`。独立程序成功读取完整当前股票的端到端验收尚未完成，不能将单元测试或桌面工具成功视作独立实时采集已验收。
