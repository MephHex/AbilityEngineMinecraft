"""
Builds AbilityEngine-Combined.zip: the AbilityEngine pack (AbilityEngine-HiddenUlt.zip, next to this script) plus Model
Engine's generated pack, as one pack for server.properties (a server sends only one there).

Model Engine regenerates its pack on every /meg reload, so run this again after adding or changing models:

    python merge_model_engine.py "<server>/plugins/ModelEngine/resource pack.zip"

Then host AbilityEngine-Combined.zip where resource-pack= points and put the printed sha1 in resource-pack-sha1=.
Model Engine's pack.mcmeta is kept (its overlays pick the right files per Minecraft version); if both packs have the
same file, it stops and lists them.
"""
import hashlib
import json
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OURS = HERE / "AbilityEngine-HiddenUlt.zip"
OUT = HERE / "AbilityEngine-Combined.zip"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    theirs = Path(sys.argv[1])
    with zipfile.ZipFile(OURS) as a, zipfile.ZipFile(theirs) as b:
        ours = {n for n in a.namelist() if not n.endswith("/")}
        meg = {n for n in b.namelist() if not n.endswith("/")}
        clash = sorted((ours & meg) - {"pack.mcmeta", "pack.png"})
        if clash:
            sys.exit("Both packs have these files, merge them by hand: " + ", ".join(clash))

        meta = json.loads(b.read("pack.mcmeta"))
        meta["pack"]["description"] = "AbilityEngine + Model Engine"

        with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as out:
            out.writestr("pack.mcmeta", json.dumps(meta, indent=2))
            for name in sorted(ours - {"pack.mcmeta"}):
                out.writestr(name, a.read(name))
            for name in sorted(meg - {"pack.mcmeta"} - ours):
                out.writestr(name, b.read(name))

    sha1 = hashlib.sha1(OUT.read_bytes()).hexdigest()
    print(f"Wrote {OUT.name} ({len(ours | meg)} files)")
    print(f"resource-pack-sha1={sha1}")


if __name__ == "__main__":
    main()
