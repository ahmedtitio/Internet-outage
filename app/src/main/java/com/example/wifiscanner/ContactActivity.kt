package com.example.wifiscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.ads.AdManager
import com.example.wifiscanner.databinding.ActivityContactBinding
import com.example.wifiscanner.util.LocaleHelper

/** شاشة "اتصل بنا" — بيانات المطور وإرسال بريد دعم مباشر. */
class ContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSendEmail.setOnClickListener { sendEmail() }
        binding.textEmail.setOnClickListener { sendEmail() }

        AdManager.showBanner(this, binding.adContainerContact)
    }

    private fun sendEmail() {
        val body = binding.editMessage.text?.toString().orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$DEV_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_email_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val DEV_EMAIL = "ahmedtitio@gmail.com"
    }
}
