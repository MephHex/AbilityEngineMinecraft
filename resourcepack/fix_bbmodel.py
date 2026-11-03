"""
Makes a Blockbench model ready for Model Engine, in place (a copy of the original is kept as <name>.bbmodel.orig, next
to the plugins/ModelEngine folder's blueprints, never inside it):

- bone names lowercase, without spaces (Model Engine rejects the whole model otherwise: "Non [a-z0-9_-./] character");
- the six player bones (Head, Body, Right Arm, Left Arm, Right Leg, Left Leg, any case or spacing) become Model
  Engine's player limbs (phead_head, pbody_body, prarm_right_arm, ...), drawn with the wearer's own skin;
- the animations follow the new names;
- a player-limb model built at vanilla size gets its skeleton shrunk to 15/16 (bone pivots, cubes and position
  keyframes, around the feet): Model Engine draws player limbs at 15/16 of vanilla size, so at vanilla pivots they
  don't meet (arms apart from the body, feet off the ground). AbilityEngine scales such a model back up by 16/15
  in-game, so it ends up exactly vanilla-sized with its limbs joined.

    python fix_bbmodel.py "<server>/plugins/ModelEngine/blueprints/test_animation.bbmodel"

Then /meg reload models. Running it again on a fixed model changes nothing.
"""
import json
import re
import shutil
import sys
from pathlib import Path

LIMBS = {
    "head": "phead_head", "body": "pbody_body",
    "right_arm": "prarm_right_arm", "left_arm": "plarm_left_arm",
    "right_leg": "prleg_right_leg", "left_leg": "plleg_left_leg",
}


def fixed(name):
    plain = re.sub(r"[^a-z0-9_\-.]", "_", name.strip().lower().replace(" ", "_"))
    if plain in LIMBS.values() or re.match(r"^[a-z_]+\[", name):  # already a limb / a Model Engine tag: keep it
        return plain if plain in LIMBS.values() else name
    return LIMBS.get(plain, plain)


LIMB_SCALE = 15 / 16  # Model Engine draws player limbs at this share of vanilla size


def shrink_to_limb_scale(model):
    """Shrinks a vanilla-sized player-limb skeleton to 15/16 around the feet. False: none, or already done."""
    heads = [g for g in model.get("groups", []) if g.get("name", "").startswith("phead_")]
    if not heads or abs(heads[0]["origin"][1] - 24) > 0.01:  # vanilla neck at 24 px; 15/16: 22.5
        return False
    k = LIMB_SCALE

    def scaled(v):
        return [round(c * k, 5) for c in v]

    for group in model.get("groups", []):
        group["origin"] = scaled(group["origin"])
    for element in model.get("elements", []):
        for key in ("from", "to", "origin"):
            if key in element:
                element[key] = scaled(element[key])
        if "inflate" in element:
            element["inflate"] = round(element["inflate"] * k, 5)
        element["box_uv"] = False  # (keep each face's skin area; Blockbench would re-derive it from the new size)
    for animation in model.get("animations", []):
        for animator in animation.get("animators", {}).values():
            for frame in animator.get("keyframes", []):
                if frame.get("channel") != "position":
                    continue
                for point in frame.get("data_points", []):
                    for axis in ("x", "y", "z"):
                        try:
                            point[axis] = str(round(float(point[axis]) * k, 5))
                        except (KeyError, ValueError):
                            pass  # (a Molang expression: left as is)
    return True


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    path = Path(sys.argv[1])
    model = json.loads(path.read_text(encoding="utf-8"))
    renamed = {}
    for group in model.get("groups", []):  # Blockbench 5: bones are listed here
        new = fixed(group["name"])
        if new != group["name"]:
            renamed[group["name"]] = new
            group["name"] = new

    def walk(nodes):  # older Blockbench: bones nested in the outliner
        for node in nodes:
            if isinstance(node, dict):
                if "name" in node:
                    new = fixed(node["name"])
                    if new != node["name"]:
                        renamed[node["name"]] = new
                        node["name"] = new
                walk(node.get("children", []))
    walk(model.get("outliner", []))

    for animation in model.get("animations", []):
        for animator in animation.get("animators", {}).values():
            if animator.get("name") in renamed:
                animator["name"] = renamed[animator["name"]]

    shrunk = shrink_to_limb_scale(model)

    if not renamed and not shrunk:
        print("Already fine:", [g.get("name") for g in model.get("groups", [])])
        return
    backup = path.parent.parent / (path.name + ".orig")  # outside blueprints/: Model Engine would load it too
    if not backup.exists():  # (the first original only: a second run doesn't overwrite it)
        shutil.copyfile(path, backup)
    path.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
    for old, new in renamed.items():
        print(f"  {old} -> {new}")
    if shrunk:
        print("  skeleton shrunk to 15/16 (player limbs join up; scaled back to vanilla size in-game)")
    print(f"Fixed {path.name} (original kept as {backup})")


if __name__ == "__main__":
    main()
