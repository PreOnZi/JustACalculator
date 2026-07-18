# Export the custom bridge pieces to game-space OBJ + MTL.
#   /Applications/Blender.app/Contents/MacOS/Blender --background \
#       --python "3D assets/export_bridge.py"
import bpy, os

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "bridge")
DST = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "models", "bridge"))
os.makedirs(DST, exist_ok=True)

def deselect_all():
    for o in bpy.data.objects:
        try: o.select_set(False)
        except Exception: pass

for n in range(1, 10):
    src = os.path.join(SRC, f"bridge{n}.blend")
    if not os.path.exists(src):
        print("!! missing", src); continue
    bpy.ops.wm.open_mainfile(filepath=src)
    convertible = [ob.name for ob in bpy.data.objects if ob.type in ('FONT', 'CURVE', 'SURFACE', 'META')]
    for nm in convertible:
        ob = bpy.data.objects.get(nm)
        if ob is None: continue
        deselect_all(); bpy.context.view_layer.objects.active = ob; ob.select_set(True)
        try: bpy.ops.object.convert(target='MESH')
        except Exception as e: print("convert failed", nm, e)
    deselect_all()
    active = None
    for nm in [o.name for o in bpy.data.objects]:
        ob = bpy.data.objects.get(nm)
        if ob is None or ob.type != 'MESH': continue
        ob.select_set(True); active = ob
    if active is None:
        print("!! no mesh in", src); continue
    bpy.context.view_layer.objects.active = active
    out = os.path.join(DST, f"bridge{n}.obj")
    bpy.ops.wm.obj_export(filepath=out, export_selected_objects=True,
                          forward_axis='NEGATIVE_Z', up_axis='Y',
                          export_normals=False, export_uv=False, export_materials=True)
    print("exported", out)
