"""
Builds the Arcanist's Model Engine model (arcanist.bbmodel): a player-skin body (the wearer's own skin) that moves the
way a vanilla player does, plus the attack animation from the test model.

    python make_arcanist_model.py <test_animation.bbmodel> <out: .../plugins/ModelEngine/blueprints/arcanist.bbmodel>

The template is the test model (vanilla-sized, as made in Blockbench): its skeleton, skin cubes and its animationTest
(the Arcane Bolt swing) are kept. Then /meg reload models.

Animations (named for the kit's `part:`s, prefix arcanist_):
  idle, walk, run, sneak, sneak_walk, ride, swim, glide - loops, picked by the kit's model: states
  death - once (on: death)
  animationTest - the basic attack (the Arcane Bolt's animate step), override on: the left arm does exactly the swing
                  while the legs keep walking

The limb motion is vanilla's (HumanoidModel.setupAnim): the same swing per stride, the same crouch, the same seat. Its
angles are turned into Blockbench's: vanilla's model space has y down and x mirrored, so x and y rotations change sign
and z stays; positions change sign in x and y. In Blockbench (and Model Engine) the model faces north: a positive x
rotation swings an arm forward.
"""
import copy
import json
import math
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import fix_bbmodel  # noqa: E402  (names the player limbs, shrinks the skeleton to 15/16)

PREFIX = "arcanist_"
ITEM_POSE = math.pi / 10  # vanilla: an arm holding an item is raised by this, its swing halved


def deg(radians):
    return math.degrees(radians)


def new_uuid():
    return str(uuid.uuid4())


