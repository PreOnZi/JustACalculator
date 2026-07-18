# Export the Stickmancourse modular track pieces to game-space OBJ.
#
# Run after editing any piece:
#   /Applications/Blender.app/Contents/MacOS/Blender --background \
#       --python "3D assets/export_stickmancourse.py"
#
# The source is now grouped into subfolders:
#   Stickmancourse/regular       - regular pieces (base, ramps, gaps, spikes)
#   Stickmancourse/obstacles     - hill, sptoll, spgap, spring, spwall
#   Stickmancourse/assets        - sign, directions, boulders (ball1-4)
#   Stickmancourse/forks_branch  - forkin, forkoutl, forkoutr
#   Stickmancourse/forks_choice  - fork, forkud
#   Stickmancourse/*.blend       - loose pieces (coin)
#
# Each piece is exported to the matching subfolder under
#   app/src/main/assets/models/stickmancourse/<group>/<name>.obj
# (loose pieces land in the root) with forward_axis=NEGATIVE_Z, up_axis=Y so
# Blender (X=lane, Y=length, Z=up) lands in the runner's world axes
# (X=lane, Y=up, Z=length, run = -Z). loadTilePiece() reads positions only and
# computes flat normals, so materials/normals are not exported.

import bpy, os, glob

SRC = os.path.join(os.path.dirname(__file__), "Stickmancourse")
DST = os.path.normpath(os.path.join(os.path.dirname(__file__),
        "..", "app", "src", "main", "assets", "models", "stickmancourse"))
os.makedirs(DST, exist_ok=True)

# Every .blend under SRC, preserving its subfolder (if any) in the output path.
for f in sorted(glob.glob(os.path.join(SRC, "**", "*.blend"), recursive=True)):
    if f.endswith(".blend1"):
        continue
    rel = os.path.relpath(f, SRC)                  # e.g. "obstacles/hill.blend"
    rel_dir = os.path.dirname(rel)                 # e.g. "obstacles" (or "")
    name = os.path.splitext(os.path.basename(f))[0]
    out_dir = os.path.join(DST, rel_dir) if rel_dir else DST
    os.makedirs(out_dir, exist_ok=True)

    bpy.ops.wm.open_mainfile(filepath=f)
    # Convert FONT/CURVE objects (e.g. the toll sign's text) to mesh so they export.
    for ob in list(bpy.data.objects):
        if ob.type in ('FONT', 'CURVE', 'SURFACE', 'META'):
            bpy.context.view_layer.objects.active = ob
            bpy.ops.object.select_all(action='DESELECT')
            ob.select_set(True)
            try: bpy.ops.object.convert(target='MESH')
            except Exception as e: print(f"!! {rel}: convert {ob.name} failed: {e}")
    active = None
    for ob in bpy.data.objects:
        try: ob.select_set(ob.type == 'MESH')
        except Exception: pass
        if ob.type == 'MESH': active = ob
    if active is None:
        print(f"!! {rel}: no mesh, skipped"); continue
    bpy.context.view_layer.objects.active = active
    out = os.path.join(out_dir, name + ".obj")
    bpy.ops.wm.obj_export(filepath=out, export_selected_objects=True,
                          forward_axis='NEGATIVE_Z', up_axis='Y',
                          export_normals=False, export_uv=False,
                          export_materials=False)
    print(f"exported {rel} -> {out}")
