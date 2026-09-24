package com.example.wifiscanner

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.databinding.ActivitySplashBinding

/**
 * شاشة البداية: تعرض شعار التطبيق لمدة 5 ثوانٍ ثم تنتقل إلى الشاشة الرئيسية.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // حركة ظهور ناعمة للشعار
        binding.cardLogo.alpha = 0f
        binding.cardLogo.scaleX = 0.8f
        binding.cardLogo.scaleY = 0.8f
        binding.cardLogo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.textAppTitle.alpha = 0f
        binding.textAppTitle.animate().alpha(1f).setStartDelay(400).setDuration(700).start()

        binding.textTagline.alpha = 0f
        binding.textTagline.animate().alpha(1f).setStartDelay(800).setDuration(700).start()

        // الانتقال بعد 5 ثوانٍ (قابلة للتجاوز بالضغط على الشاشة)
        binding.root.postDelayed(::goToMain, SPLASH_DURATION_MS)
        binding.root.setOnClickListener { goToMain() }
    }

    private var navigated = false

    private fun goToMain() {
        if (navigated || isFinishing || isDestroyed) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onBackPressed() { /* منع الرجوع من شاشة البداية */ }

    companion object {
        private const val SPLASH_DURATION_MS = 5_000L
    }
}
