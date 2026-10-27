package br.com.lume.launcher.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Bounded original vector illustrations in a 1000 × 562.5 design space. No images or I/O. */
data class ArtMark(val kind: String, val points: List<Float>, val color: Int, val stroke: Float = 0f, val opacity: Float = 1f)

object ProceduralArtwork {
    fun compose(seed: Long): List<ArtMark> {
        val r = Random(seed xor 0x456FAB23L)
        val marks = mutableListOf<ArtMark>()
        val ivory = 0xF4E7CE; val ink = 0x133748; val leaf = 0x316D5B
        fun mark(kind: String, color: Int, stroke: Float = 0f, alpha: Float = 1f, vararg p: Float) { marks.add(ArtMark(kind, p.toList(), color or 0xFF000000.toInt(), stroke, alpha)) }
        fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Float = 1f) = mark("ellipse", color, 0f, alpha, x, y, rx, ry)
        fun line(color: Int, width: Float, vararg p: Float) = mark("line", color, width, 1f, *p)
        fun poly(color: Int, vararg p: Float) = mark("polygon", color, 0f, 1f, *p)
        fun curve(color: Int, stroke: Float = 0f, alpha: Float = 1f, vararg p: Float) = mark("curve", color, stroke, alpha, *p)
        fun cloud(x: Float, y: Float, scale: Float) {
            repeat(5) { i -> oval(x + i * 20 * scale, y + sin(i.toFloat()) * 7 * scale, (22 + i % 2 * 9) * scale, 13 * scale, ivory, 0.16f) }
        }
        fun stars(count: Int) {
            repeat(count) { oval(r.nextFloat() * 1000, r.nextFloat() * 510, 0.5f + r.nextFloat() * 1.1f, 0.5f + r.nextFloat() * 1.1f, ivory, 0.3f + r.nextFloat() * 0.6f) }
        }
        fun tree(x: Float, ground: Float, height: Float, color: Int) {
            line(ink, height / 25, x, ground, x, ground - height)
            repeat(4) { i -> val y = ground - height * (0.98f - i * 0.18f); val w = height * (0.13f + i * 0.065f); poly(color, x, y, x - w, y + height * 0.33f, x + w, y + height * 0.33f) }
        }
        fun flower(x: Float, y: Float, scale: Float, color: Int) {
            curve(leaf, 1.4f * scale, 1f, x, y + 35 * scale, x - 7 * scale, y + 12 * scale, x + 5 * scale, y + 15 * scale, x, y)
            oval(x - 6 * scale, y + 21 * scale, 8 * scale, 3 * scale, leaf)
            repeat(7) { i -> val a = i * Math.PI * 2 / 7; oval(x + cos(a).toFloat() * 8 * scale, y + sin(a).toFloat() * 8 * scale, 6 * scale, 5 * scale, color) }
            oval(x, y, 4 * scale, 4 * scale, 0xEABD61)
        }
        fun fish(x: Float, y: Float, s: Float, color: Int) {
            poly(color, x - 25 * s, y, x - 47 * s, y - 20 * s, x - 47 * s, y + 20 * s)
            oval(x, y, 31 * s, 15 * s, color)
            poly(0xDAAA7C, x - 8 * s, y - 12 * s, x + 6 * s, y - 27 * s, x + 17 * s, y - 9 * s)
            repeat(3) { i -> curve(ivory, 1.1f * s, 0.5f, x - (12 - i * 10) * s, y - 11 * s, x - (18 - i * 10) * s, y - 4 * s, x - (18 - i * 10) * s, y + 6 * s, x - (12 - i * 10) * s, y + 11 * s) }
            oval(x + 19 * s, y - 3 * s, 2 * s, 2 * s, ink)
        }
        fun butterfly(x: Float, y: Float, s: Float, color: Int) {
            for (sign in listOf(-1f, 1f)) {
                curve(color, 0f, 1f, x, y, x + sign * 60 * s, y - 90 * s, x + sign * 92 * s, y + 5 * s, x, y + 8 * s)
                curve(color, 0f, 0.82f, x, y + 4 * s, x + sign * 70 * s, y + 8 * s, x + sign * 41 * s, y + 75 * s, x, y + 20 * s)
                repeat(4) { i -> line(ink, 1.1f * s, x, y + 7 * s, x + sign * (20 + i * 8) * s, y + (i * 12 - 26) * s) }
                repeat(5) { i -> oval(x + sign * (15 + i * 8) * s, y + (i * 4 - 25) * s, 2.5f * s, 3.5f * s, ivory, 0.8f) }
                line(ink, s, x, y, x + sign * 7 * s, y - 13 * s)
            }
            oval(x, y + 11 * s, 3 * s, 20 * s, ink)
        }
        when (ProceduralSceneRules.theme(seed)) {
            "city" -> {
                stars(90)
                repeat(3) { layer ->
                    var x = -20f
                    while (x < 1020) {
                        val w = 28 + r.nextFloat() * 45; val h = 30 + r.nextFloat() * (85 + layer * 37)
                        val base = 340f + layer * 67
                        poly(if (layer == 0) 0x53657B else if (layer == 1) 0x304C65 else 0x17364F, x, base, x, base - h, x + w, base - h, x + w, base)
                        line(0x91AFBB, 1f, x + 2, base - h, x + w - 2, base - h)
                        var wy = base - h + 9
                        while (wy < base - 4) { var wx = x + 7; while (wx < x + w - 4) { if (r.nextFloat() > 0.4f) oval(wx, wy, 1.4f, 2.1f, 0xEDC893, 0.65f); wx += 9 }; wy += 12 }
                        if (r.nextBoolean()) line(0x8099AD, 1f, x + w / 2, base - h, x + w / 2, base - h - 12)
                        x += w + 7
                    }
                }
                repeat(40) { i -> val x = r.nextFloat() * 1000; val y = 510 + r.nextFloat() * 45; line(0xD5B784, 1f, x, y, x + 8 + i % 17, y) }
            }
            "whale" -> {
                repeat(9) { i -> poly(0xA8D7D5, 110f + i * 90, 0f, 120f + i * 90, 0f, 50f + i * 105, 560f, 3f + i * 105, 560f); marks[marks.lastIndex] = marks.last().copy(opacity = 0.035f) }
                curve(0x436E8C, 0f, 1f, 280f, 290f, 350f, 182f, 611f, 212f, 675f, 325f, 581f, 371f, 377f, 391f, 280f, 290f)
                curve(0x9CBCC7, 0f, 1f, 305f, 313f, 400f, 356f, 592f, 351f, 675f, 325f, 558f, 406f, 368f, 379f, 305f, 313f)
                curve(0x355E7C, 0f, 1f, 629f, 310f, 713f, 325f, 721f, 224f, 791f, 233f, 775f, 269f, 744f, 305f, 741f, 313f, 790f, 300f, 824f, 328f, 819f, 353f, 769f, 345f, 714f, 343f, 661f, 347f)
                curve(0x2B506E, 0f, 1f, 443f, 326f, 440f, 403f, 530f, 412f, 516f, 345f)
                repeat(9) { i -> curve(0x739AAC, 1.1f, 0.8f, 335f + i * 18, 329f, 354f + i * 18, 347f, 371f + i * 18, 356f, 389f + i * 18, 354f) }
                oval(330f, 285f, 4f, 4f, ink); oval(331f, 284f, 1.2f, 1.2f, ivory)
                repeat(55) { oval(r.nextFloat() * 1000, r.nextFloat() * 560, 0.8f + r.nextFloat() * 2, 0.8f + r.nextFloat() * 2, ivory, 0.24f) }
                repeat(6) { fish(120f + it * 31, 430f + sin(it.toFloat()) * 20, 0.25f, 0x9AB8C7) }
            }
            "airplane" -> {
                repeat(16) { cloud(r.nextFloat() * 1000, 60 + r.nextFloat() * 340, 0.4f + r.nextFloat() * 2) }
                curve(ivory, 2f, 0.25f, 20f, 360f, 200f, 310f, 360f, 244f, 589f, 219f)
                curve(ivory, 2f, 0.18f, 10f, 370f, 194f, 322f, 360f, 252f, 589f, 225f)
                poly(0xD3E3E8, 550f, 220f, 721f, 208f, 750f, 216f, 719f, 224f, 550f, 232f)
                poly(0xADBFCF, 629f, 219f, 593f, 162f, 611f, 164f, 676f, 218f, 631f, 279f, 612f, 283f, 639f, 226f)
                poly(0x4A7592, 565f, 223f, 548f, 189f, 560f, 190f, 591f, 223f)
                repeat(13) { oval(603f + it * 8, 216f, 1.5f, 1.7f, 0x4F7089) }
                oval(690f, 231f, 12f, 4f, 0x6C8DA5)
            }
            "galaxy", "milkyway" -> {
                val milky = ProceduralSceneRules.theme(seed) == "milkyway"
                repeat(900) {
                    val distance = r.nextFloat()
                    val swirl = (it % 3) * 2.094f + distance * 5f + (r.nextFloat() - 0.5f) * 0.5f
                    val x = 500 + cos(swirl) * distance * 420
                    val y = 230 + sin(swirl) * distance * if (milky) 50 else 160
                    oval(x, y + (x - 500) * if (milky) -0.37f else 0.08f, 4 + distance * 15, 2 + distance * 7, if (it % 2 == 0) 0xA789BE else 0x87B7D3, 0.065f)
                }
                stars(330)
                repeat(90) { val x = r.nextFloat() * 900 + 50; oval(x, 230 - (x - 500) * 0.37f + (r.nextFloat() - 0.5f) * 80, 0.8f, 0.8f, ivory, 0.7f) }
                if (!milky) { repeat(12) { i -> oval(500f, 230f, 100f - i * 7, 36f - i * 2.5f, ivory, 0.045f) }; oval(500f, 230f, 21f, 7f, ivory, 0.35f) }
            }
            "fish" -> {
                repeat(20) { i ->
                    val x = i * 53f; val height = 45 + r.nextFloat() * 100
                    repeat(3) { branch -> curve(if (i % 2 == 0) 0xBB887F else 0x6CA6A0, 3f, 0.85f, x, 560f, x + 15, 540 - height * 0.3f, x - 20 + branch * 17, 550 - height * 0.8f, x - 28 + branch * 25, 550 - height) }
                }
                repeat(15) { fish(90 + r.nextFloat() * 820, 90 + r.nextFloat() * 320, 0.4f + r.nextFloat() * 0.8f, listOf(0xE5BB81, 0x8ABCB5, 0xC192AE)[it % 3]) }
                repeat(80) { oval(r.nextFloat() * 1000, r.nextFloat() * 550, 0.6f, 0.9f, ivory, 0.4f) }
            }
            "car" -> {
                poly(0x3B4248, 370f, 562f, 565f, 279f, 588f, 279f, 950f, 562f)
                line(0xC9C3AD, 2f, 390f, 562f, 568f, 280f); line(0xC9C3AD, 2f, 932f, 562f, 586f, 280f)
                repeat(8) { i -> val y = 305f + i * i * 4; line(ivory, 1 + i * 0.5f, 578 + i * 5f, y, 580 + i * 5f, y + 3 + i * 2f) }
                curve(0x507F87, 0f, 1f, 560f, 452f, 555f, 425f, 583f, 420f, 603f, 418f, 623f, 382f, 673f, 381f, 702f, 420f, 735f, 421f, 758f, 436f, 755f, 457f, 700f, 465f, 620f, 465f, 560f, 452f)
                poly(0xB4C7C8, 616f, 420f, 634f, 397f, 662f, 395f, 681f, 420f)
                line(0x345E68, 2f, 650f, 397f, 651f, 420f); line(ivory, 1f, 567f, 445f, 745f, 444f)
                for (x in listOf(601f, 715f)) { oval(x, 458f, 16f, 17f, 0x24363F); oval(x, 458f, 8f, 9f, 0x8DACA9); repeat(5) { val a = it * 1.256f; line(ink, 1f, x, 458f, x + cos(a) * 7, 458 + sin(a) * 8) } }
                oval(744f, 438f, 5f, 3f, ivory); oval(569f, 438f, 3f, 3f, 0xD88D6E)
                repeat(18) { tree(it * 65f, 450 + r.nextFloat() * 70, 25 + r.nextFloat() * 30, leaf) }
            }
            "balloon" -> {
                repeat(13) { cloud(r.nextFloat() * 1000, 70 + r.nextFloat() * 300, 0.6f) }
                repeat(7) { i ->
                    val x = 80 + r.nextFloat() * 840; val y = 100 + r.nextFloat() * 230; val s = 0.5f + r.nextFloat()
                    val color = listOf(0xCB9B90, 0xDDBA80, 0x8CAFB2)[i % 3]
                    curve(color, 0f, 1f, x, y + 43 * s, x - 90 * s, y - 30 * s, x + 90 * s, y - 30 * s, x, y + 43 * s)
                    repeat(5) { j -> curve(ivory, 0.8f * s, 0.65f, x, y + 40 * s, x + (j - 2) * 30 * s, y - 30 * s, x + (j - 2) * 10 * s, y - 18 * s, x, y - 12 * s) }
                    line(ink, s, x - 7 * s, y + 36 * s, x - 5 * s, y + 55 * s, x + 5 * s, y + 55 * s, x + 7 * s, y + 36 * s)
                    poly(0x8F7355, x - 6 * s, y + 53 * s, x + 6 * s, y + 53 * s, x + 4 * s, y + 61 * s, x - 4 * s, y + 61 * s)
                }
            }
            "deer" -> {
                repeat(30) { tree(it * 37f, 450 + r.nextFloat() * 80, 110 + r.nextFloat() * 160, if (it % 2 == 0) 0x3E7262 else 0x28594E) }
                for ((x, s) in listOf(510f to 1f, 670f to 0.65f)) {
                    oval(x, 438f, 48 * s, 22 * s, 0xB59D7B)
                    poly(0xB59D7B, x + 29 * s, 438f, x + 43 * s, 382f, x + 58 * s, 377f, x + 59 * s, 391f, x + 52 * s, 432f)
                    oval(x + 56 * s, 381f, 17 * s, 9 * s, 0xC8B28C)
                    for (dx in listOf(-30, -17, 20, 33)) line(0x9B866B, 4 * s, x + dx * s, 446f, x + (dx - 3) * s, 495f)
                    for (sign in listOf(-1, 1)) { line(0x9B866B, 2 * s, x + 48 * s, 380f, x + (48 + sign * 12) * s, 355f, x + (52 + sign * 15) * s, 339f); line(0x9B866B, s, x + (48 + sign * 8) * s, 365f, x + (58 + sign * 14) * s, 358f) }
                    oval(x + 62 * s, 379f, 1.5f, 1.5f, ink)
                    repeat(9) { oval(x + (r.nextFloat() - 0.5f) * 58 * s, 430 + r.nextFloat() * 13, 1.6f, 1.4f, ivory, 0.8f) }
                }
            }
            "forest" -> {
                repeat(4) { layer -> repeat(23) { tree(it * 48f + r.nextFloat() * 22, 320f + layer * 75, 90 + r.nextFloat() * (120 + layer * 30), listOf(0x789A8D, 0x527F70, 0x356759, 0x1F4D43)[layer]) } }
                repeat(70) { oval(r.nextFloat() * 1000, 240 + r.nextFloat() * 300, 1f, 1f, 0xE2D6A0, 0.65f) }
            }
            "butterfly" -> {
                repeat(46) { flower(r.nextFloat() * 1000, 360 + r.nextFloat() * 180, 0.5f + r.nextFloat() * 0.7f, listOf(0xBB98B8, 0xD7B49D, 0x9BBFC0)[it % 3]) }
                repeat(9) { butterfly(90 + r.nextFloat() * 820, 100 + r.nextFloat() * 270, 0.3f + r.nextFloat() * 0.5f, listOf(0xB7A3CF, 0xDBB187, 0x91BFC6)[it % 3]) }
            }
            "bee" -> {
                repeat(26) { flower(r.nextFloat() * 1000, 250 + r.nextFloat() * 260, 0.6f + r.nextFloat() * 1.3f, listOf(0xD8C69C, 0xC4B0B6, 0xE0D4B8)[it % 3]) }
                repeat(9) {
                    val x = 90 + r.nextFloat() * 820; val y = 110 + r.nextFloat() * 230; val s = 0.4f + r.nextFloat() * 0.6f
                    oval(x - 3 * s, y - 11 * s, 13 * s, 7 * s, ivory, 0.55f); oval(x + 8 * s, y - 10 * s, 13 * s, 7 * s, ivory, 0.5f)
                    oval(x, y, 16 * s, 9 * s, 0xD8B56C)
                    repeat(3) { i -> line(ink, 3 * s, x - (7 - i * 7) * s, y - 7 * s, x - (7 - i * 7) * s, y + 7 * s) }
                    oval(x + 16 * s, y - s, 6 * s, 6 * s, ink)
                    for (j in 0..3) line(ink, s, x + (j * 5 - 9) * s, y + 5 * s, x + (j * 6 - 11) * s, y + 13 * s)
                }
            }
            "garden" -> {
                repeat(60) { val x = r.nextFloat() * 1000; val y = 330 + r.nextFloat() * 220; line(0x98BBB0, 0.7f, x - 18, y + 5, x + 24, y + 5) }
                repeat(32) { val x = r.nextFloat() * 1000; val y = 330 + r.nextFloat() * 200; oval(x, y, 12 + r.nextFloat() * 17, 4 + r.nextFloat() * 6, leaf); if (it % 3 == 0) flower(x, y - 5, 0.7f, 0xD9BAC8) }
                repeat(20) { tree(it * 55f, 312f, 45 + r.nextFloat() * 70, 0x406F62) }
            }
            "lighthouse" -> {
                repeat(10) { cloud(r.nextFloat() * 1000, 35 + r.nextFloat() * 170, 0.4f + r.nextFloat()) }
                poly(0x345667, 630f, 562f, 685f, 382f, 750f, 342f, 867f, 362f, 1000f, 562f)
                poly(ivory, 742f, 380f, 756f, 222f, 790f, 222f, 806f, 380f)
                for (y in listOf(260f, 310f)) poly(0xAE8781, 749f, y, 797f, y, 799f, y + 18, 747f, y + 18)
                poly(ink, 747f, 223f, 747f, 200f, 797f, 200f, 797f, 223f)
                poly(0x849CAC, 740f, 200f, 773f, 178f, 806f, 200f)
                oval(773f, 211f, 6f, 8f, 0xF4D8A2)
                poly(ivory, 766f, 207f, 170f, 123f, 30f, 280f, 766f, 216f); marks[marks.lastIndex] = marks.last().copy(opacity = 0.065f)
                repeat(18) { i -> val y = 340 + i * 12f + r.nextFloat() * 5; line(0x9EBABC, 0.8f, 30f + i * 5, y, 420f + i * 8 + r.nextFloat() * 25, y) }
            }
            else -> {
                repeat(8) { cloud(r.nextFloat() * 1000, 35 + r.nextFloat() * 170, 0.3f + r.nextFloat()) }
                when (ProceduralSceneRules.theme(seed)) {
                    "night" -> stars(200)
                    "ocean" -> repeat(75) { val y = 310 + r.nextFloat() * 240; val x = r.nextFloat() * 1000; line(0xB4CECC, 0.6f, x, y, x + 5 + r.nextFloat() * 28, y) }
                    "mountain" -> repeat(12) { val x = it * 90f; poly(0xD6D6CE, x, 319f, x + 32, 264f + r.nextFloat() * 27, x + 58, 315f, x + 33, 298f) }
                    "hills" -> repeat(50) { flower(r.nextFloat() * 1000, 420 + r.nextFloat() * 100, 0.3f, 0xD3C7AB) }
                    "dunes" -> repeat(28) { i -> curve(0xD9BC98, 0.6f, 0.35f, 0f, 350 + i * 8f, 300f, 240 + i * 10f, 600f, 500 + i * 4f, 1000f, 350 + i * 9f) }
                    else -> repeat(20) { tree(it * 55f, 440 + r.nextFloat() * 85, 20 + r.nextFloat() * 40, leaf) }
                }
            }
        }
        return marks
    }
}
