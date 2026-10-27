package br.com.lume.launcher.core

import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Original, deterministic scene geometry. Coordinates are local to the requested positive bounds. */
object ProceduralSceneRules {
    val themes = listOf("ocean", "mountain", "dunes", "mist", "hills", "night", "city", "whale", "airplane", "galaxy", "milkyway", "fish", "car", "balloon", "deer", "forest", "butterfly", "bee", "garden", "lighthouse")
    private val titles = arrayOf("Mar de aurora", "Montanhas serenas", "Dunas de luz", "Brumas do vale", "Colinas verdes", "Noite tranquila", "Luzes da cidade", "Canto das baleias", "Rotas no céu", "Jardim de galáxias", "Nossa Via Láctea", "Jardim de corais", "Estrada dourada", "Balões ao amanhecer", "Refúgio dos cervos", "Floresta encantada", "Jardim das borboletas", "Abelhas e flores", "Lago dos nenúfares", "Farol da serenidade")
    private val palettes = arrayOf(
        intArrayOf(0x16365A, 0xB4D4D3, 0x73A8B0, 0x568B9E, 0x345F7B, 0x173852, 0xE7D4AD),
        intArrayOf(0x28385C, 0xD3C3BD, 0x9B9FB0, 0x6F8099, 0x445B78, 0x283E5C, 0xF0D4B3),
        intArrayOf(0x3D566D, 0xE2C7A1, 0xD2B38F, 0xB89374, 0x886C58, 0x554D4D, 0xF5DCAC),
        intArrayOf(0x2B424D, 0xC0D2C8, 0xA4BBB2, 0x809E99, 0x547A7B, 0x315861, 0xD7E0CF),
        intArrayOf(0x234858, 0xC8D8BE, 0x94B69A, 0x6E987C, 0x477467, 0x2B5054, 0xE5DFB1),
        intArrayOf(0x101D3A, 0x4C6481, 0x4A6079, 0x354D6B, 0x223B59, 0x152A45, 0xDCE1CD)
    )
    data class Point(val x: Float, val y: Float)
    data class Geometry(val layers: List<List<Point>>, val sun: Point, val radius: Float, val stars: List<Point>)

    fun themeIndex(seed: Long): Int = Math.floorMod(seed, themes.size.toLong()).toInt()
    fun theme(seed: Long): String = themes[themeIndex(seed)]
    fun title(seed: Long): String = titles[themeIndex(seed)]
    fun palette(seed: Long): IntArray {
        val special = when (theme(seed)) {
            "whale", "fish" -> intArrayOf(0x397F91, 0x071C35, 0x6697A5, 0x467E95, 0x27526F, 0x103650, 0xD4E8DD)
            "galaxy" -> intArrayOf(0x080D26, 0x202348, 0x4A6079, 0x354D6B, 0x223B59, 0x152A45, 0xDCE1CD)
            "milkyway" -> intArrayOf(0x101B32, 0x30334B, 0x4A6079, 0x354D6B, 0x223B59, 0x152A45, 0xDCE1CD)
            else -> null
        }
        if (special != null) return special.map { it or 0xFF000000.toInt() }.toIntArray()
        val index = when (theme(seed)) {
            "city", "galaxy", "milkyway" -> 5
            "whale", "fish", "lighthouse" -> 0
            "airplane", "balloon" -> 1
            "car" -> 2
            "forest", "garden" -> 3
            "deer", "butterfly", "bee" -> 4
            else -> themeIndex(seed)
        }
        return palettes[index].map { it or 0xFF000000.toInt() }.toIntArray()
    }

    /** Visit every theme before repeating, while entropy changes each illustration's composition. */
    fun nextSeed(previous: Long?, random: Long): Long {
        if (previous == null) return random
        val safe = random.coerceIn(Long.MIN_VALUE + themes.size, Long.MAX_VALUE - themes.size)
        return safe - themeIndex(safe) + (themeIndex(previous) + 1) % themes.size
    }

    fun geometry(width: Int, height: Int, seed: Long): Geometry {
        require(width > 0 && height > 0)
        val random = Random(seed)
        val w = width.toFloat(); val h = height.toFloat()
        val theme = themeIndex(seed)
        val amplitude = when (theme) { 0 -> 0.006f; 1 -> 0.12f; 2 -> 0.06f; 3 -> 0.025f; 4 -> 0.05f; else -> 0.08f }
        val phase = random.nextFloat() * 6.28f
        val horizon = 0.40f + random.nextFloat() * 0.08f
        val layers = (0..3).map { layer ->
            (0..8).map { step ->
                val wave = sin(step * 0.75f + phase + layer) * amplitude
                val noise = (random.nextFloat() - 0.5f) * amplitude * 0.3f
                Point(w * (step / 8f), (h * (horizon + layer * 0.105f + wave + noise)).coerceIn(0f, h))
            }
        }
        val sun = Point(w * (0.18f + random.nextFloat() * 0.64f), h * (0.12f + random.nextFloat() * 0.18f))
        val stars = if (theme == 5) (0 until 16).map {
            Point(w * (0.06f + random.nextFloat() * 0.88f), h * (0.06f + random.nextFloat() * 0.28f))
        } else emptyList()
        return Geometry(layers, sun, min(w, h) * 0.045f, stars)
    }
}
