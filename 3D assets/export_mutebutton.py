# Export the mute button model to game-space OBJ + MTL.
#   /Applications/Blender.app/Contents/MacOS/Blender --background \
#       --python "3D assets/export_mutebutton.py"
import bpy, os, shutil

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "button10", "mutebutton.blend")
DST = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "models"))
os.makedirs(DST, exist_ok=True)

bpy.ops.wm.open_mainfile(filepath=SRC)

def deselect_all():
    for o in bpy.data.objects:
        try: o.select_set(False)
        except Exception: pass

convertible = [ob.name for ob in bpy.data.objects if ob.type in ('FONT', 'CURVE', 'SURFACE', 'META')]
for nm in convertible:
    ob = bpy.data.objects.get(nm)
    if ob is None: continue
    deselect_all(); bpy.context.view_layer.objects.active = ob; ob.select_set(True)
    try: bpy.ops.object.convert(target='MESH')
    except Exception as e: print("convert failed", nm, e)

out = os.path.join(DST, "mutebutton.obj")
# Export the WHOLE scene, not a selection. Selecting every mesh and passing
# export_selected_objects=True looks equivalent, but it silently dropped the drum
# (the button's outer wall + lid, object "Cylinder"): the exporter wrote its `o`
# line with no geometry under it, so Building 10 came out as furniture standing in
# thin air with no button around it. Exporting the scene has no such problem.
#
# UVs must come along (export_uv=True) or the textured surfaces — the whiteboards,
# whose images are single UV-mapped planes — arrive with nothing to map their image
# onto and render as flat white. path_mode='STRIP' cuts the MTL's map_Kd down to a
# bare filename; the absolute path Blender writes by default means nothing on a phone.
bpy.ops.wm.obj_export(filepath=out, export_selected_objects=False,
                      forward_axis='NEGATIVE_Z', up_axis='Y',
                      export_normals=False, export_uv=True, export_materials=True,
                      path_mode='STRIP')
print("exported ->", out)

# The textures the MTL now names, copied next to the model so the app can open them
# from assets/. Blender keeps them wherever the artist had them (here: res/drawable,
# which the GL renderer cannot read).
TEX_DST = os.path.join(DST, "textures")
os.makedirs(TEX_DST, exist_ok=True)
for im in bpy.data.images:
    if im.source != 'FILE' or not im.filepath:
        continue
    src = bpy.path.abspath(im.filepath)
    if not os.path.isfile(src):
        print("missing texture, skipped:", src)
        continue
    shutil.copyfile(src, os.path.join(TEX_DST, os.path.basename(src)))
    print("texture ->", os.path.basename(src))
