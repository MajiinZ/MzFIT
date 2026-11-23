"""Generates MzFIT muscle-map vector paths (viewBox 200x420) for front & back views.
Outputs: Kotlin source + preview SVGs. Points are authored for the left half (x<100) and mirrored."""
import json, sys

CX = 100.0

def catmull(points, closed=True, tension=0.5):
    pts = points
    n = len(pts)
    d = f"M{pts[0][0]:.1f},{pts[0][1]:.1f}"
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        p0 = pts[(i - 1) % n] if closed or i > 0 else pts[i]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n] if closed or i + 2 < n else p2
        t = tension / 3 * 2  # catmull-rom -> bezier factor (1/6 at tension .5*2)
        c1 = (p1[0] + (p2[0] - p0[0]) * t / 2, p1[1] + (p2[1] - p0[1]) * t / 2)
        c2 = (p2[0] - (p3[0] - p1[0]) * t / 2, p2[1] - (p3[1] - p1[1]) * t / 2)
        d += f"C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"
    if closed:
        d += "Z"
    return d

def mirror(points):
    return [(2 * CX - x, y) for x, y in reversed(points)]

def ellipse(cx, cy, rx, ry):
    return (f"M{cx-rx:.1f},{cy:.1f}A{rx:.1f},{ry:.1f} 0 1,0 {cx+rx:.1f},{cy:.1f}"
            f"A{rx:.1f},{ry:.1f} 0 1,0 {cx-rx:.1f},{cy:.1f}Z")

# ---------------- silhouette (half, top->bottom on left side, ends at crotch) -----
HALF_OUTLINE = [
    (91, 44), (90, 56), (80, 61), (66, 65), (56, 72), (51, 86), (49, 104), (47, 124),
    (45, 146), (41, 168), (37, 192), (35, 210), (31, 220), (29, 234), (34, 244), (41, 240),
    (45, 226), (49, 208), (55, 182), (60, 160), (64, 138), (68, 120), (70, 144), (71, 168),
    (71, 186), (69, 202), (67, 226), (67, 254), (69, 282), (73, 302), (73, 318), (71, 340),
    (73, 366), (77, 390), (75, 404), (83, 411), (92, 408), (91, 392), (92, 366), (94, 340),
    (95, 318), (96, 300), (98, 270), (99, 240), (100, 224),
]

def silhouette_path():
    left = HALF_OUTLINE
    right = mirror(left)
    # right starts at crotch mirrored (100,224) -> skip duplicate
    pts = left + right[1:-0 or None]
    return catmull(pts, closed=True, tension=0.42) + ellipse(100, 25, 16, 20)

# ---------------- muscles -------------------------------------------------------
FRONT = {
    "NECK": [[(92, 46), (96, 50), (99, 62), (95, 64), (91, 57)]],
    "TRAPS": [[(89, 55), (91, 61), (82, 65), (70, 66), (79, 60)]],
    "SIDE_DELTS": [[(63, 67), (56, 73), (52, 86), (51, 100), (56, 101), (58, 86), (64, 72)]],
    "FRONT_DELTS": [[(73, 67), (66, 69), (61, 78), (59, 92), (60, 101), (66, 94), (71, 84), (76, 73)]],
    "UPPER_CHEST": [[(98, 69), (88, 68), (78, 70), (73, 76), (72, 85), (84, 87), (98, 86)]],
    "LOWER_CHEST": [[(98, 89), (85, 90), (72, 88), (70, 97), (75, 106), (87, 111), (98, 109)]],
    "BICEPS": [[(59, 103), (54, 113), (52, 128), (53, 143), (58, 148), (63, 137), (66, 119), (65, 106)]],
    "FOREARMS": [[(50, 151), (45, 166), (41, 186), (40, 204), (43.5, 206), (47.5, 190), (51.5, 172), (55, 155)],
                 [(57.5, 152), (54, 172), (50, 192), (46.5, 208), (49.5, 210), (55, 192), (60, 170), (61, 156)]],
    "ABS": [[(89, 113), (98.5, 112), (98.5, 128), (89, 128.5)],
            [(89, 131.5), (98.5, 131), (98.5, 147), (89, 147.5)],
            [(89, 150.5), (98.5, 150), (98.5, 166), (89, 166.5)],
            [(89, 169.5), (98.5, 169), (98.5, 198), (94, 194), (90, 184)]],
    "OBLIQUES": [[(75, 114), (86, 117), (87, 150), (86, 181), (80, 187), (75, 168), (73.5, 140)]],
    "ADDUCTORS": [[(99, 212), (94.5, 216), (91.5, 230), (92, 250), (96, 253), (99, 236)]],
    "QUADS": [[(72, 205), (69, 230), (69, 258), (73, 282), (79, 296), (80, 270), (78, 240), (78, 213)],
              [(81, 207), (80, 236), (82, 266), (84, 286), (88, 283), (89.5, 256), (89, 226), (86, 210)],
              [(90, 260), (86, 284), (88, 299), (94, 297), (95, 280), (93, 263)]],
    "TIBIALIS": [[(76, 322), (74, 340), (76, 362), (80, 386), (83, 386), (82, 360), (81, 336), (80, 322)]],
    "CALVES": [[(88, 320), (86, 336), (87, 356), (91, 362), (94, 340), (94, 322)]],
}

