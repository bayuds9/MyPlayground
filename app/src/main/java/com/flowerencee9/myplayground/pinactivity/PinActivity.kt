package com.flowerencee9.myplayground.pinactivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.flowerencee9.myplayground.databinding.ActivityPinBinding
import com.flowerencee9.myplayground.supportclass.PinView


class PinActivity : AppCompatActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, PinActivity::class.java)
    }

    private lateinit var binding: ActivityPinBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        val pinListener = object : PinView.TextListener {
            override fun onTextChanged() {
                if(binding.pinEntry.text?.length == 6) Toast.makeText(
                    this@PinActivity,
                    "Full",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }

        }
        binding.pinEntry.apply {
            setMpinMode(true)
            setListener(pinListener)
        }
    }
}