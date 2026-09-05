package com.iykyk.videocollage.processing

import android.graphics.*

class CollageGenerator(private val config: CollageConfig = CollageConfig()) {

    fun generate(
        persons: List<CollageInput>,
        maxOutputWidth: Int = 1080,
        maxOutputHeight: Int = 1920
    ): Bitmap {
        require(persons.isNotEmpty()) { "At least one person required" }

        val outputWidth = maxOutputWidth.coerceAtMost(MAX_BITMAP_DIMENSION)
        val outputHeight = maxOutputHeight.coerceAtMost(MAX_BITMAP_DIMENSION)

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = config.backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), bgPaint)

        val layout = computeLayout(persons.size, outputWidth, outputHeight)

        for ((index, tile) in layout.tiles.withIndex()) {
            val person = persons[index]
            drawTile(canvas, person, tile)
        }

        return bitmap
    }

    fun computeLayout(personCount: Int, width: Int, height: Int): CollageLayout {
        require(personCount > 0) { "At least one person required" }
        val gap = config.gapPx
        val inset = config.marginPx
        val innerW = (width - inset * 2 - gap * 2).toFloat()
        val innerH = (height - inset * 2 - gap * 2).toFloat()

        val tiles = when (personCount) {
            1 -> listOf(
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap,
                    width = innerW + gap,
                    height = innerH + gap
                )
            )
            2 -> listOf(
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap,
                    width = innerW + gap,
                    height = (innerH - gap) / 2f
                ),
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap + (innerH - gap) / 2f + gap,
                    width = innerW + gap,
                    height = (innerH - gap) / 2f
                )
            )
            3 -> listOf(
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap,
                    width = innerW + gap,
                    height = (innerH - gap) * 0.50f
                ),
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap + (innerH - gap) * 0.50f + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) * 0.50f
                ),
                TileRect(
                    x = inset.toFloat() + gap + (innerW - gap) / 2f + gap,
                    y = inset.toFloat() + gap + (innerH - gap) * 0.50f + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) * 0.50f
                )
            )
            4 -> listOf(
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) / 2f
                ),
                TileRect(
                    x = inset.toFloat() + gap + (innerW - gap) / 2f + gap,
                    y = inset.toFloat() + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) / 2f
                ),
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap + (innerH - gap) / 2f + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) / 2f
                ),
                TileRect(
                    x = inset.toFloat() + gap + (innerW - gap) / 2f + gap,
                    y = inset.toFloat() + gap + (innerH - gap) / 2f + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap) / 2f
                )
            )
            5 -> listOf(
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap * 2) * 0.45f
                ),
                TileRect(
                    x = inset.toFloat() + gap + (innerW - gap) / 2f + gap,
                    y = inset.toFloat() + gap,
                    width = (innerW - gap) / 2f,
                    height = (innerH - gap * 2) * 0.45f
                ),
                TileRect(
                    x = inset.toFloat() + gap,
                    y = inset.toFloat() + gap + (innerH - gap * 2) * 0.45f + gap,
                    width = (innerW - gap * 2) / 3f,
                    height = (innerH - gap * 2) * 0.55f
                ),
                TileRect(
                    x = inset.toFloat() + gap + (innerW - gap * 2) / 3f + gap,
                    y = inset.toFloat() + gap + (innerH - gap * 2) * 0.45f + gap,
                    width = (innerW - gap * 2) / 3f,
                    height = (innerH - gap * 2) * 0.55f
                ),
                TileRect(
                    x = inset.toFloat() + gap + ((innerW - gap * 2) / 3f + gap) * 2,
                    y = inset.toFloat() + gap + (innerH - gap * 2) * 0.45f + gap,
                    width = (innerW - gap * 2) / 3f,
                    height = (innerH - gap * 2) * 0.55f
                )
            )
            else -> {
                val cols = 3
                val rows = (personCount + cols - 1) / cols
                val cellW = (innerW - gap * (cols - 1)) / cols
                val cellH = (innerH - gap * (rows - 1)) / rows

                val result = mutableListOf<TileRect>()
                for (i in 0 until personCount) {
                    val col = i % cols
                    val row = i / cols
                    result.add(
                        TileRect(
                            x = inset.toFloat() + gap + col * (cellW + gap),
                            y = inset.toFloat() + gap + row * (cellH + gap),
                            width = cellW,
                            height = cellH
                        )
                    )
                }
                result
            }
        }

        return CollageLayout(tiles = tiles)
    }

    private fun drawTile(canvas: Canvas, person: CollageInput, tile: TileRect) {
        val cornerRadius = config.tileCornerRadiusPx

        val bitmap = person.representativeFrame
        if (bitmap != null) {
            drawImageTile(canvas, bitmap, tile, cornerRadius)
        } else {
            drawPlaceholderTile(canvas, tile, cornerRadius)
        }

        drawLabelOverlay(canvas, person, tile)
    }

    private fun drawImageTile(
        canvas: Canvas,
        source: Bitmap,
        tile: TileRect,
        cornerRadius: Float
    ) {
        val rect = RectF(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)

        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)

        val scaleX = tile.width / source.width
        val scaleY = tile.height / source.height
        val scale = maxOf(scaleX, scaleY)

        val drawWidth = source.width * scale
        val drawHeight = source.height * scale

        val left = tile.x + (tile.width - drawWidth) / 2f
        val top = tile.y + (tile.height - drawHeight) / 2f

        val destRect = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(source, null, destRect, null)

        canvas.restore()

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = config.tileBorderWidthPx
            color = config.tileBorderColor
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawPlaceholderTile(canvas: Canvas, tile: TileRect, cornerRadius: Float) {
        val rect = RectF(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)
        val paint = Paint().apply {
            color = 0xFF3A3A3A.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "?",
            tile.x + tile.width / 2f,
            tile.y + tile.height / 2f + 16f,
            textPaint
        )
    }

    private fun drawLabelOverlay(canvas: Canvas, person: CollageInput, tile: TileRect) {
        val labelHeight = config.labelHeightPx
        val labelY = tile.y + tile.height - labelHeight

        val labelPaint = Paint().apply {
            color = config.labelBackgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(tile.x, labelY, tile.x + tile.width, tile.y + tile.height, labelPaint)

        val textPaint = Paint().apply {
            color = config.labelTextColor
            textSize = config.labelTextSizePx
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val name = person.name
        val countText = "${person.appearanceCount}x"

        val textY = labelY + labelHeight * 0.65f
        val textX = tile.x + config.labelPaddingPx

        canvas.drawText(name, textX, textY, textPaint)

        val countPaint = Paint(textPaint).apply {
            textAlign = Paint.Align.RIGHT
            color = config.countTextColor
        }
        canvas.drawText(countText, tile.x + tile.width - config.labelPaddingPx, textY, countPaint)
    }

    data class TileRect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    data class CollageLayout(
        val tiles: List<TileRect>
    )

    companion object {
        private const val MAX_BITMAP_DIMENSION = 4096
    }
}
