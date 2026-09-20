import type { ReactionSample } from './reactionTypes';
export const reactionSample: ReactionSample = {
  id: 1, majorEventId: 40, sourceOriginType: 'NEWS_ITEM', sourceOriginKey: 'news-99', title: '设备公司签订重大合同',
  summary: '合同金额已披露，交付预计分阶段完成。', sourceUrl: 'https://example.com/announcement', occurredDate: '2026-09-18',
  firstCapturedAt: '2026-09-18T11:00:00', registeredAt: '2026-09-18T12:00:00',
  instrumentCode: '600519.SH', instrumentName: '示例设备', eventType: 'CONTRACT',
  publishedAt: '2026-09-18T10:30:00', relationNote: '上市公司是签约方', state: 'OBSERVING', historicalBackfill: false, revision: 2,
  calculation: {
    methodVersion: 'DAILY_RETURN_DIFFERENCE_V1', benchmarkCode: '000300.SH', benchmarkName: '沪深300', adjustment: 'QFQ',
    stockSource: 'TEST', benchmarkSource: 'INDEX_TEST', stockQuality: 'FRESH_PRIMARY', benchmarkQuality: 'FRESH_PRIMARY',
    stockAsOf: '2026-09-18', benchmarkAsOf: '2026-09-18', baselineDate: '2026-09-17', firstSession: '2026-09-18',
    calculatedAt: '2026-09-18T16:30:00', pathType: 'OBSERVING', warnings: ['盘中公开信息：包含当日公告前行情'],
    points: [
      { session: -1, tradeDate: '2026-09-16', status: 'READY', stockReturnPct: -1, benchmarkReturnPct: -.3, relativeReturnPp: -.7 },
      { session: 0, tradeDate: '2026-09-17', status: 'READY', stockReturnPct: 0, benchmarkReturnPct: 0, relativeReturnPp: 0 },
      { session: 1, tradeDate: '2026-09-18', status: 'READY', stockReturnPct: 4, benchmarkReturnPct: 1, relativeReturnPp: 3 },
      { session: 2, tradeDate: '2026-09-21', status: 'NOT_DUE' }
    ],
    windows: [
      { sessions: 1, endDate: '2026-09-18', status: 'READY', stockReturnPct: 4, benchmarkReturnPct: 1, relativeReturnPp: 3 },
      { sessions: 3, endDate: '2026-09-22', status: 'NOT_DUE' },
      { sessions: 5, endDate: '2026-09-24', status: 'NOT_DUE' }
    ]
  }
};
