"""
Builds AbilityEngine-HiddenUlt.zip: a resource pack that lets the ultimate's icon (the offhand slot) show in the
hotbar / inventory as usual but not be held in the left hand.

For every vanilla item it adds  ability_engine:hidden/<item>  (assets/ability_engine/items/hidden/<item>.json): an item
model definition that draws the item's own vanilla model in the GUI and nothing anywhere else (hands, head, ground,
frames). AbilityEngine puts that model on the offhand icon when config.yml resource-pack.hide-ultimate-in-hand is on.

The vanilla definitions are read from a Minecraft client jar (1.21.4 or newer: item model definitions):
    python build_pack.py "%APPDATA%/.minecraft/versions/1.21.11/1.21.11.jar"
"""
import json
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "AbilityEngine-HiddenUlt.zip"

# Resource pack formats: 1.21.9 / 1.21.10 are 69, 1.21.11 is 75 (min_format / max_format replaced pack_format in 1.21.9).
PACK_MCMETA = {
    "pack": {
        "description": "AbilityEngine: ultimate icons in the HUD only, not held in hand",
        "min_format": 65,
        "max_format": 75,
    }
}


def hidden(definition: dict) -> dict:
    """The vanilla definition, drawn only in the GUI (hotbar, offhand slot, inventory); empty everywhere else."""
    out = {k: v for k, v in definition.items() if k != "model"}  # (hand_animation_on_swap, oversized_in_gui...)
    out["model"] = {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": definition["model"]}],
        "fallback": {"type": "minecraft:empty"},
    }
    return out


def main(jar: str) -> None:
    count = 0
    with zipfile.ZipFile(jar) as client, zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps(PACK_MCMETA, indent=2))
        for name in sorted(client.namelist()):
            if not (name.startswith("assets/minecraft/items/") and name.endswith(".json")):
                continue
            definition = json.loads(client.read(name))
            item = name.rsplit("/", 1)[1]
            pack.writestr(f"assets/ability_engine/items/hidden/{item}", json.dumps(hidden(definition), separators=(",", ":")))
            count += 1
    print(f"{OUT.name}: {count} items")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
