type MatterStep = {
  publishedOn: string;
  title: string;
  detail: string;
  document: { label: string; url: string };
};

export type WatchlistMatter = {
  id: string;
  stockCode: string;
  stockName: string;
  category: '项目与产能' | '合同履约';
  title: string;
  status: string;
  tone: 'progress' | 'change' | 'stop';
  summary: string;
  metrics: { label: string; value: string }[];
  readingNote: string;
  steps: [MatterStep, MatterStep, ...MatterStep[]];
  featured?: true;
};

export const matterReviewDate = '2026-11-08';

// Each step summarizes a named disclosure. publishedOn is the disclosure date, not the event date.
export const watchlistMatterEvidence: WatchlistMatter[] = [
  {
    id: 'taiji-fab9b',
    stockCode: '600667',
    stockName: '太极实业',
    category: '合同履约',
    title: '华虹 FAB9B 工程总承包',
    status: '披露履约进度',
    tone: 'progress',
    featured: true,
    summary: '从预中标到正式中标，半年报进一步披露了完工比例、确认收入和回款。',
    metrics: [
      { label: '完工比例', value: '28%' },
      { label: '累计回款', value: '5.32 亿元' }
    ],
    readingNote: '履约数字来自截至 2026 年 6 月 30 日的半年报。表中金额含税，最终确认收入以竣工决算为准。',
    steps: [
      {
        publishedOn: '2026-02-06',
        title: '预中标',
        detail: '子公司十一科技与上海四建组成的联合体，被公示为该工程总承包的拟中标人。',
        document: { label: '预中标公示提示', url: 'https://static.cninfo.com.cn/finalpage/2026-02-06/1224968105.PDF#page=1' }
      },
      {
        publishedOn: '2026-02-12',
        title: '正式中标',
        detail: '联合体收到中标通知书，中标价约 37.78 亿元。十一科技预计承担约 37.19 亿元，公告明确最终金额以正式合同为准。',
        document: { label: '正式中标公告', url: 'https://static.cninfo.com.cn/finalpage/2026-02-12/1224976455.PDF#page=1' }
      },
      {
        publishedOn: '2026-08-27',
        title: '披露完工及回款',
        detail: '半年报将同一项目列入重大合同，项目金额约 37.19 亿元。截至上半年末，完工比例 28%，累计确认收入约 7.66 亿元，累计回款约 5.32 亿元。',
        document: { label: '半年报 · 第 46 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-27/1225512991.PDF#page=46' }
      }
    ]
  },
  {
    id: 'deli-shantou',
    stockCode: '603092',
    stockName: '德力佳',
    category: '项目与产能',
    title: '汕头海上风电齿轮箱项目',
    status: '计划已调整',
    tone: 'change',
    summary: '汕头项目预计可使用时间提前，部分募集资金转投无锡研发制造项目。',
    metrics: [
      { label: '原项目预计可使用', value: '2027 年 6 月' },
      { label: '无锡项目拟用募资', value: '6.62 亿元' }
    ],
    readingNote: '两个可使用日期均为公司计划。无锡项目拟投入金额按半年报披露值取整。',
    steps: [
      {
        publishedOn: '2025-11-03',
        title: '披露建设计划',
        detail: '招股说明书列出年产 800 台大型海上风电齿轮箱汕头项目，预计从前期准备到竣工验收投入使用共需三年。',
        document: { label: '招股书 · 第 364 页', url: 'https://static.cninfo.com.cn/finalpage/2025-11-03/1224781881.PDF#page=364' }
      },
      {
        publishedOn: '2026-05-29',
        title: '提出变更方案',
        detail: '汕头项目的预计可使用时间由 2028 年 4 月调整至 2027 年 6 月。公司拟将约 6.59 亿元募集资金转投无锡的 10 兆瓦以上风电齿轮箱项目一期。',
        document: { label: '项目变更公告 · 第 5 页', url: 'https://static.cninfo.com.cn/finalpage/2026-05-29/1225336803.PDF#page=5' }
      },
      {
        publishedOn: '2026-06-16',
        title: '股东会通过变更',
        detail: '股东会于 6 月 15 日通过募集资金投资项目变更议案。',
        document: { label: '股东会决议 · 第 2 页', url: 'https://static.cninfo.com.cn/finalpage/2026-06-16/1225372555.PDF#page=2' }
      },
      {
        publishedOn: '2026-08-21',
        title: '更新变更后计划',
        detail: '半年专项报告列示无锡新项目拟使用募集资金约 6.62 亿元，预计 2029 年 12 月达到预定可使用状态。',
        document: { label: '募集资金专项报告 · 第 13 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-21/1225486658.PDF#page=13' }
      }
    ]
  },
  {
    id: 'huamai-datacenter',
    stockCode: '603042',
    stockName: '华脉科技',
    category: '项目与产能',
    title: '敏捷智慧绿色数据中心项目',
    status: '终止获批',
    tone: 'stop',
    summary: '原计划建设两年，后来公司因市场变化提出终止，股东会已通过。',
    metrics: [
      { label: '原定建设周期', value: '24 个月' },
      { label: '期末累计募资投入', value: '2,051.74 万元' }
    ],
    readingNote: '累计投入截至 2026 年 6 月 30 日。股东会决议确认终止，后续资金划转需以公司披露为准。',
    steps: [
      {
        publishedOn: '2024-07-30',
        title: '披露项目计划',
        detail: '公司拟将募集资金改投敏捷智慧绿色数据中心系统研发及产业化项目，计划总投资 4,500 万元，建设期 24 个月。',
        document: { label: '募投项目变更公告 · 第 5 页', url: 'https://static.cninfo.com.cn/finalpage/2024-07-30/1220746690.PDF#page=5' }
      },
      {
        publishedOn: '2026-08-01',
        title: '拟终止实施',
        detail: '公司披露原计划于 2026 年 8 月 31 日完成。截至 6 月末已累计投入募集资金 2,051.74 万元，因市场变化、效益不及预期，提出终止。',
        document: { label: '终止公告 · 第 5 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-01/1225451874.PDF#page=5' }
      },
      {
        publishedOn: '2026-08-20',
        title: '股东会通过终止',
        detail: '8 月 19 日召开的临时股东会通过项目终止及剩余募集资金补充流动资金的议案。',
        document: { label: '股东会决议 · 第 2 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-20/1225482656.PDF#page=2' }
      }
    ]
  },
  {
    id: 'huamai-wireless',
    stockCode: '603042',
    stockName: '华脉科技',
    category: '项目与产能',
    title: '5G 无线射频器件研发及产业化',
    status: '结项获批',
    tone: 'progress',
    summary: '项目达到预定可使用状态，股东会通过结项。',
    metrics: [
      { label: '前次计划完成', value: '2027 年 7 月' },
      { label: '已达到可使用状态', value: '截至 2026 年 6 月末' }
    ],
    readingNote: '6 月末是公告确认的状态截止日。8 月股东会通过结项。',
    steps: [
      {
        publishedOn: '2026-04-21',
        title: '披露前次计划',
        detail: '2025 年度专项报告列示，项目调整后的预计完成时间为 2027 年 7 月。',
        document: { label: '募集资金年度专项报告', url: 'https://static.cninfo.com.cn/finalpage/2026-04-21/1225128444.PDF' }
      },
      {
        publishedOn: '2026-08-01',
        title: '达到预定可使用状态',
        detail: '截至 6 月 30 日，公司已建成 POI 产线及 5G 宽带无源设备产线各一条，董事会同意结项。',
        document: { label: '结项公告 · 第 3 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-01/1225451874.PDF#page=3' }
      },
      {
        publishedOn: '2026-08-20',
        title: '股东会通过结项',
        detail: '8 月 19 日召开的临时股东会通过项目结项及节余募集资金补充流动资金的议案。',
        document: { label: '股东会决议 · 第 2 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-20/1225482656.PDF#page=2' }
      }
    ]
  },
  {
    id: 'aohong-thailand',
    stockCode: '605058',
    stockName: '澳弘电子',
    category: '项目与产能',
    title: '泰国 PCB 生产基地',
    status: '部分客户进入批量生产',
    tone: 'progress',
    summary: '从基建基本完工、设备安装调试，推进到产线试运行及部分客户进入批量生产阶段。',
    metrics: [
      { label: '期末募资投入比例', value: '45.51%' },
      { label: '计划新增年产能', value: '120 万平方米' }
    ],
    readingNote: '45.51% 是募集资金投入比例。计划产能、客户批量生产与整厂达产分别以各自的披露为准。',
    steps: [
      {
        publishedOn: '2025-12-09',
        title: '披露建设计划',
        detail: '可转债募集说明书列出泰国生产基地建设项目，规划新增年产能 120 万平方米。',
        document: { label: '可转债募集说明书', url: 'https://static.cninfo.com.cn/finalpage/2025-12-09/1224859323.PDF' }
      },
      {
        publishedOn: '2026-04-16',
        title: '设备进场调试',
        detail: '年报披露基建工程基本完工，设备陆续进场安装调试，同时推进当地团队及供应链准备。',
        document: { label: '年报 · 第 14 页', url: 'https://static.cninfo.com.cn/finalpage/2026-04-16/1225105752.PDF#page=14' }
      },
      {
        publishedOn: '2026-08-28',
        title: '披露试运行与批量生产进展',
        detail: '半年报披露上半年完成核心设备安装调试及产线联动试运行，部分客户完成审厂并进入产品认证及批量生产阶段。同一半年报的募资表列示投入比例为 45.51%。',
        document: { label: '半年报 · 第 11 页', url: 'https://static.cninfo.com.cn/finalpage/2026-08-28/1225519568.PDF#page=11' }
      }
    ]
  },
  {
    id: 'cxmt-wafer',
    stockCode: '688825',
    stockName: '长鑫科技',
    category: '项目与产能',
    title: '存储器晶圆量产线技术升级',
    status: '预算结构调整',
    tone: 'change',
    summary: '董事会批准在原项目内增加设备购置及安装预算，项目总投资仍为 75 亿元。',
    metrics: [
      { label: '设备预算增加', value: '19 亿元' },
      { label: '调整后设备预算', value: '65.66 亿元' }
    ],
    readingNote: '金额是调整后的预算。公告确认了预算结构变化，项目投产与达产状态需查后续披露。',
    steps: [
      {
        publishedOn: '2026-07-22',
        title: '披露募投方案',
        detail: '招股说明书列出存储器晶圆制造量产线技术升级改造项目，计划投资总额为 75 亿元。',
        document: { label: '科创板招股说明书', url: 'https://static.cninfo.com.cn/finalpage/2026-07-22/1225435990.PDF' }
      },
      {
        publishedOn: '2026-09-29',
        title: '调整内部预算',
        detail: '设备购置及安装费从 46.66 亿元增至 65.66 亿元，无形资产支出从 25 亿元降至 6 亿元。',
        document: { label: '内部投资结构调整 · 第 3 页', url: 'https://static.cninfo.com.cn/finalpage/2026-09-29/1225586347.PDF#page=3' }
      }
    ]
  }
];
