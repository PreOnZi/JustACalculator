"""
combine_animations.py — Merge a folder of Mixamo FBX clips into one .glb
with multiple named animation clips, ready for SceneView/Filament.

WHAT IT DOES
============
1. Reads every .fbx in FBX_FOLDER whose stem appears in ANIMATIONS.
2. Imports the FIRST file. Its armature + mesh become the "master" character.
3. For every other file: imports it, grabs the new action, renames it to the
   game-friendly name, pushes it as an NLA strip on the master, then deletes
   the duplicate armature and mesh that came with the import.
4. Exports the master as a single .glb with one named clip per action.

The retargeting "just works" because every Mixamo rig shares identical bone
names (mixamorig:Hips, mixamorig:Spine, etc.) — an action recorded against
one Mixamo armature plays correctly on any other Mixamo armature.

HOW TO RUN IT — option A: command line (recommended, repeatable)
================================================================
    /Applications/Blender.app/Contents/MacOS/Blender --background \\
        --python "3D assets/combine_animations.py"

You can re-run it any time you add or rename animations. The output .glb is
overwritten in place.

HOW TO RUN IT — option B: inside Blender's UI
=============================================
    1. Open Blender (any version 3.x or 4.x).
    2. Switch to the "Scripting" workspace (tab at the top).
    3. In the text editor: Text → Open → pick this file.
    4. Hit "Run Script" (or Alt+P with the cursor in the editor).
    5. Watch the System Console (Window → Toggle System Console on Windows;
       on macOS run Blender from a Terminal so prints appear there).

CONFIGURE
=========
Edit FBX_FOLDER, OUTPUT_GLB, and the ANIMATIONS map below if you want
different filename → clip-name mappings, or to skip clips you won't use.
"""

import bpy
import os

# ─── CONFIG ──────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

FBX_FOLDER = os.path.join(SCRIPT_DIR, "animation")
OUTPUT_GLB = os.path.join(SCRIPT_DIR, "character.glb")

# Map FBX filename (no extension) → clean clip name used at runtime.
# Each clip name reflects WHAT THE CLIP IS, not which state plays it — the
# runtime code in Building6Game.kt picks the right clip for each state. This
# keeps the asset library flexible (e.g. the fight cinematic can randomise
# between boxing / mma_kick / punching).
#
# Game-state intent for each clip (set in code, not here):
#   idle_to_fight   — defenders standing at the gate; player idle pose
#   run             — player running on a platform
#   jump            — player jumps while stationary
#   jump_running    — player jumps while moving forward
#   fall_idle       — long mid-air fall pose (held during high jumps)
#   fall_through    — falling through a hole, before respawn fade
#   land            — feet contact after a long drop
#   boxing/mma_kick/punching — gate-fight cinematic (rotated/randomised)
#   stairs_walk     — finale staircase climb
#   crush_death     — squashed by the descending ceiling
ANIMATIONS = {
    "Action Idle To Fight Idle":  "idle_to_fight",
    "Slow Run":                    "run",
    "Jump":                        "jump",
    "Running Jump":                "jump_running",
    "Falling Idle":                "fall_idle",
    "Falling":                     "fall_through",
    "Falling To Landing":          "land",
    "Boxing":                      "boxing",
    "Mma Kick":                    "mma_kick",
    "Punching":                    "punching",
    "Ascending Stairs":            "stairs_walk",
    "Two Handed Sword Death":      "crush_death",
    # Crawling is not currently used by any game state. Uncomment to bundle it
    # anyway if you want to experiment with it later:
    # "Crawling":                   "crawl",
}


# ─── IMPLEMENTATION ─────────────────────────────────────────────────────────

def clear_scene():
    """Wipe the default cube / camera / light and any leftover data."""
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.actions, bpy.data.armatures,
                       bpy.data.meshes,   bpy.data.materials,
                       bpy.data.images):
        for item in list(collection):
            collection.remove(item)


def import_fbx_and_get_new_action(path):
    """Import an FBX. Return (armature_object, newly_added_action)."""
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
    """Add the action as an NLA strip on the armature so the glTF exporter
    will emit it as its own named clip."""
    if not armature.animation_data:
        armature.animation_data_create()
    track = armature.animation_data.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name=name, start=1, action=action)
    strip.name = name
    return strip


def delete_armature_with_meshes(armature):
    """Delete an armature object plus its mesh children. Leaves actions alone
    (we've already retargeted them to the master)."""
    children_to_remove = list(armature.children)
    bpy.ops.object.select_all(action='DESELECT')
    armature.select_set(True)
    for child in children_to_remove:
        child.select_set(True)
    bpy.ops.object.delete(use_global=False)


def main():
    print("=" * 60)
    print(f"FBX folder:  {FBX_FOLDER}")
    print(f"Output GLB:  {OUTPUT_GLB}")
    print("=" * 60)

    if not os.path.isdir(FBX_FOLDER):
        print(f"ERROR: FBX folder does not exist: {FBX_FOLDER}")
        return

    # Discover and order the files we'll actually process.
    files_to_use = []
    seen = set()
    for fname in sorted(os.listdir(FBX_FOLDER)):
        if not fname.lower().endswith(".fbx"):
            continue
        stem = os.path.splitext(fname)[0]
        if stem in ANIMATIONS and stem not in seen:
            files_to_use.append((fname, ANIMATIONS[stem]))
            seen.add(stem)

    missing = [name for name in ANIMATIONS if name not in seen]
    if missing:
        print(f"WARN: these mapped names were not found in {FBX_FOLDER}:")
        for m in missing:
            print(f"   - {m}")

    if not files_to_use:
        print("ERROR: no matching FBX files found. Nothing to do.")
        return

    clear_scene()

    # ── First file → master armature ────────────────────────────────────────
    first_fname, first_clip = files_to_use[0]
    print(f"[master] {first_fname}  →  {first_clip}")
    master, first_action = import_fbx_and_get_new_action(
        os.path.join(FBX_FOLDER, first_fname))
    first_action.name = first_clip
    first_action.use_fake_user = True
    push_action_to_nla(master, first_action, first_clip)

    # ── Remaining files: retarget action to master, delete temp armature ───
    for fname, clip_name in files_to_use[1:]:
        print(f"  [clip] {fname}  →  {clip_name}")
        temp_armature, action = import_fbx_and_get_new_action(
            os.path.join(FBX_FOLDER, fname))
        action.name = clip_name
        action.use_fake_user = True
        # Detach the action from the temp armature so deleting the temp doesn't
        # orphan us. The action keeps its fcurves referencing bone names that
        # exist on the master armature, which is all glTF needs.
        if temp_armature.animation_data:
            temp_armature.animation_data.action = None
        push_action_to_nla(master, action, clip_name)
        delete_armature_with_meshes(temp_armature)

    # ── Clean up: leave master without an "active action" so first clip
    #    in the .glb isn't double-baked.
    if master.animation_data:
        master.animation_data.action = None

    # ── Export ──────────────────────────────────────────────────────────────
    bpy.ops.object.select_all(action='SELECT')
    bpy.context.view_layer.objects.active = master

    bpy.ops.export_scene.gltf(
        filepath=OUTPUT_GLB,
        export_format='GLB',
        export_animations=True,
        export_animation_mode='ACTIONS',   # one named clip per Action
        export_yup=True,
        export_apply=False,
    )
    print("=" * 60)
    print(f"Done. Wrote {OUTPUT_GLB}")
    print(f"Clips: {', '.join(c for _, c in files_to_use)}")
    print("=" * 60)


if __name__ == "__main__":
    main()
