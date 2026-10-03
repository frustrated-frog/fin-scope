export type WatchFocus = {
  watchlistId: number;
  code: string;
  type: 'STOCK' | 'FUND' | 'SECTOR';
  name?: string;
  sectorCode?: string;
  reason?: string;
  nextWatch?: string;
  direction?: string;
};