class Anim:
    """One animation: keyframes per bone, in Blockbench degrees / pixels (vanilla-sized: shrunk with the model)."""

    def __init__(self, name, length, loop="loop", override=False):
        self.name, self.length, self.loop, self.override = name, round(length, 5), loop, override
        self.frames = {}  # bone -> channel -> [(time, (x, y, z), interpolation)]

    def key(self, bone, channel, time, xyz, interpolation="linear"):
        self.frames.setdefault(bone, {}).setdefault(channel, []).append((round(time, 5), xyz, interpolation))

    def pose(self, bone, channel, xyz):
        """Held the whole time (a loop of two equal keyframes)."""
        self.key(bone, channel, 0, xyz)
        self.key(bone, channel, self.length, xyz)

    def wave(self, bone, channel, fn, samples=16):
        """fn(phase 0..2pi) -> (x, y, z), sampled over one cycle the length of the animation."""
        for i in range(samples + 1):
            phase = 2 * math.pi * i / samples
            self.key(bone, channel, self.length * i / samples, fn(phase))

    def to_json(self, bones):
        animators = {}
        for bone, channels in self.frames.items():
            frames = []
            for channel, keys in channels.items():
                for time, (x, y, z), interpolation in keys:
                    frames.append({"channel": channel,
                                   "data_points": [{"x": str(round(x, 4)), "y": str(round(y, 4)), "z": str(round(z, 4))}],
                                   "uuid": new_uuid(), "time": time, "color": -1, "interpolation": interpolation})
            animators[bones[bone]] = {"name": bone, "type": "bone", "rotation_global": False,
                                      "quaternion_interpolation": False, "keyframes": frames}
        return {"uuid": new_uuid(), "name": self.name, "loop": self.loop, "override": self.override,
                "length": self.length, "snapping": 20, "selected": False, "anim_time_update": "",
                "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}


HEAD, BODY, RARM, LARM, RLEG, LLEG, ROOT = ("h_phead_head", "pbody_body", "prarm_right_arm", "plarm_left_arm",
                                            "prleg_right_leg", "plleg_left_leg", "root")


def stride_period(amount):
    """Seconds per stride at a vanilla limb-swing amount (walk 0.863, sprint 1, sneak 0.259): 2pi / (0.6662 * amount)."""
    return 2 * math.pi / (0.6662 * amount) / 20


def strides(a, amount, crouch_arms=0.0):
    """Vanilla's walk cycle at swing amount `amount`: arms against legs, the right arm in the item pose."""
    def right_arm(p):  # vanilla: xRot = -cos * amount, then the item pose: xRot * 0.5 - pi/10, (+ crouch)
        return (-deg(-math.cos(p) * amount * 0.5 - ITEM_POSE + crouch_arms), 0, 0)

    def left_arm(p):
        return (-deg(math.cos(p) * amount + crouch_arms), 0, 0)

    a.wave(RARM, "rotation", right_arm)
    a.wave(LARM, "rotation", left_arm)
    a.wave(RLEG, "rotation", lambda p: (-deg(math.cos(p) * 1.4 * amount), 0, 0))
    a.wave(LLEG, "rotation", lambda p: (-deg(-math.cos(p) * 1.4 * amount), 0, 0))


def crouch(a):
    """Vanilla's crouch: the body leans 0.5 rad, everything above the hips drops, the legs go back 4 px."""
    a.pose(BODY, "rotation", (-deg(0.5), 0, 0))
    a.pose(BODY, "position", (0, -3.2, 0))
    a.pose(HEAD, "position", (0, -4.2, 0))
    a.pose(RARM, "position", (0, -3.2, 0))
    a.pose(LARM, "position", (0, -3.2, 0))
    a.pose(RLEG, "position", (0, -0.2, 4))
    a.pose(LLEG, "position", (0, -0.2, 4))


def lie_flat(a):
    """Face down along the way they go (vanilla swimming / elytra): turned over the feet, then centred, 0.3 up."""
    a.pose(ROOT, "rotation", (-90, 0, 0))
    a.pose(ROOT, "position", (0, 4.8, 16))
    a.pose(HEAD, "rotation", (45, 0, 0))  # looking ahead, not at the floor


def animations():
    out = []

    idle = Anim(PREFIX + "idle", 4.0)  # vanilla's arm bob (cos / sin of age, 0.05 rad) over one 4s loop
    idle.wave(RARM, "rotation", lambda p: (deg(ITEM_POSE) - deg(0.05 * math.sin(p)), 0, deg(0.05 * math.cos(p) + 0.05)))
    idle.wave(LARM, "rotation", lambda p: (deg(0.05 * math.sin(p)), 0, -deg(0.05 * math.cos(p) + 0.05)))
    out.append(idle)

    walk = Anim(PREFIX + "walk", stride_period(0.863))  # walking: 0.216 blocks a tick, x4 = swing amount 0.863
    strides(walk, 0.863)
    out.append(walk)

    run = Anim(PREFIX + "run", stride_period(1.0))  # sprinting: amount capped at 1
    strides(run, 1.0)
    out.append(run)

    sneak = Anim(PREFIX + "sneak", 1.0)
    crouch(sneak)
    sneak.pose(RARM, "rotation", (-deg(-ITEM_POSE + 0.4), 0, 0))
    sneak.pose(LARM, "rotation", (-deg(0.4), 0, 0))
    out.append(sneak)

    sneak_walk = Anim(PREFIX + "sneak_walk", stride_period(0.259))  # sneaking: 0.3x walking speed
    crouch(sneak_walk)
    strides(sneak_walk, 0.259, crouch_arms=0.4)
    out.append(sneak_walk)

    ride = Anim(PREFIX + "ride", 1.0)  # vanilla seated: arms forward pi/5, legs out in front, spread
    ride.pose(RARM, "rotation", (-deg(-math.pi / 5 * 0.5 - ITEM_POSE), 0, 0))
    ride.pose(LARM, "rotation", (-deg(-math.pi / 5), 0, 0))
    ride.pose(RLEG, "rotation", (-deg(-1.4137), -deg(math.pi / 10), deg(0.07854)))
    ride.pose(LLEG, "rotation", (-deg(-1.4137), -deg(-math.pi / 10), deg(-0.07854)))
    out.append(ride)

    swim = Anim(PREFIX + "swim", 1.3)  # a crawl: each arm a full turn per stroke, half a stroke apart; legs flutter
    lie_flat(swim)
    # x falls through a full turn (180: reaching ahead, 90: pulling under, 0: at the hip, -90: over the back): the
    # loop's start and end are the same pose, a turn apart, so it wraps seamlessly
    swim.wave(RARM, "rotation", lambda p: (180 - math.degrees(p), 0, 0), samples=12)
    swim.wave(LARM, "rotation", lambda p: (0 - math.degrees(p), 0, 0), samples=12)
    swim.wave(RLEG, "rotation", lambda p: (deg(0.3 * math.cos(2 * p)), 0, 0))
    swim.wave(LLEG, "rotation", lambda p: (-deg(0.3 * math.cos(2 * p)), 0, 0))
    out.append(swim)

    glide = Anim(PREFIX + "glide", 1.0)  # elytra: flat, arms a little out
    lie_flat(glide)
    glide.pose(RARM, "rotation", (0, 0, 10))
    glide.pose(LARM, "rotation", (0, 0, -10))
    out.append(glide)

    death = Anim(PREFIX + "death", 1.0, loop="hold")  # vanilla: tips over sideways, 90 * sqrt(1.6 t), in 0.625s
    for t in (0, 0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.625, 1.0):
        death.key(ROOT, "rotation", t, (0, 0, 90 * min(1.0, math.sqrt(1.6 * t))))
    out.append(death)
    return out


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    template = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    model = copy.deepcopy(template)
    model["name"] = "arcanist"

    for group in model["groups"]:  # player limbs (h_phead_head: the head turns where they look)
        group["name"] = fix_bbmodel.fixed(group["name"])
    bones = {g["name"]: g["uuid"] for g in model["groups"]}
    missing = {HEAD, BODY, RARM, LARM, RLEG, LLEG} - bones.keys()
    if missing:
        sys.exit(f"The template has no {sorted(missing)}: it should be the test model (Head, Body, Right Arm, ...)")

    root = {k: v for k, v in model["groups"][0].items()}  # (a bone like the others)
    root.update({"name": ROOT, "uuid": new_uuid(), "origin": [0, 0, 0], "rotation": [0, 0, 0], "children": []})
    model["groups"].insert(0, root)
    model["outliner"] = [{"uuid": root["uuid"], "isOpen": True, "children": model["outliner"]}]
    bones[ROOT] = root["uuid"]

    attack = next(a for a in template["animations"] if a["name"] == "animationTest")
    attack = copy.deepcopy(attack)
    attack["override"] = True  # the swing replaces the walk on the bones it moves (the left arm)
    attack["animators"] = {k: v for k, v in attack["animators"].items() if v.get("keyframes")}
    for animator in attack["animators"].values():
        animator["name"] = fix_bbmodel.fixed(animator["name"])

    made = animations()
    model["animations"] = [attack] + [a.to_json(bones) for a in made]

    if not fix_bbmodel.shrink_to_limb_scale(model):
        sys.exit("The template isn't vanilla-sized (its neck should be at 24 px): give the original test model")
    out = Path(sys.argv[2])
    out.write_text(json.dumps(model, indent=1), encoding="utf-8")
    print(f"Wrote {out}: {', '.join(a['name'] for a in model['animations'])}")


if __name__ == "__main__":
    main()
