package com.tomasthrawat.candycrush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    companion object {
        private const val SIZE = 8
        private const val TYPES = 6
        private const val START_MOVES = 30
        private const val SWIPE_THRESHOLD = 40f
    }

    private val board = Array(SIZE) { IntArray(SIZE) { -1 } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileRect = RectF()
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var score = 0
    private var moves = START_MOVES
    private var selectedRow = -1
    private var selectedCol = -1
    private var downX = 0f
    private var downY = 0f

    private val candyColors = intArrayOf(
        0xFFE84A5F.toInt(), 0xFFFFB84D.toInt(), 0xFF58C878.toInt(),
        0xFF4DA6FF.toInt(), 0xFF9B6BFF.toInt(), 0xFFFF72C0.toInt()
    )

    init {
        paint.typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        resetGame()
    }

    private fun resetGame() {
        score = 0
        moves = START_MOVES
        selectedRow = -1
        selectedCol = -1
        fillBoardWithoutMatches()
        invalidate()
    }

    private fun fillBoardWithoutMatches() {
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                var value: Int
                do {
                    value = Random.nextInt(TYPES)
                } while (
                    (c >= 2 && board[r][c - 1] == value && board[r][c - 2] == value) ||
                    (r >= 2 && board[r - 1][c] == value && board[r - 2][c] == value)
                )
                board[r][c] = value
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFFF4E9FF.toInt())

        val width = width.toFloat()
        val height = height.toFloat()
        val topPanel = height * 0.19f
        cell = min(width * 0.112f, (height - topPanel - 28f) / SIZE)
        val boardSize = cell * SIZE
        boardLeft = (width - boardSize) / 2f
        boardTop = topPanel

        paint.style = Paint.Style.FILL
        paint.color = 0xFF6B3FA0.toInt()
        canvas.drawRect(0f, 0f, width, topPanel, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.075f
        canvas.drawText("Sweet Match", width / 2f, topPanel * 0.43f, paint)
        paint.textSize = width * 0.043f
        canvas.drawText("Score  $score", width * 0.25f, topPanel * 0.77f, paint)
        canvas.drawText("Moves  $moves", width * 0.75f, topPanel * 0.77f, paint)

        paint.color = 0xFFD8C2E8.toInt()
        canvas.drawRoundRect(
            boardLeft - 7f, boardTop - 7f,
            boardLeft + boardSize + 7f, boardTop + boardSize + 7f,
            18f, 18f, paint
        )
        drawBoard(canvas)

        if (moves == 0) {
            paint.color = 0xCC24152F.toInt()
            canvas.drawRect(0f, topPanel, width, height, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = width * 0.09f
            canvas.drawText("Game Over", width / 2f, boardTop + boardSize / 2f, paint)
            paint.textSize = width * 0.045f
            canvas.drawText("Tap to play again", width / 2f, boardTop + boardSize / 2f + 55f, paint)
        }
    }

    private fun drawBoard(canvas: Canvas) {
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val x = boardLeft + c * cell
                val y = boardTop + r * cell

                paint.color = 0xFFE9DDF0.toInt()
                canvas.drawRoundRect(
                    x + 2f, y + 2f, x + cell - 2f, y + cell - 2f,
                    12f, 12f, paint
                )

                val value = board[r][c]
                if (value >= 0) {
                    val pad = cell * 0.12f
                    tileRect.set(x + pad, y + pad, x + cell - pad, y + cell - pad)
                    paint.color = candyColors[value]
                    canvas.drawRoundRect(tileRect, cell * 0.22f, cell * 0.22f, paint)
                    paint.color = 0x55FFFFFF
                    canvas.drawCircle(x + cell * 0.36f, y + cell * 0.34f, cell * 0.09f, paint)
                }

                if (r == selectedRow && c == selectedCol) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = cell * 0.055f
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawRoundRect(
                        x + 3f, y + 3f, x + cell - 3f, y + cell - 3f,
                        12f, 12f, paint
                    )
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (moves == 0) {
                resetGame()
                return true
            }
            downX = event.x
            downY = event.y
            val pos = cellAt(event.x, event.y)
            selectedRow = pos.first
            selectedCol = pos.second
            invalidate()
            return true
        }

        if (event.action == MotionEvent.ACTION_UP) {
            if (selectedRow !in 0 until SIZE || selectedCol !in 0 until SIZE) return true

            val dx = event.x - downX
            val dy = event.y - downY
            val target = when {
                abs(dx) >= SWIPE_THRESHOLD && abs(dx) > abs(dy) ->
                    selectedRow to (selectedCol + if (dx > 0) 1 else -1)
                abs(dy) >= SWIPE_THRESHOLD ->
                    (selectedRow + if (dy > 0) 1 else -1) to selectedCol
                else -> selectedRow to selectedCol
            }

            if (target.first in 0 until SIZE && target.second in 0 until SIZE &&
                (target.first != selectedRow || target.second != selectedCol)
            ) {
                tryMove(selectedRow, selectedCol, target.first, target.second)
            }

            selectedRow = -1
            selectedCol = -1
            invalidate()
            return true
        }
        return true
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        return ((y - boardTop) / cell).toInt() to ((x - boardLeft) / cell).toInt()
    }

    private fun tryMove(r1: Int, c1: Int, r2: Int, c2: Int) {
        swap(r1, c1, r2, c2)
        if (findMatches().isEmpty()) {
            swap(r1, c1, r2, c2)
            return
        }
        moves--
        resolveMatches()
    }

    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val temp = board[r1][c1]
        board[r1][c1] = board[r2][c2]
        board[r2][c2] = temp
    }

    private fun findMatches(): Set<Pair<Int, Int>> {
        val matches = mutableSetOf<Pair<Int, Int>>()

        for (r in 0 until SIZE) {
            var start = 0
            while (start < SIZE) {
                var end = start + 1
                while (end < SIZE && board[r][end] == board[r][start]) end++
                if (board[r][start] >= 0 && end - start >= 3) {
                    for (c in start until end) matches.add(r to c)
                }
                start = end
            }
        }

        for (c in 0 until SIZE) {
            var start = 0
            while (start < SIZE) {
                var end = start + 1
                while (end < SIZE && board[end][c] == board[start][c]) end++
                if (board[start][c] >= 0 && end - start >= 3) {
                    for (r in start until end) matches.add(r to c)
                }
                start = end
            }
        }
        return matches
    }

    private fun resolveMatches() {
        var combo = 0
        while (true) {
            val matches = findMatches()
            if (matches.isEmpty()) break

            combo++
            score += matches.size * 10 * combo
            for ((r, c) in matches) board[r][c] = -1

            for (c in 0 until SIZE) {
                var write = SIZE - 1
                for (r in SIZE - 1 downTo 0) {
                    if (board[r][c] >= 0) {
                        board[write][c] = board[r][c]
                        if (write != r) board[r][c] = -1
                        write--
                    }
                }
                while (write >= 0) {
                    board[write][c] = Random.nextInt(TYPES)
                    write--
                }
            }
        }
    }
}
