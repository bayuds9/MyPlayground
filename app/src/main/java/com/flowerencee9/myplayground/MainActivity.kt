package com.flowerencee9.myplayground

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowerencee9.myplayground.databinding.ActivityMainBinding
import com.flowerencee9.myplayground.dynamicinput.DynamicInputActivity
import com.flowerencee9.myplayground.fr.FRActivity
import com.flowerencee9.myplayground.fr2.FRActivity2
import com.flowerencee9.myplayground.nestedrv.NestedRvView
import com.flowerencee9.myplayground.pinactivity.PinActivity
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        initView()
    }

    private fun initView() {
        val menuList = generateMenu()
        val menuListener = object : MainAdapter.MenuListener{
            override fun onClick(item: String, position: Int) {
                when(position){
                    0 -> startActivity(NestedRvView.newIntent(this@MainActivity))
                    1 -> startActivity(PinActivity.newIntent(this@MainActivity))
                    2 -> startActivity(DynamicInputActivity.newIntent(this@MainActivity))
                    3 -> startActivity(FRActivity.myIntent(this@MainActivity))
                    4 -> startActivity(FRActivity2.myIntent(this@MainActivity))
                }
            }

        }
        val menuAdapter = MainAdapter(menuList, menuListener)
        binding.rv.apply {
            adapter = menuAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun generateMenu(): ArrayList<String> {
        return arrayListOf("Nested RV", "PIN", "DYNAMIC INPUT", "FACE RECOGNITION by TFLITE - FACENET", "2 FACE RECOGNITION by TFLITE - FACENET")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Permission not granted",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}