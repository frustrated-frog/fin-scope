import { ReactNode } from 'react';

type MarkdownBlock =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] };

function plainText(value: string) {
  return value
    .trim()
    .replace(/^(?:>\s*)+/, '')
    .replace(/^(?:#{1,6}\s*)+/, '')
    .replace(/^(?:[-*+]\s+|\d+[.)]\s+)/, '')
    .replace(/^(?:\*\*|__)(.*)(?:\*\*|__)$/, '$1');
}

function blocksFrom(value: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: string[] } | undefined;

  const flushParagraph = () => {
    if (paragraph.length) blocks.push({ type: 'paragraph', text: paragraph.join(' ') });
    paragraph = [];
  };
  const flushList = () => {
    if (list?.items.length) blocks.push({ type: 'list', ...list });
    list = undefined;
  };

  value.split(/\r?\n/).forEach((line) => {
    const heading = line.match(/^\s*#{1,6}\s+(.+?)\s*#*\s*$/);
    const listItem = line.match(/^\s*((?:[-*+])|(\d+)[.)])\s+(.+)$/);

    if (heading) {
      flushParagraph();
      flushList();
      blocks.push({ type: 'heading', text: plainText(heading[1]) });
    } else if (listItem) {
      flushParagraph();
      const ordered = Boolean(listItem[2]);
      if (list && list.ordered !== ordered) flushList();
      if (!list) list = { ordered, items: [] };
      list.items.push(plainText(listItem[3]));
    } else if (!line.trim()) {
      flushParagraph();
      flushList();
    } else {
      flushList();
      paragraph.push(plainText(line));
    }
  });

  flushParagraph();
  flushList();
  return blocks;
}

function renderBlock(block: MarkdownBlock, index: number): ReactNode {
  if (block.type === 'heading') return <h4 key={index}>{block.text}</h4>;
  if (block.type === 'paragraph') return <p key={index}>{block.text}</p>;
  const List = block.ordered ? 'ol' : 'ul';
  return <List key={index}>{block.items.map((item, itemIndex) => <li key={`${item}-${itemIndex}`}>{item}</li>)}</List>;
}

export function KnowledgeMarkdown({ value }: { value: string }) {
  return <div className="knowledge-markdown">{blocksFrom(value).map(renderBlock)}</div>;
}
