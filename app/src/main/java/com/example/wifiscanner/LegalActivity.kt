package com.example.wifiscanner

import android.os.Bundle
import android.text.util.Linkify
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.ads.AdManager
import com.example.wifiscanner.databinding.ActivityLegalBinding
import com.example.wifiscanner.util.LocaleHelper

/** شاشة عامة لعرض النصوص الطويلة: سياسة الخصوصية / شروط الاستخدام / عن التطبيق. */
class LegalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.settings)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.textLegalContent.text = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        Linkify.addLinks(binding.textLegalContent, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)

        AdManager.showBanner(this, binding.adContainerLegal)
    }

    companion object {
        const val EXTRA_TITLE = "legal_title"
        const val EXTRA_CONTENT = "legal_content"
    }
}
