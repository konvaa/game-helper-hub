from dataclasses import dataclass
import math


def round_down(x: float) -> int:
    return int(math.floor(float(x) + 1e-9))


@dataclass
class BaseStats:
    hp: float
    phys_min: float
    phys_max: float
    defense: float
    attack_rating: float
