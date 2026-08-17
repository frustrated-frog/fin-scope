from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class TradingScopeDecision:
    allowed: bool
    market: str | None
    reason: str | None = None


class TradingScopePolicy:
    """Account-aware A-share universe used before any market-data request."""

    def classify(self, code: str) -> TradingScopeDecision:
        normalized = str(code).strip()
        if len(normalized) != 6 or not normalized.isdigit():
            return TradingScopeDecision(False, None, "UNSUPPORTED_SECURITY_SCOPE")
        if normalized.startswith(("688", "689")):
            return TradingScopeDecision(False, None, "NO_STAR_MARKET_PERMISSION")
        if normalized.startswith(("4", "8", "92")):
            return TradingScopeDecision(False, None, "NO_BEIJING_MARKET_PERMISSION")
        if normalized.startswith(("600", "601", "603", "605")):
            return TradingScopeDecision(True, "SH")
        if normalized.startswith(("000", "001", "002", "003", "300", "301")):
            return TradingScopeDecision(True, "SZ")
        return TradingScopeDecision(False, None, "UNSUPPORTED_SECURITY_SCOPE")
