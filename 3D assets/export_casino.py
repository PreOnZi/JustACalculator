# Export the Building 8 "gambling" props to game-space OBJ + MTL.
#
# Run after editing any prop:
#   /Applications/Blender.app/Contents/MacOS/Blender --background \
#       --python "3D assets/export_casino.py"
#
# Source: loose 3D assets/<name>.blend
# Output: app/src/main/assets/models/casino/<name>.obj (+ .mtl)
#
# Unlike the runner pieces, these keep materials (export_materials=True) so the
# offscreen model→bitmap renderer (CurrencyIcon.kt / ModelBitmapRenderer) picks
# up per-material Kd colours. Axes match the rest of the project
# (forward=NEGATIVE_Z, up=Y). Positions/materials only — normals are recomputed
# on the CPU as flat per-triangle normals at render time.

import bpy, os

HERE = os.path.dirname(__file__)
DST = os.path.normpath(os.path.join(HERE, "..",
        "app", "src", "main", "assets", "models", "casino"))
os.makedirs(DST, exist_ok=True)

PROPS = ["arcade", "boxclosed", "boxopen", "cup", "button", "table", "bomb"]

for name in PROPS:
    src = os.path.join(HERE, name + ".blend")
    if not os.path.exists(src):
        print(f"!! {name}: {src} missing, skipped")
        continue

    bpy.ops.wm.open_mainfile(filepath=src)

    def deselect_all():
        for o in bpy.data.objects:
            try: o.select_set(False)
            except Exception: pass

    # Convert any FONT/CURVE/SURFACE/META objects to mesh so they export.
    # Iterate by name and re-fetch each time — convert can invalidate stale
    # Python object wrappers (StructRNA removed), so never reuse a loop var.
    convertible = [ob.name for ob in bpy.data.objects
                   if ob.type in ('FONT', 'CURVE', 'SURFACE', 'META')]
    for nm in convertible:
        ob = bpy.data.objects.get(nm)
        if ob is None: continue
        deselect_all()
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        try: bpy.ops.object.convert(target='MESH')
        except Exception as e: print(f"!! {name}: convert {nm} failed: {e}")

    deselect_all()
    active = None
    for nm in [o.name for o in bpy.data.objects]:
        ob = bpy.data.objects.get(nm)
        if ob is None or ob.type != 'MESH': continue
        try:
            ob.select_set(True); active = ob
        except Exception: pass
    if active is None:
        print(f"!! {name}: no mesh, skipped"); continue
    bpy.context.view_layer.objects.active = active

    out = os.path.join(DST, name + ".obj")
    bpy.ops.wm.obj_export(filepath=out, export_selected_objects=True,
                          forward_axis='NEGATIVE_Z', up_axis='Y',
                          export_normals=False, export_uv=False,
                          export_materials=True)
    print(f"exported {name} -> {out}")
