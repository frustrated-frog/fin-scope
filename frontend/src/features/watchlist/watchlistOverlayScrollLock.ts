let activeOverlayCount = 0;

export function acquireWatchlistOverlayScrollLock() {
  activeOverlayCount += 1;
  document.documentElement.classList.add('watchlist-kline-open');
  let released = false;
  return () => {
    if (released) return;
    released = true;
    activeOverlayCount = Math.max(0, activeOverlayCount - 1);
    if (activeOverlayCount === 0) document.documentElement.classList.remove('watchlist-kline-open');
  };
}
