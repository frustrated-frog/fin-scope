from finscope_market_data.discovery.trading_scope import TradingScopePolicy


def test_trading_scope_accepts_main_board_and_chinext() -> None:
    policy = TradingScopePolicy()

    assert policy.classify("600584").market == "SH"
    assert policy.classify("002156").market == "SZ"
    assert policy.classify("300672").market == "SZ"
    assert policy.classify("301308").market == "SZ"


def test_trading_scope_rejects_star_and_beijing_before_market_lookup() -> None:
    policy = TradingScopePolicy()

    assert policy.classify("688380").reason == "NO_STAR_MARKET_PERMISSION"
    assert policy.classify("689009").reason == "NO_STAR_MARKET_PERMISSION"
    assert policy.classify("920012").reason == "NO_BEIJING_MARKET_PERMISSION"
    assert policy.classify("830799").reason == "NO_BEIJING_MARKET_PERMISSION"


def test_trading_scope_rejects_unknown_security_prefix() -> None:
    decision = TradingScopePolicy().classify("510300")

    assert decision.allowed is False
    assert decision.market is None
    assert decision.reason == "UNSUPPORTED_SECURITY_SCOPE"

