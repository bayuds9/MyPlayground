package com.flowerencee9.myplayground.fr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.flowerencee9.myplayground.databinding.ActivityFrBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.face.FaceDetector
import kotlin.collections.Map.Entry

class FRActivity : AppCompatActivity() {
    companion object {
        fun myIntent(context: Context) = Intent(context, FRActivity::class.java)
    }

    private lateinit var binding: ActivityFrBinding

    var detector: FaceDetector? = null

    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    /*var previewView: PreviewView? = null
    var face_preview: ImageView? = null
    var tfLite: Interpreter? = null
    var reco_name: TextView? = null
    var preview_info: TextView? = null
    var textAbove_preview: TextView? = null
    var recognize: Button? = null
    var camera_switch: android.widget.Button? = null
    var actions: android.widget.Button? = null
    var add_face: ImageButton? = null*/
    var cameraSelector: CameraSelector? = null
    var developerMode = false
    var distance = 1.0f
    var start = true
    var flipX: kotlin.Boolean = false
    var cam_face = CameraSelector.LENS_FACING_BACK //Default Back Camera


    lateinit var intValues: IntArray
    var inputSize = 112 //Input size for model

    var isModelQuantized = false
    lateinit var embeedings: Array<FloatArray>
    var IMAGE_MEAN = 128.0f
    var IMAGE_STD = 128.0f
    var OUTPUT_SIZE = 192 //Output size of model

    private val SELECT_PICTURE = 1
    var cameraProvider: ProcessCameraProvider? = null
    private val MY_CAMERA_REQUEST_CODE = 100

    var modelFile = "mobile_face_net.tflite" //model name


    private var registered: HashMap<String, SimilarityClassifier.Recognition>? = null


    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        binding = ActivityFrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupView()
        setupLogic()
    }

    private fun setupLogic() {
        registered = readFromSP()
        val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
    }

    private fun setupView() {
//        TODO("Not yet implemented")
    }

    private fun readFromSP(): HashMap<String, SimilarityClassifier.Recognition>? {
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val defValue = Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue)
        val token: TypeToken<HashMap<String?, SimilarityClassifier.Recognition?>> =
            object : TypeToken<HashMap<String?, SimilarityClassifier.Recognition?>>() {}
        val retrievedMap: java.util.HashMap<String, SimilarityClassifier.Recognition> =
            Gson().fromJson(json,token.type)

        retrievedMap.entries.forEach { entry ->
            val output = Array(1) {
                FloatArray(
                    OUTPUT_SIZE
                )
            }
            var arrayList = entry.value.extra as ArrayList<*>
            arrayList = arrayList[0] as ArrayList<*>
            for (counter in arrayList.indices) {
                output[0][counter] = (arrayList[counter] as Double).toFloat()
            }
            entry.value.extra = output
        }
        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
        Toast.makeText(this, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }
}