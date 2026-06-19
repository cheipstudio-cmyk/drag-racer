package com.secondream.aiidler

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class AudioManager(private val context: Context) {
    private var tapSound: Int = 0
    private var upgradeSound: Int = 0
    private var breakthroughSound: Int = 0
    private var loaded = false

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    init {
        try {
            soundPool.setOnLoadCompleteListener { _, _, _ -> loaded = true }
            tapSound = soundPool.load(context, R.raw.tap, 1)
            upgradeSound = soundPool.load(context, R.raw.upgrade, 1)
            breakthroughSound = soundPool.load(context, R.raw.breakthrough, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playTapSound() {
        try {
            soundPool.play(tapSound, 0.6f, 0.6f, 1, 0, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playUpgradeSound() {
        try {
            soundPool.play(upgradeSound, 0.85f, 0.85f, 1, 0, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playBreakthroughSound() {
        try {
            soundPool.play(breakthroughSound, 1f, 1f, 2, 0, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        soundPool.release()
    }
}
