from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="FINSCOPE_MARKET_DATA_",
        env_file=".env",
        extra="ignore",
    )

    data_dir: Path = Path("data")
    max_retries: int = Field(default=1, ge=0, le=5)
    retry_delay_seconds: float = Field(default=0.2, ge=0, le=5)
    daily_bar_retry_cooldown_seconds: int = Field(default=21600, ge=60, le=86400)
    failure_threshold: int = Field(default=3, ge=1, le=20)
    circuit_open_seconds: int = Field(default=60, ge=1, le=3600)
    scrapling_enabled: bool = True
    scrapling_session_timeout_seconds: float = Field(default=15, ge=1, le=60)
    scrapling_browser_timeout_seconds: float = Field(default=20, ge=1, le=60)
    scrapling_browser_max_concurrency: int = Field(default=1, ge=1, le=2)
    scrapling_idle_timeout_seconds: float = Field(default=300, ge=30, le=3600)
