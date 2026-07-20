import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ToastHost } from './ToastHost';

test('renders global toast notifications as a separate live region', () => {
  render(
    <ToastHost
      toasts={[
        { id: 1, type: 'success', message: '2025 一季报已抓取并完成分析' },
        { id: 2, type: 'error', message: '接口暂不可用' }
      ]}
    />
  );

  const region = screen.getByLabelText('全局通知');
  expect(region).toHaveClass('toast-container');
  expect(region).toHaveAttribute('aria-live', 'polite');

  expect(screen.getByRole('status', { name: '成功通知' })).toHaveTextContent('完成');
  expect(screen.getByRole('status', { name: '错误通知' })).toHaveTextContent('注意');
});
