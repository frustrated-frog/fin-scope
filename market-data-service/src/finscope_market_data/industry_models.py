"""Historical industry facts shared by acquisition and research."""
from dataclasses import dataclass


@dataclass(frozen=True)
class IndustryChange:
    code: str
    industry: str
    effective_from: str
    taxonomy: str
    source: str
    retrieved_at: str
    available_at: str | None = None
