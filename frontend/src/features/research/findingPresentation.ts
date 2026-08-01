const BOILERPLATE = /(?:免责声明|必须阅读|分析师披露|商业关系披露|下载本公司(?:之)?研究报告|下载本研究报告|备查文件|释义项|审计报告原件|签名并盖章|指定网站公开披露|公司文件的正本|公告原稿|备置地点)/;
const LEADING_LABEL = /^(?:核心结论|主要观点|结论|摘要|观点)\s*[:：—-]?\s*/;

export type PresentedFinding = {
  summary: string;
  fullText: string;
  expandable: boolean;
  readable: boolean;
};

export function presentFinding(raw: string): PresentedFinding {
  const fullText = cleanFindingText(raw) || '该证据暂时没有可展示的文字摘要。';
  const presentationText = stripResearchNavigation(fullText);
  const sentences = presentationText.match(/[^。！？!?；;]+[。！？!?；;]?/g) || [presentationText];
  const meaningful = sentences
    .map((sentence) => sentence.trim().replace(LEADING_LABEL, ''))
    .filter((sentence) => sentence.length >= 2 && !BOILERPLATE.test(sentence));
  const readable = meaningful.length > 0;
  const summary = readable
    ? truncateFinding(meaningful[0], 92)
    : '原始材料未形成可读判断，请展开核对证据。';
  return {
    summary,
    fullText,
    expandable: !readable || fullText.length > summary.length + 8,
    readable
  };
}

function stripResearchNavigation(value: string) {
  if (!/(?:下载本公司(?:之)?研究报告|下载本研究报告|研究部网站)/.test(value)) return value;
  return value.replace(/^.*(?:研究部网站|下载本公司(?:之)?研究报告|下载本研究报告)[，,\s]*/u, '').trim();
}

function cleanFindingText(raw: string) {
  return (raw || '')
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
    .replace(/https?:\/\/\S+/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#*_>`]+/g, ' ')
    .replace(/\|{2,}/g, ' · ')
    .replace(/\s+/g, ' ')
    .trim();
}

function truncateFinding(value: string, limit: number) {
  if (value.length <= limit) return value;
  const candidate = value.slice(0, limit);
  const boundary = Math.max(candidate.lastIndexOf('，'), candidate.lastIndexOf('、'), candidate.lastIndexOf(' '));
  return `${candidate.slice(0, boundary >= limit * .58 ? boundary : limit).trim()}…`;
}
