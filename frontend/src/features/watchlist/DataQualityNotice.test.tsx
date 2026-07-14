import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { DataQualityNotice } from './DataQualityNotice';

test('keeps stale warning visible and exposes exact snapshot age', () => {
  render(<DataQualityNotice quality={{
    status: 'STALE_FALLBACK',
    sourceCode: 'SINA_STOCK',
    staleAgeSeconds: 1620,
    warning: '当前行情源均不可用'
  }} />);

  expect(screen.getByRole('alert')).toHaveTextContent('27 分钟前');
  expect(screen.getByRole('alert')).toHaveTextContent('请勿视为实时行情');
  expect(screen.getByRole('alert')).toHaveTextContent('当前行情源均不可用');
});

test('renders automatic provider fallback as a read-only status', () => {
  render(<DataQualityNotice quality={{
    status: 'FRESH_FALLBACK', sourceCode: 'TENCENT_STOCK',
    warning: '主数据源异常，已自动切换备用数据源'
  }} />);

  expect(screen.getByRole('status')).toHaveTextContent('已自动切换备用数据源');
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});
