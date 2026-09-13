/** Recorded through the desktop accessibility tool on 2026-09-14.
 * Time comes from the THS page clock, not a live market timestamp.
 * Kept separate from the native capture path and visibly marked in the UI.
 */
export const recordedExample = {
  status: 'OK', message: '实测示例 · 非当前读取', source: 'THS_DESKTOP_AX_RECORDED',
  capturedAt: '2026-09-13T16:16:05Z', dataDate: '2026-09-11',
  stockCode: '605069', stockName: '正和生态', price: '11.50', changePct: '+10.05%', netBuy: '1875.87万',
  reason: '日涨幅偏离值达7%的证券',
  fields: [
    { label: '代码', value: '605069' }, { label: '名称', value: '正和生态' },
    { label: '涨幅%', value: '+10.05' }, { label: '现价', value: '11.50' },
    { label: '净买入', value: '1875.87万' }
  ],
  buySeats: [
    '国信证券北京分公司 +1202.89\\0 1202.89 1202.89',
    '国泰海通证券上海浦东新区锦康路 +622.46\\0 622.46 622.46',
    '国信证券北京亚运村 +485.76\\0 485.76 485.76',
    '国泰海通证券重庆中山三路 +469.89\\0 469.89 469.89',
    '东方证券无锡梁清路 +416.19\\0 416.19 416.18'
  ],
  sellSeats: [
    '申万宏源证券上海普陀区大渡河路 0\\-433.73 -433.73 0.00',
    '中信证券上海分公司 0\\-268.62 -268.62 0.00',
    '瑞银证券上海花园石桥路 0\\-242.45 -242.45 0.00',
    '国泰君安股份有限公司总部 一线 0\\-192.06 -192.06 0.00',
    '摩根大通中国上海银城中路 0\\-184.46 -184.46 0.00'
  ],
  warnings: [
    '这是通过桌面工具读取并保存的真实页面示例，不代表独立采集器已成功读取当前窗口。',
    '示例记录时间取自同花顺页面时钟；榜单日期为 2026-09-11。',
    '当前栏价格与涨幅的行情时间未暴露，不能当作榜单日期的收盘数据。',
    '席位数据保留原文，原表金额单位为万；未拆分的数值请对照原表。'
  ]
};
