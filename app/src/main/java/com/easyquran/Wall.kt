package com.easyquran

import android.app.WallpaperManager
import android.content.Context

object Wall {
    fun advance(ctx: Context) {
        Quran.load(ctx)
        Prefs.setIndex(ctx, (Prefs.index(ctx) + 1) % Quran.ayat.size)
        Prefs.setSeed(ctx, Prefs.seed(ctx) + 1)
    }

    fun apply(ctx: Context) {
        Quran.load(ctx)
        val dm = ctx.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels)
        val h = maxOf(dm.widthPixels, dm.heightPixels)
        val (bmp, _) = Scene.render(ctx, w, h, Quran.ayat[Prefs.index(ctx)], Prefs.seed(ctx))
        val flags = when (Prefs.target(ctx)) {
            1 -> WallpaperManager.FLAG_LOCK
            2 -> WallpaperManager.FLAG_SYSTEM
            else -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
        }
        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, flags)
        bmp.recycle()
    }
}
