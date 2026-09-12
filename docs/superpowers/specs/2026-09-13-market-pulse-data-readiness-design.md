# 主题行情补齐与日频缓存

承接用户批准的下一批：主题成员行情自动补齐、日频结果缓存，并完成真实运行验收。范围不含风格矩阵、盘中雷达或自动生成未经核实的主题关系。

## 行为与契约

1. 打开今日雷达/保存主题后，读取本浏览器维护的去重A股成员名单（最多5×50只）；后台有界并发2逐只补齐，显示进度与每只状态。跨主题重复代码只处理一次。日频样本成功重算后更新主题和股票组；日期切换/卸载取消旧队列和丢弃旧结果。失败允许重试，不自动无限循环。仅补齐主题成员，不加入自选、不补全全市场。
2. Java POST /api/market-pulse/research/{businessDate}/members/{instrumentCode} 转发 Python POST /v1/markets/CN-A/daily-research/members/{instrumentCode}?business_date=ISO。代码严格A股含市场后缀；日期为已收盘交易日，非法范围不触发采集。
3. 逐股响应 schema_version=research-member-v1（Python）、business_date、instrument_code、status=READY/PARTIAL/FAILED/SKIPPED、reason=COMPLETE/NO_DATA/DATE_MISSING/HISTORY_GAP/ADJUSTMENT_REQUIRED/NOT_CLOSED/UPSTREAM_FAILED、message、valid_bars（0..22）、required_bars=22、source_code（可空）。先检查本地连续22日QFQ；够用直接返回READY。否则复用ProviderRouter补齐至少250根日K，不能用旧日期冒充；单股超时60秒、并发2、有界失败冷却，重复请求共用正在执行的调用。供应商失败/历史不完整分别报告。
4. 日频快照缓存放Python侧，按交易日、算法版本和日K修订号命中。日K写入/删除原子递增修订号（同秒修改也有效），报价等非日K更新不失效。计算前后修订号不同不保存为可命中的新版本。日期合法性每次检查，盘前UNAVAILABLE不能越过收盘边界复用。缓存最多20个日期，返回副本避免调用方污染。相同日期并发请求合并扫描；进程重启可读取持久缓存。可选cache_hit/calculated_at元数据透传界面，不改变旧消费者契约。
5. 页面缓存命中显示生成时间；补齐结果展示明确缺失原因和有效连续日数，不把远程请求成功当作覆盖完整。保留已有历史成员生效边界。日K更新后下一次日频读取自然失效重算。

## 验收

重复读取不再扫描；同秒日K写入、删除、其他进程写入后失效；非日K保存不失效；重启命中；并发同键仅算一次；异常不入缓存；日期边界关闭；容量有限。补齐覆盖已完整不联网、成功/部分/异常/超时/并发去重、停牌/历史缺口、复权不足、非法代码/未来日、失败冷却。前端覆盖初次自动、保存新增、去重、错误重试和日期竞态。真实库测冷/热耗时、真实成员数据链路与浏览器检查；不覆盖原有未提交配置。
