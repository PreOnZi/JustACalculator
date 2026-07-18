"""
combine_stickman4.py — Merge the stickman4 Mixamo FBX clips (animation/stickman4)
into one stickman4.glb with named clips, then it's copied to
app/src/main/assets/models/stickman4.glb for the Building 6 runner.

Same approach as combine_animations.py: the first FBX (alphabetically, that is
mapped below) becomes the master character (its stickman4 mesh + mixamorig
armature); every other FBX's action is retargeted onto the master and pushed as
an NLA strip so the glTF exporter emits one named clip each.

Run:
    /Applications/Blender.app/Contents/MacOS/Blender --background \\
        --python "3D assets/combine_stickman4.py"
"""

import bpy, os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FBX_FOLDER = os.path.join(SCRIPT_DIR, "animation", "stickman4")
OUTPUT_GLB = os.path.join(SCRIPT_DIR, "stickman4.glb")

# FBX filename (no extension) → runtime clip name. The runner needs "run" and
# "idle"; the rest are bundled for future use.
ANIMATIONS = {
    "Bouncing Fight Idle": "idle_to_fight",
    "Slow Run":            "run",
    "Idle":                "idle",
    "Jump":                "jump",
    "Falling Idle":        "fall_idle",
    "Fist Fight B":        "boxing",
    "Jogging With Box":    "carry_run",   # toll helper hauling the coin
    "Box Idle":            "carry_idle",  # helper stood with the coin (hand-off)
    "Pushing":             "push",        # shoving the hill boulder
    "Shaking Head No":     "refuse",      # boulder too heavy to push alone
    "Waving":              "wave",        # helpers waving the player off at the crest
    "Dying Backwards":     "death1",      # ring-fight deaths (4 variants, mixed per body)
    "Falling Back Death":  "death2",
    "Mutant Dying":        "death3",
    "Two Handed Sword Death": "death4",
    "Climbing":            "climb",       # climbers stacking into the human-tower bridge
    "Talking Phone Pacing": "phone",      # accepted a call
    "Walking While Texting": "texting",   # accepted a message (in-place walk + text)
    "Climbing Down Wall":  "climb_down",  # bashelp character coming down
    "Shaking Hands 2":     "handshake",   # greeting the helped character
    "Injured Wave Idle":   "help_wave",   # someone calling for help on the column
}


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.actions, bpy.data.armatures,
                       bpy.data.meshes, bpy.data.materials, bpy.data.images):
        for item in list(collection):
            collection.remove(item)


def import_fbx_and_get_new_action(path):
    actions_before = set(bpy.data.actions)
    bpy.ops.import_scene.fbx(filepath=path)
    new_actions = [a for a in bpy.data.actions if a not in actions_before]
    armatures = [o for o in bpy.context.selected_objects if o.type == 'ARMATURE']
    if not armatures:
        raise RuntimeError(f"No armature in {path}")
    if not new_actions:
        raise RuntimeError(f"No animation/action in {path}")
    return armatures[0], new_actions[0]


def push_action_to_nla(armature, action, name):
    if not armature.animation_data:
        armature.animation_data_create()
    track = armature.animation_data.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name=name, start=1, action=action)
    strip.name = name
    return strip


def delete_armature_with_meshes(armature):
    children = list(armature.children)
    bpy.ops.object.select_all(action='DESELECT')
    armature.select_set(True)
    for c in children:
        c.select_set(True)
    bpy.ops.object.delete(use_global=False)


def main():
    files = []
    seen = set()
    for fname in sorted(os.listdir(FBX_FOLDER)):
        if not fname.lower().endswith(".fbx"):
            continue
        stem = os.path.splitext(fname)[0]
        if stem in ANIMATIONS and stem not in seen:
            files.append((fname, ANIMATIONS[stem]))
            seen.add(stem)
    if not files:
        print("ERROR: no matching FBX found"); return

    clear_scene()
    first_fname, first_clip = files[0]
    print(f"[master] {first_fname} -> {first_clip}")
    master, first_action = import_fbx_and_get_new_action(os.path.join(FBX_FOLDER, first_fname))
    first_action.name = first_clip
    first_action.use_fake_user = True
    push_action_to_nla(master, first_action, first_clip)

    for fname, clip in files[1:]:
        print(f"  [clip] {fname} -> {clip}")
        temp, action = import_fbx_and_get_new_action(os.path.join(FBX_FOLDER, fname))
        action.name = clip
        action.use_fake_user = True
        if temp.animation_data:
            temp.animation_data.action = None
        push_action_to_nla(master, action, clip)
        delete_armature_with_meshes(temp)

    if master.animation_data:
        master.animation_data.action = None

    bpy.ops.object.select_all(action='SELECT')
    bpy.context.view_layer.objects.active = master
    bpy.ops.export_scene.gltf(
        filepath=OUTPUT_GLB,
        export_format='GLB',
        export_animations=True,
        export_animation_mode='ACTIONS',
        export_yup=True,
        export_apply=False,
    )
    print(f"Done. Wrote {OUTPUT_GLB}  clips: {', '.join(c for _, c in files)}")


if __name__ == "__main__":
    main()
