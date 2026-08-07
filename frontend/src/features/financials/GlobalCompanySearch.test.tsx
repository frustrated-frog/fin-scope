import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { GlobalCompanySearch } from './GlobalCompanySearch';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

beforeEach(() => vi.mocked(api).mockReset());

test('searches by company name and returns issuer-level results', async () => {
  const user = userEvent.setup();
  const onSelect = vi.fn();
  vi.mocked(api).mockResolvedValue([
    {
      providerCode: 'SEC_EDGAR',
      providerCompanyId: 'CIK0001652044',
      legalName: 'Alphabet Inc.',
      displayName: 'Alphabet Inc.',
      countryCode: 'US',
      capabilityLevel: 'L4',
      securities: [
        { symbol: 'GOOGL', exchange: 'Nasdaq', market: 'US' },
        { symbol: 'GOOG', exchange: 'Nasdaq', market: 'US' }
      ]
    }
  ]);

  render(<GlobalCompanySearch onSelect={onSelect} />);
  await user.type(screen.getByRole('combobox', { name: '搜索全球上市公司' }), 'Google');

  expect(await screen.findByRole('option', { name: /Alphabet Inc/ })).toHaveTextContent('GOOGL / GOOG');
  await user.click(screen.getByRole('option', { name: /Alphabet Inc/ }));

  expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ providerCompanyId: 'CIK0001652044' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/companies/search?q=Google&limit=8'));
});

test('does not query providers until the user enters two characters', async () => {
  const user = userEvent.setup();
  render(<GlobalCompanySearch onSelect={vi.fn()} />);

  await user.type(screen.getByRole('combobox', { name: '搜索全球上市公司' }), 'S');
  await new Promise((resolve) => setTimeout(resolve, 400));

  expect(api).not.toHaveBeenCalled();
  expect(screen.getByText('输入至少两个字符开始搜索')).toBeInTheDocument();
});
