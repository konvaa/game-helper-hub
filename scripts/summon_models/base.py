from dataclasses import dataclass, field
from typing import Dict, List, Any


@dataclass
class SummonResult:
    skill_key: str
    monster_id: str
    difficulty: str

    final: Dict[str, float] = field(default_factory=dict)
    breakdown: List[Dict[str, Any]] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)


class SummonModel:
    """
    Base class for every summon calculation model.
    Each summon MUST implement compute().
    """

    def compute(self, engine_ctx: Any) -> SummonResult:
        raise NotImplementedError("Summon model must implement compute()")
