"""
Makes a Blockbench model ready for Model Engine, in place (a copy of the original is kept as <name>.bbmodel.orig, next
to the plugins/ModelEngine folder's blueprints, never inside it):

- bone names lowercase, without spaces (Model Engine rejects the whole model otherwise: "Non [a-z0-9_-./] character");
- the six player bones (Head, Body, Right Arm, Left Arm, Right Leg, Left Leg, any case or spacing) become Model
  Engine's player limbs (phead_head, pbody_body, prarm_right_arm, ...), drawn with the wearer's own skin;
- the animations follow the new names.

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

    if not renamed:
        print("Already fine:", [g.get("name") for g in model.get("groups", [])])
        return
    backup = path.parent.parent / (path.name + ".orig")  # outside blueprints/: Model Engine would load it too
    shutil.copyfile(path, backup)
    path.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
    for old, new in renamed.items():
        print(f"  {old} -> {new}")
    print(f"Fixed {path.name} (original kept as {backup})")


if __name__ == "__main__":
    main()
