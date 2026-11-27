import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { AccountPortfolioRisk } from './AccountPortfolioRisk';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const request = vi.mocked(api);
afterEach(() => vi.resetAllMocks());
const props = {
  version: 2,
  day: '2026-11-27',
  days: ['2026-11-27', '2026-11-26'],
  name: () => '待补名股票',
  onDay: vi.fn(),
};
const report = {
  state: 'VALUED',
  at: '2026-11-27T15:00:00+08:00',
  equity: 10000,
  cash: 2000,
  receivable: 0,
  source: 'BOOK',
  holdings: [
    {
      code: '601288',
      name: '农业银行',
      industry: '银行',
      industryAsOf: '2026-11-26',
      weight: 0.8,
      value: 8000,
      quantity: 1000,
      riskShare: 1,
      marginalVol: 0.25,
    },
  ],
  industries: [{ industry: '银行', count: 1, weight: 0.8, riskShare: 1 }],
  fromDay: '2026-09-01',
  throughDay: '2026-11-27',
  risk: {
    days: 60,
    marketDays: 60,
    beta: 0.4,
    annualVol: 0.2,
    systematicShare: 0.25,
    residualShare: 0.75,
    var95: 0.02,
    es95: 0.035,
    strongestPair: null,
  },
  shocks: [{ kind: 'SECTOR', industry: '银行', change: -0.08 }],
  drawdown: { current: 0.03, maximum: 0.04, observations: 53, underwaterDays: 1 },
};

it('reads dated holdings, risk decomposition and scenario losses in account currency', async () => {
  request.mockResolvedValue(report);
  render(<AccountPortfolioRisk {...props} />);
  const holding = (await screen.findByRole('rowheader', { name: /农业银行/ })).closest('tr')!;
  expect(within(holding).getByText('80.0%')).toBeInTheDocument();
  expect(within(holding).getByText('100.0%')).toBeInTheDocument();
  expect(screen.queryByText('601288')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '市场与尾部风险' }));
  expect(screen.getByText('3.50%')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '压力与回撤' }));
  expect(screen.getByText(/银行下跌 10%/)).toBeInTheDocument();
  expect(screen.getByText(/¥\s*-800.00/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('风险持仓日'), { target: { value: '2026-11-26' } });
  expect(props.onDay).toHaveBeenCalledWith('2026-11-26');
});

it('prevents an old window from replacing the selected window and labels a short sample', async () => {
  let complete!: (value: unknown) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  request.mockResolvedValue({
    ...report,
    risk: { ...report.risk, days: 11, marketDays: 11, annualVol: null, beta: null },
  });
  render(<AccountPortfolioRisk {...props} />);
  fireEvent.change(screen.getByLabelText('风险回看窗口'), { target: { value: '20' } });
  await screen.findByText(/仅有 11 个共同收益日/);
  complete({ state: 'NO_OBSERVATION' });
  expect(request.mock.calls[1][0]).toContain('window=20&day=2026-11-27');
  expect(await screen.findByText(/仅有 11 个共同收益日/)).toBeInTheDocument();
  expect(screen.queryByText(/尚无账户权益记录/)).not.toBeInTheDocument();
});

it('recovers from an error and shows an empty valuation day', async () => {
  request
    .mockRejectedValueOnce(new Error('行情服务暂不可用'))
    .mockResolvedValueOnce({ state: 'NO_OBSERVATION' });
  render(<AccountPortfolioRisk {...props} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('行情服务暂不可用');
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByText(/尚无账户权益记录/)).toBeInTheDocument();
});