BACK = {
    "TRAPS": [[(99, 44), (92, 52), (86, 61), (69, 67), (83, 74), (91, 92), (99, 114)]],
    "REAR_DELTS": [[(67, 67), (58, 72), (52, 84), (51, 99), (57, 100), (63, 86), (73, 74)]],
    "TRICEPS": [[(55, 103), (51, 117), (50, 134), (53, 147), (59, 147), (63, 131), (65, 112), (61, 103)]],
    "FOREARMS": [[(50, 151), (45, 166), (41, 186), (40, 204), (43.5, 206), (47.5, 190), (51.5, 172), (55, 155)],
                 [(57.5, 152), (54, 172), (50, 192), (46.5, 208), (49.5, 210), (55, 192), (60, 170), (61, 156)]],
    "MID_BACK": [[(83, 77), (76, 80), (72, 92), (80, 99), (90, 96), (89, 86)]],
    "LATS": [[(72, 96), (69, 114), (72, 140), (79, 162), (88, 173), (90, 150), (90, 124), (86, 104), (80, 101)]],
    "LOWER_BACK": [[(99, 118), (94, 120), (92, 148), (92, 178), (99, 188)]],
    "GLUTES": [[(99, 192), (88, 189), (76, 195), (70, 211), (72, 228), (82, 236), (94, 233), (99, 222)],
               [(73, 186), (69, 197), (72, 203), (80, 192), (86, 188)]],
    "HAMSTRINGS": [[(71, 238), (70, 260), (73, 284), (79, 300), (85, 300), (87, 276), (87, 251), (83, 240)],
                   [(89, 241), (89, 270), (91, 296), (96, 293), (98, 262), (97, 239)]],
    "CALVES": [[(75, 314), (72, 333), (74, 355), (80, 364), (84, 346), (84, 320)],
               [(86, 316), (86, 340), (88, 364), (93, 357), (95, 336), (93, 318)],
               [(80, 367), (81, 388), (88, 390), (90, 367), (85, 370)]],
}

def build(view):
    out = []
    for muscle, polys in view.items():
        for poly in polys:
            out.append((muscle, catmull(poly, tension=0.5)))
            out.append((muscle, catmull(mirror(poly), tension=0.5)))
    return out

PAL = {"NECK": "#7dd3fc", "TRAPS": "#f472b6", "SIDE_DELTS": "#fb923c", "FRONT_DELTS": "#facc15",
       "REAR_DELTS": "#fb7185", "UPPER_CHEST": "#ef4444", "LOWER_CHEST": "#dc2626", "BICEPS": "#22c55e",
       "TRICEPS": "#16a34a", "FOREARMS": "#14b8a6", "ABS": "#3b82f6", "OBLIQUES": "#6366f1",
       "ADDUCTORS": "#a855f7", "QUADS": "#d946ef", "TIBIALIS": "#84cc16", "CALVES": "#06b6d4",
       "MID_BACK": "#eab308", "LATS": "#f97316", "LOWER_BACK": "#8b5cf6", "GLUTES": "#ec4899",
       "HAMSTRINGS": "#10b981"}

def svg(view_paths):
    s = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 420" width="400" height="840" style="background:#0d1117">',
         f'<path d="{silhouette_path()}" fill="#262c38"/>']
    for m, d in view_paths:
        s.append(f'<path d="{d}" fill="{PAL[m]}" stroke="#0d1117" stroke-width="0.8"/>')
    s.append("</svg>")
    return "\n".join(s)

def kotlin(front, back):
    def lst(paths):
        return ",\n".join(f'        MuscleShape(Muscle.{m}, "{d}")' for m, d in paths)
    return f'''package org.mz.mzfit.ui.musclemap

import org.mz.mzfit.data.Muscle

/**
 * Auto-generated by tools/musclegen/gen.py — do not edit by hand.
 * Vector paths for the anatomical muscle map, in a 200 x 420 viewport.
 */
data class MuscleShape(val muscle: Muscle, val pathData: String)

object MuscleMapPaths {{
    const val VIEWPORT_WIDTH = 200f
    const val VIEWPORT_HEIGHT = 420f

    const val SILHOUETTE = "{silhouette_path()}"

    val front: List<MuscleShape> = listOf(
{lst(front)}
    )

    val back: List<MuscleShape> = listOf(
{lst(back)}
    )
}}
'''

if __name__ == "__main__":
    f, b = build(FRONT), build(BACK)
    open("front.svg", "w").write(svg(f))
    open("back.svg", "w").write(svg(b))
    if len(sys.argv) > 1:
        open(sys.argv[1], "w").write(kotlin(f, b))
    print("ok", len(f), len(b))
