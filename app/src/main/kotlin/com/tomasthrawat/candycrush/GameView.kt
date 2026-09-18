
package com.tomasthrawat.candycrush

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.util.ArrayDeque

class GameView(context: Context) : View(context) {
    private var framePosted = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            invalidate()
        }
    }
    private fun requestFrame() {
        if (!framePosted) {
            framePosted = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    companion object {
        private const val N = 8
        private const val TYPES = 6
        private const val MAX_LEVEL = 40

        private const val NONE = 0
        private const val H_STRIPE = 1
        private const val V_STRIPE = 2
        private const val WRAPPED = 3
        private const val COLOR_BOMB = 4

        private const val IDLE = 0
        private const val SWAP = 1
        private const val CLEAR = 2
        private const val FALL = 3
    }

    private enum class Screen { MENU, LEVELS, GAME, SHOP, HELP, SETTINGS }
    private data class Candy(var type: Int, var special: Int = NONE)
    private data class Cell(val r: Int, val c: Int)
    private data class Swap(val a: Cell, val b: Cell)
    private data class Group(val cells: List<Cell>, val horizontal: Boolean)
    private data class MatchInfo(
        val cells: MutableSet<Cell>,
        val groups: List<Group>,
        val crossings: Set<Cell>
    )
    private data class Particle(
        var x: Float, var y: Float,
        val vx: Float, val vy: Float,
        val radius: Float, var life: Float
    )

    private val prefs = context.getSharedPreferences("sweet_match", Context.MODE_PRIVATE)
    private val board = Array(N) { arrayOfNulls<Candy>(N) }
    private val jelly = Array(N) { BooleanArray(N) }
    private val fallOffset = Array(N) { FloatArray(N) }
    private val particles = mutableListOf<Particle>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 72)

    private var screen = Screen.MENU
    private var level = 1
    private var score = 0
    private var moves = 30
    private var target = 600
    private var combo = 0
    private var unlockedLevel = prefs.getInt("unlocked_level", 1).coerceIn(1, MAX_LEVEL)

    private var coins = prefs.getInt("coins", 0)
    private var hammer = prefs.getInt("hammer", 2)
    private var shuffle = prefs.getInt("shuffle", 1)

    private var jellyTarget = 0
    private var jellyRemaining = 0

    private var phase = IDLE
    private var phaseStarted = 0L
    private var swapMove: Swap? = null
    private var swapValid = false
    private var clearCells = mutableSetOf<Cell>()
    private var specialCell: Cell? = null
    private var specialType = NONE
    private var comboCount = 0

    private var selectedR = -1
    private var selectedC = -1
    private var downX = 0f
    private var downY = 0f
    private var downInBoard = false
    private var swipeHandled = false

    private var levelScroll = 0f
    private var lastActionAt = System.currentTimeMillis()
    private var lastFrame = System.currentTimeMillis()

    private var msg = ""
    private var msgUntil = 0L

    private var fps = prefs.getString("fps", "60") ?: "60"
    private var graphics = prefs.getString("graphics", "HIGH") ?: "HIGH"
    private var shadows = prefs.getBoolean("shadows", true)
    private var sound = prefs.getBoolean("sound", true)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isFocusable = true
    }

    override fun onDetachedFromWindow() {
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrame).coerceIn(0L, 48L)).toFloat() / 1000f
        lastFrame = now

        drawBackground(c)

        when (screen) {
            Screen.MENU -> drawMenu(c)
            Screen.LEVELS -> drawLevels(c)
            Screen.GAME -> drawGame(c, now)
            Screen.SHOP -> drawShop(c)
            Screen.HELP -> drawHelp(c)
            Screen.SETTINGS -> drawSettings(c)
        }

        updateParticles(dt)
        drawParticles(c)

        if (msgUntil > now) {
            drawText(c, msg, width * 0.5f, height * 0.965f, width * 0.033f, 0xFF6E5366.toInt(), true)
            requestFrame()
        }

        advancePhase(now)
        if (screen == Screen.GAME && phase != IDLE) requestFrame()
        if (screen == Screen.GAME && phase == IDLE && now - lastActionAt > 7000L) requestFrame()
    }

    private fun drawBackground(c: Canvas) {
        val g = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            0xFFFFFCF8.toInt(), 0xFFFFEAF3.toInt(), Shader.TileMode.CLAMP
        )
        paint.shader = g
        paint.style = Paint.Style.FILL
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawMenu(c: Canvas) {
        drawPanel(c, width * .10f, height * .07f, width * .90f, height * .20f)
        drawText(c, "CANDY FAMILY", width * .50f, height * .135f, width * .073f, 0xFF7B4C63.toInt(), true)
        drawText(c, "MATCH • COMBO • CASCADE", width * .50f, height * .175f, width * .025f, 0xFF9D7287.toInt(), true)

        drawText(c, "LEVEL " + level, width * .30f, height * .255f, width * .030f, 0xFF76566A.toInt(), true)
        drawText(c, "COINS " + coins, width * .70f, height * .255f, width * .030f, 0xFFB57920.toInt(), true)

        menuButton(c, .14f, .32f, .86f, .41f, "PLAY", 0xFFE85D8D.toInt())
        menuButton(c, .14f, .43f, .86f, .52f, "LEVELS", 0xFF7A67D7.toInt())
        menuButton(c, .14f, .54f, .86f, .63f, "SHOP", 0xFF4FA8B1.toInt())
        menuButton(c, .14f, .65f, .86f, .74f, "SETTINGS", 0xFF668BD7.toInt())
        menuButton(c, .14f, .76f, .86f, .85f, "HOW TO PLAY", 0xFFB67BC7.toInt())

        drawText(c, "Swipe or tap two adjacent candies", width * .5f, height * .92f, width * .026f, 0xFF927184.toInt(), false)
    }

    private fun drawLevels(c: Canvas) {
        drawText(c, "LEVELS", width * .5f, height * .07f, width * .060f, 0xFF7B4C63.toInt(), true)
        drawText(c, "Complete each level to unlock the next", width * .5f, height * .115f, width * .026f, 0xFF927184.toInt(), false)

        val top = height * .16f
        val rowH = height * .095f
        val rows = (MAX_LEVEL + 3) / 4
        val maxScroll = max(0f, top + rows * rowH - height * .87f)
        levelScroll = levelScroll.coerceIn(0f, maxScroll)

        for (row in 0 until rows) {
            val y = top + row * rowH - levelScroll
            if (y > height * .88f || y + rowH < top) continue

            for (col in 0..3) {
                val n = row * 4 + col + 1
                if (n > MAX_LEVEL) continue
                val l = width * .055f + col * width * .235f
                val r = l + width * .19f
                val unlocked = n <= unlockedLevel
                val current = n == level
                val fill = when {
                    current -> 0xFFE85D8D.toInt()
                    unlocked -> 0xFFFFEEF4.toInt()
                    else -> 0xFFEDE6EB.toInt()
                }
                drawPill(c, l, y, r, y + height * .067f, fill)
                if (unlocked) {
                    drawText(
                        c, n.toString(), (l + r) * .5f, y + height * .045f,
                        width * .032f, if (current) Color.WHITE else 0xFF7B4C63.toInt(), true
                    )
                } else {
                    drawLock(c, (l + r) * .5f, y + height * .031f, min(width, height) * .014f)
                }
            }
        }

        menuButton(c, .22f, .91f, .78f, .975f, "BACK", 0xFF8E7180.toInt())
    }

    private fun drawSettings(c: Canvas) {
        drawText(c, "SETTINGS", width * .5f, height * .08f, width * .058f, 0xFF7B4C63.toInt(), true)
        drawText(c, "VIDEO", width * .5f, height * .145f, width * .033f, 0xFF927184.toInt(), true)

        settingsButton(c, .14f, .19f, .86f, .265f, "FPS", fps)
        settingsButton(c, .14f, .285f, .86f, .36f, "GRAPHICS", graphics)
        settingsButton(c, .14f, .38f, .86f, .455f, "SHADOWS", if (shadows) "ON" else "OFF")
        settingsButton(c, .14f, .475f, .86f, .55f, "SOUND", if (sound) "ON" else "OFF")

        drawText(c, "Tap a row to change it", width * .5f, height * .61f, width * .026f, 0xFF927184.toInt(), false)
        menuButton(c, .20f, .77f, .80f, .84f, "RESET PROGRESS", 0xFFD17C83.toInt())
        menuButton(c, .20f, .86f, .80f, .93f, "BACK", 0xFF8E7180.toInt())
    }

    private fun drawShop(c: Canvas) {
        drawText(c, "SHOP", width * .5f, height * .08f, width * .061f, 0xFF7B4C63.toInt(), true)
        drawText(c, "COINS " + coins, width * .5f, height * .135f, width * .033f, 0xFFB57920.toInt(), true)

        shopItem(c, .14f, .19f, "HAMMER", "Break one candy", hammer, 30, false)
        shopItem(c, .14f, .39f, "SHUFFLE", "Rebuild the board", shuffle, 45, true)

        drawText(c, "Helpers do not use moves", width * .5f, height * .64f, width * .026f, 0xFF927184.toInt(), false)
        drawHelpIcon(c, width * .24f, height * .72f)
        drawText(c, "HAMMER", width * .24f, height * .78f, width * .024f, 0xFF745367.toInt(), true)
        drawShuffleIcon(c, width * .76f, height * .72f)
        drawText(c, "SHUFFLE", width * .76f, height * .78f, width * .024f, 0xFF745367.toInt(), true)

        menuButton(c, .20f, .88f, .80f, .95f, "BACK", 0xFF8E7180.toInt())
    }

    private fun drawHelp(c: Canvas) {
        drawText(c, "HOW TO PLAY", width * .5f, height * .08f, width * .056f, 0xFF7B4C63.toInt(), true)
        val lines = listOf(
            "1. Swipe a candy into an adjacent cell.",
            "2. Or tap two adjacent candies.",
            "3. A normal move must create a 3+ match.",
            "4. Invalid swaps return automatically.",
            "5. Four- and five-match patterns create powers.",
            "6. Powers trigger when matched or activated.",
            "7. Cascades earn combo bonus points.",
            "8. Clear jelly and reach the score target."
        )
        lines.forEachIndexed { i, s ->
            drawText(c, s, width * .5f, height * (.17f + i * .074f), width * .026f, 0xFF745367.toInt(), false)
        }
        menuButton(c, .20f, .84f, .80f, .91f, "BACK", 0xFF8E7180.toInt())
    }

    private fun drawGame(c: Canvas, now: Long) {
        val complete = levelComplete()
        val failed = !complete && moves <= 0 && phase == IDLE && clearCells.isEmpty()

        drawText(c, "LEVEL " + level, width * .13f, height * .055f, width * .031f, 0xFF7B4C63.toInt(), true)
        drawText(c, score.toString() + " / " + target, width * .50f, height * .055f, width * .031f, 0xFF745367.toInt(), true)
        drawText(c, moves.toString() + " MOVES", width * .87f, height * .055f, width * .028f, 0xFF745367.toInt(), true)

        val cell = boardCell()
        val left = boardLeft()
        val top = boardTop()

        drawPanel(
            c,
            left - cell * .12f, top - cell * .12f,
            left + N * cell + cell * .12f, top + N * cell + cell * .12f
        )

        val objective = if (jellyTarget > 0) {
            "JELLY " + (jellyTarget - jellyRemaining) + "/" + jellyTarget
        } else {
            "SCORE TARGET"
        }
        drawText(c, objective, width * .5f, top - cell * .18f, width * .024f, 0xFF927184.toInt(), true)

        drawBoard(c, left, top, cell, now)

        helperButton(c, .05f, .83f, .30f, .92f, "HAMMER", hammer, false)
        helperButton(c, .34f, .83f, .66f, .92f, "SHUFFLE", shuffle, true)
        menuButton(c, .71f, .83f, .95f, .92f, "LOBBY", 0xFF8E7180.toInt())

        if (phase == IDLE && !complete && !failed && selectedR in 0 until N && selectedC in 0 until N) {
            drawSelection(
                c,
                left + selectedC * cell + cell * .5f,
                top + selectedR * cell + cell * .5f,
                cell * .41f
            )
        }

        if (phase == IDLE && !complete && !failed && now - lastActionAt > 7000L) {
            val hint = findHint()
            if (hint != null) {
                val pulse = .62f + .24f * ((now % 800L).toFloat() / 800f)
                drawHint(c, left + hint.a.c * cell + cell * .5f, top + hint.a.r * cell + cell * .5f, cell * .40f, pulse)
                drawHint(c, left + hint.b.c * cell + cell * .5f, top + hint.b.r * cell + cell * .5f, cell * .40f, pulse)
            }
        }

        if (complete || failed) {
            drawOverlay(c, if (complete) "LEVEL COMPLETE" else "OUT OF MOVES", if (complete) {
                "+" + rewardCoins() + " COINS"
            } else {
                "TRY AGAIN"
            })
        }
    }

    private fun drawBoard(c: Canvas, left: Float, top: Float, cell: Float, now: Long) {
        for (r in 0 until N) {
            for (col in 0 until N) {
                val x = left + col * cell
                val y = top + r * cell
                drawPill(c, x + 1f, y + 1f, x + cell - 1f, y + cell - 1f, 0x3DFFFFFF.toInt())

                val candy = board[r][col] ?: continue
                var cx = x + cell * .5f
                var cy = y + cell * .5f
                var scale = 1f

                when (phase) {
                    SWAP -> {
                        val s = swapMove
                        if (s != null) {
                            val p = easeInOut(phaseProgress(now, 170L))
                            if (r == s.a.r && col == s.a.c) {
                                cx += (s.b.c - s.a.c) * cell * p
                                cy += (s.b.r - s.a.r) * cell * p
                            } else if (r == s.b.r && col == s.b.c) {
                                cx += (s.a.c - s.b.c) * cell * p
                                cy += (s.a.r - s.b.r) * cell * p
                            }
                        }
                    }
                    CLEAR -> if (clearCells.contains(Cell(r, col))) {
                        scale = 1f - phaseProgress(now, 180L)
                    }
                    FALL -> {
                        val p = phaseProgress(now, 180L)
                        cy -= fallOffset[r][col] * cell * (1f - p)
                    }
                }

                if (shadows && graphics != "LOW" && scale > .05f) {
                    solid(0x28000000)
                    c.drawCircle(cx + cell * .03f, cy + cell * .05f, cell * .28f * scale, paint)
                }

                if (scale > .02f) {
                    drawCandy(c, cx, cy, cell * .34f * scale, candy)
                    if (jelly[r][col]) drawJelly(c, cx, cy, cell * .37f)
                }
            }
        }
    }


    private fun candyBase(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * .055f
        p.color = 0x33000000
        c.drawCircle(x, y, r * .94f, p)
        p.style = Paint.Style.FILL
        p.color = 0x55FFFFFF
        c.drawOval(x-r*.52f,y-r*.64f,x-r*.08f,y-r*.25f,p)
    }

    private fun drawCandy(c: Canvas, x: Float, y: Float, r: Float, candy: Candy) {
        when (candy.type) {
            STRAWBERRY -> drawStrawberry(c,x,y,r)
            LEMON -> drawLemon(c,x,y,r)
            BERRY -> drawBerry(c,x,y,r)
            APPLE -> drawApple(c,x,y,r)
            FLOWER -> drawFlower(c,x,y,r)
            COOKIE -> drawCookie(c,x,y,r)
            WRAPPED -> drawStripe(c,x,y,r,true)
            GEM -> drawColorMark(c,x,y,r)
            ORANGE -> drawOrange(c,x,y,r)
            DONUT -> drawDonut(c,x,y,r)
            else -> candyBase(c,x,y,r,0xFFEF5A86.toInt())
        }
        if (candy.special == STRIPED_H || candy.special == STRIPED_V) drawStripe(c,x,y,r,candy.special==STRIPED_H)
        if (candy.special == WRAPPED_SPECIAL) drawWrappedMark(c,x,y,r)
        if (candy.special == COLOR_SPECIAL) drawColorMark(c,x,y,r)
    }

    private fun drawStrawberry(c: Canvas,x:Float,y:Float,r:Float) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG); p.color=0xFFF44763.toInt()
        val path=Path().apply{moveTo(x,y+r*.82f); cubicTo(x-r*.92f,y+r*.12f,x-r*.78f,y-r*.68f,x,y-r*.55f); cubicTo(x+r*.78f,y-r*.68f,x+r*.92f,y+r*.12f,x,y+r*.82f); close()}
        c.drawPath(path,p)
        p.color=0xFF4CAF62.toInt(); val leaf=Path().apply{moveTo(x,y-r*.5f); lineTo(x-r*.55f,y-r*.92f); lineTo(x-r*.08f,y-r*.74f); lineTo(x,y-r*1.02f); lineTo(x+r*.12f,y-r*.72f); lineTo(x+r*.58f,y-r*.9f); close()}; c.drawPath(leaf,p)
        p.color=0xFFFFD36B.toInt(); for(i in -1..1) c.drawOval(x+i*r*.25f-r*.035f,y-r*.15f,x+i*r*.25f+r*.035f,y+r*.02f,p)
        p.color=0x66FFFFFF; c.drawOval(x-r*.55f,y-r*.4f,x-r*.2f,y-r*.05f,p)
    }
    private fun drawLemon(c: Canvas,x:Float,y:Float,r:Float){ val p=Paint(1); p.color=0xFFFFD84D.toInt(); val q=Path().apply{moveTo(x-r*.95f,y);cubicTo(x-r*.7f,y-r*.7f,x+r*.55f,y-r*.78f,x+r*.95f,y);cubicTo(x+r*.55f,y+r*.78f,x-r*.7f,y+r*.7f,x-r*.95f,y);close()};c.drawPath(q,p);p.color=0x55FFFFFF;c.drawOval(x-r*.55f,y-r*.45f,x-r*.12f,y-r*.05f,p)}
    private fun drawBerry(c: Canvas,x:Float,y:Float,r:Float){candyBase(c,x,y,r,0xFF6874E8.toInt());val p=Paint(1);p.color=0xFF3F51B5.toInt();c.drawCircle(x-r*.3f,y-r*.55f,r*.08f,p);c.drawCircle(x+r*.05f,y-r*.62f,r*.08f,p);c.drawCircle(x+r*.38f,y-r*.48f,r*.08f,p)}
    private fun drawApple(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFF04F5F.toInt();val q=Path().apply{moveTo(x,y+r*.8f);cubicTo(x-r*.9f,y+r*.45f,x-r*.78f,y-r*.55f,x-r*.18f,y-r*.62f);cubicTo(x,y-r*.8f,x+r*.08f,y-r*.8f,x+r*.2f,y-r*.6f);cubicTo(x+r*.8f,y-r*.55f,x+r*.88f,y+r*.45f,x,y+r*.8f);close()};c.drawPath(q,p);p.color=0xFF4CAF62.toInt();c.drawOval(x+r*.02f,y-r*.95f,x+r*.58f,y-r*.6f,p);p.color=0xFF7A4E32.toInt();c.drawRoundRect(x-r*.04f,y-r*.82f,x+r*.08f,y-r*.52f,r*.05f,r*.05f,p)}
    private fun drawFlower(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFFF83B5.toInt();for(i in 0..5){val a=Math.toRadians(i*60.0);c.drawCircle(x+cos(a).toFloat()*r*.48f,y+sin(a).toFloat()*r*.48f,r*.42f,p)};p.color=0xFFFFD34E.toInt();c.drawCircle(x,y,r*.27f,p)}
    private fun drawCookie(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFC98A52.toInt();c.drawCircle(x,y,r*.86f,p);p.color=0xFF754A2E.toInt();for(i in 0..5){val a=i*1.047f;c.drawCircle(x+cos(a)*r*.45f,y+sin(a)*r*.45f,r*.09f,p)}}
    private fun drawStripe(c: Canvas,x:Float,y:Float,r:Float,horizontal:Boolean){candyBase(c,x,y,r,0xFFFF6FA3.toInt());val p=Paint(1);p.color=0xFFFFF5FA.toInt();p.strokeWidth=r*.12f;for(i in -1..1){if(horizontal)c.drawLine(x-r*.65f,y+i*r*.25f,x+r*.65f,y+i*r*.25f,p)else c.drawLine(x+i*r*.25f,y-r*.65f,x+i*r*.25f,y+r*.65f,p)}}
    private fun drawWrappedMark(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFFFD34E.toInt();c.drawCircle(x,y,r*.25f,p);p.color=0xFFFFF2B0.toInt();c.drawCircle(x,y,r*.1f,p)}
    private fun drawColorMark(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFFFFFFF.toInt();for(i in 0..7){val a=i*Math.PI/4;c.drawLine(x,y,x+cos(a).toFloat()*r*.75f,y+sin(a).toFloat()*r*.75f,p)}}
    private fun drawOrange(c: Canvas,x:Float,y:Float,r:Float){candyBase(c,x,y,r*.9f,0xFFFF9F32.toInt());val p=Paint(1);p.color=0xFF4CAF62.toInt();c.drawOval(x-r*.05f,y-r*.98f,x+r*.5f,y-r*.65f,p);p.color=0x55FFFFFF;c.drawOval(x-r*.5f,y-r*.55f,x-r*.1f,y-r*.12f,p)}
    private fun drawDonut(c: Canvas,x:Float,y:Float,r:Float){val p=Paint(1);p.color=0xFFD58B5C.toInt();c.drawCircle(x,y,r*.88f,p);p.color=0xFFFF79AA.toInt();c.drawCircle(x,y,r*.66f,p);p.color=0xFF7B4C35.toInt();c.drawCircle(x,y,r*.22f,p);p.color=0xFFFFFFFF.toInt();for(i in 0..5){val a=i*1.047f;c.drawRoundRect(x+cos(a)*r*.4f-r*.035f,y+sin(a)*r*.4f-r*.1f,x+cos(a)*r*.4f+r*.035f,y+sin(a)*r*.4f+r*.1f,.03f,.03f,p)}}
    private fun drawJelly(c: Canvas, x: Float, y: Float, r: Float) {
        solid(0x558C6DFF.toInt())
        c.drawCircle(x, y, r * .98f, paint)
        outline(0x88946ED1.toInt(), r * .035f)
        c.drawCircle(x, y, r * .98f, paint)
        solid(0x66FFFFFF)
        c.drawOval(x - r * .58f, y - r * .55f, x - r * .12f, y - r * .16f, paint)
        finishPaint()
    }

    private fun drawSelection(c: Canvas, x: Float, y: Float, r: Float) {
        outline(0xFFE85D8D.toInt(), r * .095f)
        c.drawCircle(x, y, r, paint)
        finishPaint()
    }

    private fun drawHint(c: Canvas, x: Float, y: Float, r: Float, alpha: Float) {
        outline(Color.argb((alpha * 255f).toInt(), 255, 196, 60), r * .060f)
        c.drawCircle(x, y, r, paint)
        finishPaint()
    }

    private fun drawOverlay(c: Canvas, title: String, subtitle: String) {
        solid(0xE82A1D2B.toInt())
        c.drawRoundRect(width * .10f, height * .31f, width * .90f, height * .69f, 28f, 28f, paint)
        drawText(c, title, width * .5f, height * .425f, width * .052f, Color.WHITE, true)
        drawText(c, subtitle, width * .5f, height * .50f, width * .038f, 0xFFFFD77E.toInt(), true)
        menuButton(c, .20f, .57f, .80f, .65f, if (title == "LEVEL COMPLETE") "CONTINUE" else "RETRY", 0xFFE85D8D.toInt())
        menuButton(c, .20f, .665f, .80f, .735f, "LEVELS", 0xFF8E7180.toInt())
    }

    private fun menuButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, color: Int) {
        val x1 = width * l
        val y1 = height * t
        val x2 = width * r
        val y2 = height * b
        drawPill(c, x1, y1, x2, y2, color)
        val size = min(width * .033f, (y2 - y1) * .40f)
        drawText(c, label, (x1 + x2) * .5f, (y1 + y2) * .5f + size * .35f, size, Color.WHITE, true)
    }

    private fun settingsButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, key: String, value: String) {
        val x1 = width * l
        val y1 = height * t
        val x2 = width * r
        val y2 = height * b
        drawPanel(c, x1, y1, x2, y2)
        drawText(c, key, x1 + (x2 - x1) * .20f, y1 + (y2 - y1) * .64f, width * .029f, 0xFF745367.toInt(), true)
        drawText(c, value, x1 + (x2 - x1) * .78f, y1 + (y2 - y1) * .64f, width * .029f, 0xFFD25B83.toInt(), true)
    }

    private fun shopItem(c: Canvas, l: Float, t: Float, title: String, desc: String, count: Int, price: Int, isShuffle: Boolean) {
        val x1 = width * l
        val y1 = height * t
        val x2 = width * (1f - l)
        val y2 = height * (t + .15f)
        drawPanel(c, x1, y1, x2, y2)

        if (isShuffle) drawShuffleIcon(c, x1 + (x2 - x1) * .18f, (y1 + y2) * .5f)
        else drawHelpIcon(c, x1 + (x2 - x1) * .18f, (y1 + y2) * .5f)

        drawText(c, title, x1 + (x2 - x1) * .44f, y1 + (y2 - y1) * .37f, width * .029f, 0xFF745367.toInt(), true)
        drawText(c, desc, x1 + (x2 - x1) * .48f, y1 + (y2 - y1) * .62f, width * .023f, 0xFF927184.toInt(), false)
        drawText(c, "OWNED " + count, x1 + (x2 - x1) * .78f, y1 + (y2 - y1) * .29f, width * .022f, 0xFFB57920.toInt(), true)
        menuButton(c, .62f, t + .085f, .93f, t + .145f, price.toString() + " COINS", 0xFF8E7180.toInt())
    }

    private fun helperButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, title: String, count: Int, isShuffle: Boolean) {
        val x1 = width * l
        val y1 = height * t
        val x2 = width * r
        val y2 = height * b
        drawPill(c, x1, y1, x2, y2, 0xFFF7EAF1.toInt())

        if (isShuffle) drawShuffleIcon(c, x1 + (x2 - x1) * .22f, (y1 + y2) * .5f)
        else drawHelpIcon(c, x1 + (x2 - x1) * .22f, (y1 + y2) * .5f)

        drawText(c, title, x1 + (x2 - x1) * .57f, y1 + (y2 - y1) * .44f, width * .021f, 0xFF745367.toInt(), true)
        drawText(c, count.toString(), x1 + (x2 - x1) * .57f, y1 + (y2 - y1) * .77f, width * .021f, 0xFFD25B83.toInt(), true)
    }

    private fun drawHelpIcon(c: Canvas, x: Float, y: Float) {
        solid(0xFFE5A047.toInt())
        c.drawRoundRect(x - width * .025f, y - height * .015f, x + width * .025f, y + height * .015f, 8f, 8f, paint)
        solid(0xFF6C4634.toInt())
        c.drawRoundRect(x - width * .034f, y - height * .05f, x - width * .020f, y - height * .015f, 5f, 5f, paint)
        c.drawRoundRect(x + width * .020f, y - height * .05f, x + width * .034f, y - height * .015f, 5f, 5f, paint)
        c.save()
        c.rotate(-28f, x, y)
        solid(0xFF8A5B43.toInt())
        c.drawRoundRect(x - width * .010f, y - height * .055f, x + width * .010f, y + height * .055f, 8f, 8f, paint)
        c.restore()
        finishPaint()
    }

    private fun drawShuffleIcon(c: Canvas, x: Float, y: Float) {
        outline(0xFF7E6474.toInt(), width * .009f)
        val p1 = Path()
        p1.moveTo(x - width * .045f, y - height * .025f)
        p1.cubicTo(x - width * .01f, y - height * .025f, x - width * .005f, y + height * .025f, x + width * .04f, y + height * .025f)
        c.drawPath(p1, paint)
        val p2 = Path()
        p2.moveTo(x - width * .045f, y + height * .025f)
        p2.cubicTo(x - width * .01f, y + height * .025f, x - width * .005f, y - height * .025f, x + width * .04f, y - height * .025f)
        c.drawPath(p2, paint)
        solid(0xFF7E6474.toInt())
        c.drawCircle(x + width * .04f, y - height * .025f, width * .008f, paint)
        c.drawCircle(x + width * .04f, y + height * .025f, width * .008f, paint)
        finishPaint()
    }

    private fun drawLock(c: Canvas, x: Float, y: Float, r: Float) {
        solid(0xFF9A8A94.toInt())
        c.drawRoundRect(x - r, y, x + r, y + r * 1.25f, r * .18f, r * .18f, paint)
        outline(0xFF9A8A94.toInt(), r * .24f)
        c.drawArc(x - r * .72f, y - r * 1.10f, x + r * .72f, y + r * .40f, 180f, 180f, false, paint)
        finishPaint()
    }

    private fun drawPill(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        solid(color)
        c.drawRoundRect(l, t, r, b, 22f, 22f, paint)
        finishPaint()
    }

    private fun drawPanel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        drawPill(c, l, t, r, b, Color.WHITE)
        outline(0x24A87991, 2f)
        c.drawRoundRect(l, t, r, b, 22f, 22f, paint)
        finishPaint()
    }

    private fun drawText(c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x, baseline, paint)
    }

    private fun gradient(c: Canvas, light: Int, dark: Int, top: Float, bottom: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, top, 0f, bottom, light, dark, Shader.TileMode.CLAMP)
    }

    private fun solid(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 255
    }

    private fun outline(color: Int, stroke: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = color
        paint.alpha = 255
    }

    private fun strokePath(c: Canvas, path: Path, color: Int, stroke: Float) {
        outline(color, stroke)
        c.drawPath(path, paint)
        finishPaint()
    }

    private fun finishPaint() {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.alpha = 255
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                swipeHandled = false
                downInBoard = false

                if (screen == Screen.LEVELS) {
                    return true
                }

                if (screen == Screen.GAME && phase == IDLE) {
                    val cell = boardCell()
                    val left = boardLeft()
                    val top = boardTop()
                    val col = ((e.x - left) / cell).toInt()
                    val row = ((e.y - top) / cell).toInt()
                    if (row in 0 until N && col in 0 until N) {
                        downInBoard = true
                        if (selectedR in 0 until N && selectedC in 0 until N &&
                            isAdjacent(selectedR, selectedC, row, col)
                        ) {
                            startMove(Cell(selectedR, selectedC), Cell(row, col))
                            selectedR = -1
                            selectedC = -1
                        } else {
                            selectedR = row
                            selectedC = col
                        }
                        lastActionAt = System.currentTimeMillis()
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val dy = e.y - downY

                if (screen == Screen.LEVELS && !swipeHandled && abs(dy) > height * .012f) {
                    val rows = (MAX_LEVEL + 3) / 4
                    val top = height * .16f
                    val rowH = height * .095f
                    val maxScroll = max(0f, top + rows * rowH - height * .87f)
                    levelScroll = (levelScroll - dy).coerceIn(0f, maxScroll)
                    downX = e.x
                    downY = e.y
                    swipeHandled = true
                    invalidate()
                    return true
                }

                if (screen == Screen.GAME && phase == IDLE && !swipeHandled &&
                    selectedR in 0 until N && selectedC in 0 until N
                ) {
                    val threshold = boardCell() * .22f
                    if (abs(dx) > threshold || abs(dy) > threshold) {
                        var tr = selectedR
                        var tc = selectedC
                        if (abs(dx) > abs(dy)) tc += if (dx > 0f) 1 else -1
                        else tr += if (dy > 0f) 1 else -1

                        if (tr in 0 until N && tc in 0 until N) {
                            startMove(Cell(selectedR, selectedC), Cell(tr, tc))
                        }
                        selectedR = -1
                        selectedC = -1
                        swipeHandled = true
                        lastActionAt = System.currentTimeMillis()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!swipeHandled && !(screen == Screen.GAME && downInBoard)) {
                    handleTap(e.x, e.y)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                swipeHandled = true
                return true
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        when (screen) {
            Screen.MENU -> when {
                y in height * .32f..height * .41f -> startLevel(unlockedLevel)
                y in height * .43f..height * .52f -> screen = Screen.LEVELS
                y in height * .54f..height * .63f -> screen = Screen.SHOP
                y in height * .65f..height * .74f -> screen = Screen.SETTINGS
                y in height * .76f..height * .85f -> screen = Screen.HELP
            }

            Screen.LEVELS -> {
                if (y > height * .90f) {
                    screen = Screen.MENU
                } else {
                    val top = height * .16f
                    val rowH = height * .095f
                    val row = ((y - top + levelScroll) / rowH).toInt()
                    val col = ((x - width * .055f) / (width * .235f)).toInt()
                    if (row >= 0 && col in 0..3) {
                        val chosen = row * 4 + col + 1
                        if (chosen in 1..MAX_LEVEL) {
                            if (chosen <= unlockedLevel) startLevel(chosen)
                            else showMessage("COMPLETE LEVEL " + unlockedLevel + " FIRST")
                        }
                    }
                }
            }

            Screen.SETTINGS -> handleSettingsTap(y)

            Screen.SHOP -> when {
                y in height * .19f..height * .34f -> buyHammer()
                y in height * .39f..height * .54f -> buyShuffle()
                y > height * .86f -> screen = Screen.MENU
            }

            Screen.HELP -> if (y > height * .82f) screen = Screen.MENU

            Screen.GAME -> handleGameButtons(x, y)
        }

        if (sound && screen != Screen.SETTINGS) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
        invalidate()
    }

    private fun handleSettingsTap(y: Float) {
        when {
            y in height * .19f..height * .265f -> fps = when (fps) {
                "60" -> "90"
                "90" -> "120"
                else -> "60"
            }
            y in height * .285f..height * .36f -> graphics = when (graphics) {
                "LOW" -> "MEDIUM"
                "MEDIUM" -> "HIGH"
                else -> "LOW"
            }
            y in height * .38f..height * .455f -> shadows = !shadows
            y in height * .475f..height * .55f -> sound = !sound
            y in height * .77f..height * .84f -> resetProgress()
            y > height * .86f -> screen = Screen.MENU
        }
        save()
    }

    private fun handleGameButtons(x: Float, y: Float) {
        val complete = levelComplete()
        val failed = !complete && moves <= 0 && phase == IDLE && clearCells.isEmpty()

        if (complete || failed) {
            when {
                y in height * .57f..height * .65f -> if (complete) completeLevel() else startLevel(level)
                y in height * .665f..height * .735f -> screen = Screen.LEVELS
                else -> screen = Screen.LEVELS
            }
            return
        }

        if (y > height * .82f) {
            when {
                x < width * .31f -> useHammer()
                x < width * .68f -> useShuffle()
                else -> screen = Screen.MENU
            }
        }
    }

    private fun startLevel(l: Int) {
        if (l !in 1..MAX_LEVEL) return
        if (l > unlockedLevel) {
            showMessage("COMPLETE LEVEL " + unlockedLevel + " FIRST")
            return
        }

        level = l
        score = 0
        moves = max(20, 30 - l / 5)
        target = 600 + l * 135
        combo = 0

        jellyTarget = when {
            l < 5 -> 0
            l < 12 -> 5 + l
            else -> min(20, 10 + l / 2)
        }
        jellyRemaining = jellyTarget

        phase = IDLE
        swapMove = null
        clearCells.clear()
        specialCell = null
        specialType = NONE
        comboCount = 0
        selectedR = -1
        selectedC = -1
        levelScroll = 0f
        lastActionAt = System.currentTimeMillis()

        fillBoard()
        placeJelly()
        ensurePlayable()
        screen = Screen.GAME
    }

    private fun resetProgress() {
        unlockedLevel = 1
        coins = 0
        hammer = 2
        shuffle = 1
        level = 1
        save()
        showMessage("PROGRESS RESET")
    }

    private fun fillBoard() {
        for (r in 0 until N) {
            for (c in 0 until N) {
                board[r][c] = randomCandyAvoidingImmediateMatch(r, c)
                jelly[r][c] = false
                fallOffset[r][c] = 0f
            }
        }
    }

    private fun randomCandyAvoidingImmediateMatch(r: Int, c: Int): Candy {
        var type = Random.nextInt(TYPES)
        var guard = 0
        while (guard++ < 80 && (
            (c >= 2 && board[r][c - 1]?.type == type && board[r][c - 2]?.type == type) ||
            (r >= 2 && board[r - 1][c]?.type == type && board[r - 2][c]?.type == type)
        )) {
            type = Random.nextInt(TYPES)
        }
        return Candy(type)
    }

    private fun placeJelly() {
        if (jellyTarget <= 0) return
        val cells = allCells().toList().shuffled()
        for (i in 0 until min(jellyTarget, cells.size)) {
            val cell = cells[i]
            jelly[cell.r][cell.c] = true
        }
    }

    private fun startMove(a: Cell, b: Cell) {
        if (phase != IDLE || !isAdjacent(a.r, a.c, b.r, b.c)) return
        if (board[a.r][a.c] == null || board[b.r][b.c] == null) return

        val specialPair =
            board[a.r][a.c]!!.special != NONE &&
            board[b.r][b.c]!!.special != NONE

        if (specialPair) {
            swapMove = Swap(a, b)
            swapValid = true
            phase = SWAP
            phaseStarted = System.currentTimeMillis()
            comboCount = 0
            return
        }

        swap(a.r, a.c, b.r, b.c)
        val valid = findMatches().cells.isNotEmpty()
        swap(a.r, a.c, b.r, b.c)

        swapMove = Swap(a, b)
        swapValid = valid
        phase = SWAP
        phaseStarted = System.currentTimeMillis()
        comboCount = 0
    }

    private fun advancePhase(now: Long) {
        when (phase) {
            SWAP -> {
                if (now - phaseStarted >= 170L) {
                    val s = swapMove ?: run {
                        phase = IDLE
                        return
                    }

                    if (!swapValid) {
                        showMessage("NO MATCH")
                        phase = IDLE
                        swapMove = null
                        lastActionAt = now
                        return
                    }

                    moves--
                    swap(s.a.r, s.a.c, s.b.r, s.b.c)
                    comboCount = 1

                    val first = board[s.a.r][s.a.c]
                    val second = board[s.b.r][s.b.c]
                    if (first?.special != NONE && second?.special != NONE) {
                        clearCells = allCells().toMutableSet()
                        specialCell = null
                        specialType = NONE
                    } else {
                        val match = findMatches()
                        clearCells = match.cells.toMutableSet()
                        val special = chooseSpecial(match, s.b)
                        specialCell = special.first
                        specialType = special.second
                    }

                    swapMove = null
                    phase = if (clearCells.isEmpty()) IDLE else CLEAR
                    phaseStarted = now
                }
            }

            CLEAR -> {
                if (now - phaseStarted >= 180L) resolveClear(now)
            }

            FALL -> {
                if (now - phaseStarted >= 180L) {
                    for (r in 0 until N) for (c in 0 until N) fallOffset[r][c] = 0f

                    val match = findMatches()
                    if (match.cells.isEmpty()) {
                        comboCount = 0
                        phase = IDLE
                        if (!hasValidMove()) reshuffleBoard()
                        checkEndState()
                    } else {
                        comboCount++
                        clearCells = match.cells.toMutableSet()
                        val special = chooseSpecial(match, Cell(-1, -1))
                        specialCell = special.first
                        specialType = special.second
                        phase = CLEAR
                        phaseStarted = now
                    }
                }
            }
        }
    }

    private fun resolveClear(now: Long) {
        if (clearCells.isEmpty()) {
            phase = IDLE
            return
        }

        val seeds = clearCells.toMutableSet()
        val preserved = specialCell
        val preservedSpecial = if (preserved != null) specialType else NONE

        val expanded = expandSpecials(seeds)
        if (preserved != null) expanded.remove(preserved)

        var cleared = 0
        for (cell in expanded) {
            val candy = board[cell.r][cell.c] ?: continue
            spawnParticles(cell)
            if (jelly[cell.r][cell.c]) {
                jelly[cell.r][cell.c] = false
                jellyRemaining = max(0, jellyRemaining - 1)
            }
            board[cell.r][cell.c] = null
            cleared++
            if (candy.special != NONE) cleared += 2
        }

        if (preserved != null && preservedSpecial != NONE) {
            board[preserved.r][preserved.c] = Candy(
                board[preserved.r][preserved.c]?.type ?: Random.nextInt(TYPES),
                preservedSpecial
            )
        }

        score += cleared * 25 + expanded.size * max(1, comboCount) * 3
        dropAndRefill()

        clearCells.clear()
        specialCell = null
        specialType = NONE
        phase = FALL
        phaseStarted = now
    }

    private fun expandSpecials(seed: Set<Cell>): MutableSet<Cell> {
        val result = seed.toMutableSet()
        val queue = ArrayDeque<Cell>()
        seed.forEach { queue.addLast(it) }
        val processed = HashSet<Cell>()

        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (!processed.add(cell)) continue

            val candy = board[cell.r][cell.c] ?: continue
            when (candy.special) {
                H_STRIPE -> for (c in 0 until N) {
                    val next = Cell(cell.r, c)
                    if (result.add(next)) queue.addLast(next)
                }

                V_STRIPE -> for (r in 0 until N) {
                    val next = Cell(r, cell.c)
                    if (result.add(next)) queue.addLast(next)
                }

                WRAPPED -> for (r in max(0, cell.r - 1)..min(N - 1, cell.r + 1)) {
                    for (c in max(0, cell.c - 1)..min(N - 1, cell.c + 1)) {
                        val next = Cell(r, c)
                        if (result.add(next)) queue.addLast(next)
                    }
                }

                COLOR_BOMB -> {
                    val targetType = findColorBombTarget(cell)
                    for (r in 0 until N) for (c in 0 until N) {
                        if (board[r][c]?.type == targetType) {
                            val next = Cell(r, c)
                            if (result.add(next)) queue.addLast(next)
                        }
                    }
                }
            }
        }

        return result
    }

    private fun findColorBombTarget(center: Cell): Int {
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = center.r + dr
                val c = center.c + dc
                if (r in 0 until N && c in 0 until N) {
                    val candy = board[r][c]
                    if (candy != null && candy.special != COLOR_BOMB) return candy.type
                }
            }
        }
        return Random.nextInt(TYPES)
    }

    private fun chooseSpecial(match: MatchInfo, preferred: Cell): Pair<Cell?, Int> {
        if (match.cells.isEmpty()) return null to NONE
        val candidate = if (preferred in match.cells) preferred else match.cells.first()

        val five = match.groups.firstOrNull { it.cells.size >= 5 && candidate in it.cells }
            ?: match.groups.firstOrNull { it.cells.size >= 5 }
        if (five != null) return candidate to COLOR_BOMB

        val cross = match.crossings.firstOrNull()
        if (cross != null) return cross to WRAPPED

        val h4 = match.groups.firstOrNull { it.horizontal && it.cells.size == 4 && candidate in it.cells }
            ?: match.groups.firstOrNull { it.horizontal && it.cells.size == 4 }
        if (h4 != null) return candidate to H_STRIPE

        val v4 = match.groups.firstOrNull { !it.horizontal && it.cells.size == 4 && candidate in it.cells }
            ?: match.groups.firstOrNull { !it.horizontal && it.cells.size == 4 }
        if (v4 != null) return candidate to V_STRIPE

        return null to NONE
    }

    private fun findMatches(): MatchInfo {
        val cells = mutableSetOf<Cell>()
        val groups = mutableListOf<Group>()

        for (r in 0 until N) {
            var c = 0
            while (c < N) {
                val type = board[r][c]?.type
                if (type == null) {
                    c++
                    continue
                }

                var end = c + 1
                while (end < N && board[r][end]?.type == type) end++
                if (end - c >= 3) {
                    val group = (c until end).map { Cell(r, it) }
                    groups.add(Group(group, true))
                    cells.addAll(group)
                }
                c = end
            }
        }

        for (c in 0 until N) {
            var r = 0
            while (r < N) {
                val type = board[r][c]?.type
                if (type == null) {
                    r++
                    continue
                }

                var end = r + 1
                while (end < N && board[end][c]?.type == type) end++
                if (end - r >= 3) {
                    val group = (r until end).map { Cell(it, c) }
                    groups.add(Group(group, false))
                    cells.addAll(group)
                }
                r = end
            }
        }

        val hs = groups.filter { it.horizontal }
        val vs = groups.filter { !it.horizontal }
        val crossings = hs.flatMap { h ->
            h.cells.filter { cell -> vs.any { cell in it.cells } }
        }.toSet()

        return MatchInfo(cells, groups, crossings)
    }

    private fun dropAndRefill() {
        for (c in 0 until N) {
            var write = N - 1

            for (r in N - 1 downTo 0) {
                val candy = board[r][c]
                if (candy != null) {
                    if (write != r) {
                        board[write][c] = candy
                        board[r][c] = null
                        fallOffset[write][c] = (write - r).toFloat()
                    }
                    write--
                }
            }

            for (r in write downTo 0) {
                board[r][c] = randomCandyAvoidingImmediateMatch(r, c)
                fallOffset[r][c] = (r + 1).toFloat()
            }
        }
    }

    private fun ensurePlayable() {
        if (!hasValidMove()) reshuffleBoard()
    }

    private fun reshuffleBoard() {
        val types = mutableListOf<Int>()
        for (r in 0 until N) for (c in 0 until N) types.add(Random.nextInt(TYPES))

        var guard = 0
        do {
            types.shuffle()
            var i = 0
            for (r in 0 until N) for (c in 0 until N) {
                board[r][c] = Candy(types[i++])
                fallOffset[r][c] = 0f
            }
            guard++
        } while (guard < 400 && (hasImmediateMatches() || !hasValidMove()))

        showMessage("BOARD SHUFFLED")
    }

    private fun hasImmediateMatches(): Boolean = findMatches().cells.isNotEmpty()

    private fun hasValidMove(): Boolean {
        for (r in 0 until N) {
            for (c in 0 until N) {
                if (c + 1 < N && testSwap(r, c, r, c + 1)) return true
                if (r + 1 < N && testSwap(r, c, r + 1, c)) return true
            }
        }
        return false
    }

    private fun testSwap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        swap(r1, c1, r2, c2)
        val ok = findMatches().cells.isNotEmpty()
        swap(r1, c1, r2, c2)
        return ok
    }

    private fun findHint(): Swap? {
        for (r in 0 until N) {
            for (c in 0 until N) {
                if (c + 1 < N && testSwap(r, c, r, c + 1)) return Swap(Cell(r, c), Cell(r, c + 1))
                if (r + 1 < N && testSwap(r, c, r + 1, c)) return Swap(Cell(r, c), Cell(r + 1, c))
            }
        }
        return null
    }

    private fun useHammer() {
        if (hammer <= 0) {
            showMessage("BUY A HAMMER")
            return
        }

        val r = if (selectedR in 0 until N) selectedR else N / 2
        val c = if (selectedC in 0 until N) selectedC else N / 2
        if (board[r][c] == null) return

        if (jelly[r][c]) {
            jelly[r][c] = false
            jellyRemaining = max(0, jellyRemaining - 1)
        }

        spawnParticles(Cell(r, c))
        board[r][c] = null
        hammer--
        score += 30
        selectedR = -1
        selectedC = -1
        dropAndRefill()
        phase = FALL
        phaseStarted = System.currentTimeMillis()
        lastActionAt = System.currentTimeMillis()
        save()
    }

    private fun useShuffle() {
        if (shuffle <= 0) {
            showMessage("BUY A SHUFFLE")
            return
        }

        shuffle--
        reshuffleBoard()
        selectedR = -1
        selectedC = -1
        phase = IDLE
        lastActionAt = System.currentTimeMillis()
        save()
    }

    private fun buyHammer() {
        if (coins < 30) showMessage("NEED 30 COINS")
        else {
            coins -= 30
            hammer++
            save()
        }
    }

    private fun buyShuffle() {
        if (coins < 45) showMessage("NEED 45 COINS")
        else {
            coins -= 45
            shuffle++
            save()
        }
    }

    private fun levelComplete(): Boolean {
        return score >= target && jellyRemaining == 0
    }

    private fun checkEndState() {
        if (levelComplete()) phase = IDLE
        else if (moves <= 0) phase = IDLE
    }

    private fun rewardCoins(): Int = 20 + level * 4

    private fun completeLevel() {
        coins += rewardCoins()
        if (level >= unlockedLevel && level < MAX_LEVEL) unlockedLevel = level + 1
        save()
        screen = Screen.LEVELS
        levelScroll = ((max(0, unlockedLevel - 1) / 4) * height * .095f).coerceAtLeast(0f)
    }

    private fun save() {
        prefs.edit()
            .putInt("coins", coins)
            .putInt("hammer", hammer)
            .putInt("shuffle", shuffle)
            .putInt("unlocked_level", unlockedLevel)
            .putString("fps", fps)
            .putString("graphics", graphics)
            .putBoolean("shadows", shadows)
            .putBoolean("sound", sound)
            .apply()
    }

    private fun showMessage(s: String) {
        msg = s
        msgUntil = System.currentTimeMillis() + 1200L
    }

    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val t = board[r1][c1]
        board[r1][c1] = board[r2][c2]
        board[r2][c2] = t
    }

    private fun boardCell(): Float = min(width * .105f, height * .062f)
    private fun boardLeft(): Float = (width - N * boardCell()) * .5f
    private fun boardTop(): Float = height * .175f

    private fun phaseProgress(now: Long, duration: Long): Float {
        return ((now - phaseStarted).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    private fun easeInOut(t: Float): Float = t * t * (3f - 2f * t)

    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        return abs(r1 - r2) + abs(c1 - c2) == 1
    }

    private fun allCells(): Set<Cell> {
        val result = mutableSetOf<Cell>()
        for (r in 0 until N) for (c in 0 until N) result.add(Cell(r, c))
        return result
    }

    private fun spawnParticles(cell: Cell) {
        val size = boardCell()
        val x = boardLeft() + cell.c * size + size * .5f
        val y = boardTop() + cell.r * size + size * .5f
        repeat(7) {
            val a = Random.nextDouble(0.0, Math.PI * 2.0)
            val speed = size * Random.nextDouble(1.0, 2.2).toFloat()
            particles += Particle(
                x, y,
                kotlin.math.cos(a).toFloat() * speed,
                kotlin.math.sin(a).toFloat() * speed,
                size * Random.nextDouble(.025, .055).toFloat(),
                1f
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt * 2.4f
            if (p.life <= 0f) iterator.remove()
        }
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            solid(0xFFD25B83.toInt())
            paint.alpha = (p.life.coerceIn(0f, 1f) * 255f).toInt()
            c.drawCircle(p.x, p.y, p.radius, paint)
            paint.alpha = 255
        }
        finishPaint()
    }

    private fun requestFrame() {
        when (fps) {
            "120" -> postDelayed({ invalidate() }, 8L)
            "90" -> postDelayed({ invalidate() }, 11L)
            else -> postDelayed({ invalidate() }, 16L)
        }
    }
}
