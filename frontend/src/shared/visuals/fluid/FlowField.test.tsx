import { act, render, waitFor } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { FlowField } from './FlowField';
import type { FlowFactory } from './renderer';

function controller() {
  return { setActive: vi.fn(), setMotion: vi.fn(), refresh: vi.fn(), destroy: vi.fn() };
}

function media(reduced = false) {
  const listeners = new Set<() => void>();
  const query = {
    matches: reduced,
    addEventListener: (_: string, fn: () => void) => listeners.add(fn),
    removeEventListener: (_: string, fn: () => void) => listeners.delete(fn)
  };
  vi.stubGlobal('matchMedia', () => query);
  return { query, listeners };
}

test('pauses while hidden, follows motion preference and releases renderer on unmount', async () => {
  const { query, listeners } = media();
  const engine = controller();
  const { container, unmount } = render(<FlowField mode="ambient" loadRenderer={async () => () => engine} />);
  await waitFor(() => expect(container.firstChild).toHaveAttribute('data-flow-ready', 'true'));
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
  act(() => document.dispatchEvent(new Event('visibilitychange')));
  expect(engine.setActive).toHaveBeenLastCalledWith(false);
  act(() => {
    query.matches = true;
    listeners.forEach(fn => fn());
  });
  expect(engine.setMotion).toHaveBeenLastCalledWith(false);
  unmount();
  expect(engine.destroy).toHaveBeenCalledTimes(1);
  expect(listeners.size).toBe(0);
  vi.restoreAllMocks();
});

test('does not create a renderer after asynchronous loading finishes on an unmounted view', async () => {
  media();
  const create = vi.fn(() => controller());
  let resolve!: (value: typeof create) => void;
  const pending = new Promise<typeof create>(done => { resolve = done; });
  const { unmount } = render(<FlowField mode="cards" loadRenderer={() => pending} />);
  unmount();
  await act(async () => { resolve(create); });
  expect(create).not.toHaveBeenCalled();
});

test('retains children and static fallback if GPU initialization fails', async () => {
  media();
  const { container, getByRole } = render(
    <FlowField mode="cards" loadRenderer={async () => () => { throw new Error('WebGL unavailable'); }}>
      <button>打开文章</button>
    </FlowField>
  );
  await waitFor(() => expect(container.firstChild).toHaveAttribute('data-flow-ready', 'false'));
  expect(getByRole('button')).toHaveTextContent('打开文章');
  expect(container.querySelector('canvas')).toHaveAttribute('aria-hidden', 'true');
});

test('destroys failed context once and switches back to readable static cards', async () => {
  media();
  const engine = controller();
  let fail!: () => void;
  const create: FlowFactory = (_canvas, _host, options) => {
    fail = options.onFailure;
    return engine;
  };
  const { container, unmount } = render(<FlowField mode="cards" loadRenderer={async () => create} />);
  await waitFor(() => expect(container.firstChild).toHaveAttribute('data-flow-ready', 'true'));
  act(() => fail());
  expect(container.firstChild).toHaveAttribute('data-flow-ready', 'false');
  unmount();
  expect(engine.destroy).toHaveBeenCalledTimes(1);
});

test('refreshes the same workspace renderer when asynchronous cards arrive or are removed', async () => {
  media();
  const engine = controller();
  const create = vi.fn(() => engine);
  const loadRenderer = async () => create;
  const { rerender, getByRole } = render(<FlowField mode="workspace" label="自选行情" loadRenderer={loadRenderer} />);
  await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
  engine.refresh.mockClear();
  rerender(<FlowField mode="workspace" label="自选行情" loadRenderer={loadRenderer}><button data-flow-surface="fresh">股票</button></FlowField>);
  await waitFor(() => expect(engine.refresh).toHaveBeenCalled());
  expect(getByRole('group', { name: '自选行情' })).toContainElement(getByRole('button'));
  engine.refresh.mockClear();
  rerender(<FlowField mode="workspace" label="自选行情" loadRenderer={loadRenderer}><button data-flow-surface="review">股票</button></FlowField>);
  await waitFor(() => expect(engine.refresh).toHaveBeenCalled());
  engine.refresh.mockClear();
  rerender(<FlowField mode="workspace" label="自选行情" loadRenderer={loadRenderer} />);
  await waitFor(() => expect(engine.refresh).toHaveBeenCalled());
  expect(create).toHaveBeenCalledTimes(1);
});
