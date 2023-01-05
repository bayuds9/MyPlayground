package com.flowerencee9.myplayground.fr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Pair
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.flowerencee9.myplayground.databinding.ActivityFrBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.experimental.inv

class FRActivity : AppCompatActivity() {
    companion object {
        fun myIntent(context: Context) = Intent(context, FRActivity::class.java)
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private lateinit var binding: ActivityFrBinding

    var detector: FaceDetector? = null

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    var tfLite: Interpreter? = null
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


    var modelFile = "mobile_face_net.tflite" //model name


    private var registered: HashMap<String, SimilarityClassifier.Recognition>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Toast.makeText(this, "wkwk", Toast.LENGTH_SHORT).show()
        //        preview_info.setText("        Recognized Face:");
        //Camera Permission
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupView()
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

    private fun setupLogic() {
        registered = readFromSP()
        val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
    }

    private fun setupView() {
        setupLogic()
        with(binding) {
            button2.setOnClickListener {
                val builder = AlertDialog.Builder(this@FRActivity)
                builder.setTitle("Select Action:")

                // add a checkbox list

                // add a checkbox list
                val names = arrayOf(
                    "View Recognition List",
                    "Update Recognition List",
                    "Save Recognitions",
                    "Load Recognitions",
                    "Clear All Recognitions",
                    "Import Photo (Beta)",
                    "Hyperparameters",
                    "Developer Mode"
                )

                builder.setItems(
                    names
                ) { dialog, which ->
                    when (which) {
                        0 -> displaynameListview()
                        1 -> updatenameListview()
                        2 -> registered?.let { insertToSP(it, 0) }
                        3 -> readFromSP()?.let { it1 -> registered?.putAll(it1) }
                        4 -> clearnameList()
                        5 -> loadphoto()
                        6 -> testHyperparameter()
                        7 -> developerMode()
                    }
                }


                builder.setPositiveButton(
                    "OK"
                ) { dialog, which -> }
                builder.setNegativeButton("Cancel", null)

                // create and show the alert dialog

                // create and show the alert dialog
                val dialog = builder.create()
                dialog.show()
            }
            button5.setOnClickListener {
                if (cam_face == CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT
                    flipX = true
                } else {
                    cam_face = CameraSelector.LENS_FACING_BACK
                    flipX = false
                }
                cameraProvider!!.unbindAll()
                cameraBind()
            }
            binding.imageButton.setOnClickListener{
                addFace()
            }
            button3.setOnClickListener {
                if (button3.text.toString() == "Recognize") {
                    start = true
                    textAbovePreview.text = "Recognized Face:"
                    button3.text = "Add Face"
                    imageButton.visibility = View.INVISIBLE
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.INVISIBLE
                    textView2.text = ""
                    //preview_info.setVisibility(View.INVISIBLE);
                }
                else {
                    textAbovePreview.text = "Face Preview: "
                    button3.text = "Recognize"
                    imageButton.visibility = View.VISIBLE
                    textView.visibility = View.INVISIBLE
                    imageView.visibility = View.VISIBLE
                    textView2.text = "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                }
            }
            //Load model
            try {
                tfLite = loadModelFile(this@FRActivity, modelFile)?.let { Interpreter(it) }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            //Initialize Face Detector
            //Initialize Face Detector
            val highAccuracyOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
            detector = FaceDetection.getClient(highAccuracyOpts)

            cameraBind()
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): MappedByteBuffer? {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    //Bind camera and preview view
    private fun cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener(Runnable {
            try {
                cameraProvider = cameraProviderFuture!!.get()
                bindPreview(cameraProvider!!)
            } catch (e: ExecutionException) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cam_face)
            .build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
            .build()
        val executor: Executor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
                Thread.sleep(0) //Camera preview refreshed every 10 millisec(adjust as required)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            var image: InputImage? = null
            @SuppressLint("UnsafeExperimentalUsageError") val mediaImage// Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)
                    = imageProxy.image
            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                //                    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
            }

//                System.out.println("ANALYSIS");

            //Process acquired image to detect faces
            val result = detector!!.process(
                image!!
            )
                .addOnSuccessListener { faces ->
                    if (faces.size != 0) {
                        val face = faces[0] //Get first face from detected faces

                        //mediaImage to Bitmap
                        val frame_bmp: Bitmap? = toBitmap(mediaImage!!)
                        val rot = imageProxy.imageInfo.rotationDegrees

                        //Adjust orientation of Face
                        val frame_bmp1: Bitmap? =
                            rotateBitmap(frame_bmp!!, rot, flipX = false, flipY = false)


                        //Get bounding box of face
                        val boundingBox = RectF(face.boundingBox)

                        //Crop out bounding box from whole Bitmap(image)
                        var cropped_face: Bitmap? = getCropBitmapByCPU(frame_bmp1, boundingBox)
                        if (flipX) cropped_face = rotateBitmap(cropped_face!!, 0, flipX, false)
                        //Scale the acquired Face to 112*112 which is required input for model
                        val scaled: Bitmap? = getResizedBitmap(cropped_face!!, 112, 112)
                        if (start) recognizeImage(scaled!!) //Send scaled bitmap to create face embeddings.
                        //                                                    System.out.println(boundingBox);
                    } else {
                        binding.textView2.text = if (registered!!.isEmpty()) "Add Face" else "No Face Detected"
                    }
                }
                .addOnFailureListener {
                    // Task failed with an exception
                    // ...
                }
                .addOnCompleteListener {
                    imageProxy.close() //v.important to acquire next frame for analysis
                }
        }
        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector!!,
            imageAnalysis,
            preview
        )
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap? {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    private fun rotateBitmap(
        bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
    ): Bitmap? {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    private fun getCropBitmapByCPU(source: Bitmap?, cropRectF: RectF): Bitmap? {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val cavas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        cavas.drawRect(
            RectF(0F, 0F, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)
        cavas.drawBitmap(source!!, matrix, paint)
        if (!source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }

    fun recognizeImage(bitmap: Bitmap) {

        // set Face to Preview
        binding.imageView.setImageBitmap(bitmap)

        //Create ByteBuffer to store normalized image
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        intValues = IntArray(inputSize * inputSize)

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        //imgData is input to our model
        val inputArray = arrayOf<Any>(imgData)
        val outputMap: MutableMap<Int, Any> = java.util.HashMap()
        embeedings =
            Array(1) { FloatArray(OUTPUT_SIZE) } //output of model will be stored in this variable
        outputMap[0] = embeedings
        tfLite?.runForMultipleInputsOutputs(inputArray, outputMap) //Run model
        var distance_local = Float.MAX_VALUE
        val id = "0"
        val label = "?"

        //Compare new face with saved Faces.
        if (registered!!.size > 0) {
            val nearest: List<Pair<String, Float>?> = findNearest(
                embeedings[0]
            )!! //Find 2 closest matching face
            if (nearest[0] != null) {
                val name = nearest[0]!!.first //get name and distance of closest matching face
                // label = name;
                distance_local = nearest[0]!!.second
                if (developerMode) {
                    binding.textView2.text = if (distance_local < distance) """
                        Nearest: $name
                        Dist: ${String.format("%.3f", distance_local)}
                        2nd Nearest: ${nearest[1]!!.first}
                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
                        """.trimIndent() else """
                        Unknown 
                        Dist: ${String.format("%.3f", distance_local)}
                        Nearest: $name
                        Dist: ${String.format("%.3f", distance_local)}
                        2nd Nearest: ${nearest[1]!!.first}
                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
                        """.trimIndent()

                } else {
                    binding.textView2.text = if (distance_local < distance) name else "Unknown"
                    Log.d("wkwk", "nearest: $name - distance: $distance_local")
                }
            }
        }


//            final int numDetectionsOutput = 1;
//            final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
//            SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
//                    id,
//                    label,
//                    distance);
//
//            recognitions.add( rec );
    }

    //    public void register(String name, SimilarityClassifier.Recognition rec) {
    //        registered.put(name, rec);
    //    }
    private fun findNearest(emb: FloatArray): List<Pair<String, Float>?>? {
        val neighbour_list: MutableList<Pair<String, Float>?> = java.util.ArrayList()
        var ret: Pair<String, Float>? = null //to get closest match
        var prev_ret: Pair<String, Float>? = null //to get second closest match
        registered?.forEach { entry: Map.Entry<String, SimilarityClassifier.Recognition> ->
            val name = entry.key
            val knownEmb : FloatArray = (entry.value.extra as Array<FloatArray>)[0]
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = Math.sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret!!.second) {
                prev_ret = ret
                ret = Pair(name, distance)
            }
        }
        if (prev_ret == null) prev_ret = ret
        neighbour_list.add(ret)
        neighbour_list.add(prev_ret)
        return neighbour_list
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray? {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)
        var pos = 0
        if (rowStride == width) { // likely
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer[nv21, pos, width]
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, savePixel.inv().toByte())
                if (uBuffer[0] == savePixel.inv().toByte()) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }

    private fun toBitmap(image: Image): Bitmap? {
        val nv21: ByteArray? = YUV_420_888toNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun readFromSP(): HashMap<String, SimilarityClassifier.Recognition>? {
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val defValue = Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue)
        val token: TypeToken<HashMap<String?, SimilarityClassifier.Recognition?>> =
            object : TypeToken<HashMap<String?, SimilarityClassifier.Recognition?>>() {}
        val retrievedMap: java.util.HashMap<String, SimilarityClassifier.Recognition> =
            Gson().fromJson(json, token.type)

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

    private fun displaynameListview() {
        val builder = AlertDialog.Builder(this)
        // System.out.println("Registered"+registered);
        if (registered!!.isEmpty()) builder.setTitle("No Faces Added!!") else builder.setTitle("Recognitions:")

        // add a checkbox list
        val names = arrayOfNulls<String>(registered!!.size)
        val checkedItems = BooleanArray(registered!!.size)
        var i = 0
        registered?.forEach { key ->
            names[i] = key.toString()
            checkedItems[i] = false
            i += 1
        }
        builder.setItems(names, null)
        builder.setPositiveButton(
            "OK"
        ) { dialog, which -> }

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun testHyperparameter() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Hyperparameter:")

        // add a checkbox list
        val names = arrayOf("Maximum Nearest Neighbour Distance")
        builder.setItems(names) { dialog, which ->
            when (which) {
                0 -> //                        Toast.makeText(context, "Clicked", Toast.LENGTH_SHORT).show();
                    hyperparameters()
            }
        }
        builder.setPositiveButton(
            "OK"
        ) { dialog, which -> }
        builder.setNegativeButton("Cancel", null)

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun developerMode() {
        if (developerMode) {
            developerMode = false
            Toast.makeText(this, "Developer Mode OFF", Toast.LENGTH_SHORT).show()
        } else {
            developerMode = true
            Toast.makeText(this, "Developer Mode ON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFace() {
        run {
            start = false
            val builder =
                AlertDialog.Builder(this)
            builder.setTitle("Enter Name")

            // Set up the input
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            // Set up the buttons
            builder.setPositiveButton(
                "ADD"
            ) { dialog, which -> //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

                //Create and Initialize new object with Face embeddings and Name.
                val result = SimilarityClassifier.Recognition(
                    "0", "", -1f
                )
                result.extra = embeedings
                registered!![input.text.toString()] = result
                start = true
            }
            builder.setNegativeButton(
                "Cancel"
            ) { dialog, which ->
                start = true
                dialog.cancel()
            }
            builder.show()
        }
    }

    private fun clearnameList() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Do you want to delete all Recognitions?")
        builder.setPositiveButton(
            "Delete All"
        ) { dialog, which ->
            registered!!.clear()
            Toast.makeText(this, "Recognitions Cleared", Toast.LENGTH_SHORT).show()
        }
        registered?.let { insertToSP(it, 1) }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun updatenameListview() {
        val builder = AlertDialog.Builder(this)
        if (registered!!.isEmpty()) {
            builder.setTitle("No Faces Added!!")
            builder.setPositiveButton("OK", null)
        } else {
            builder.setTitle("Select Recognition to delete:")

            // add a checkbox list
            val names = arrayOfNulls<String>(registered!!.size)
            val checkedItems = BooleanArray(registered!!.size)
            var i = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                registered?.forEach { (key, recognition) ->
                    names[i] = key
                    checkedItems[i] = false
                    i += 1
                }
            }
            builder.setMultiChoiceItems(
                names, checkedItems
            ) { dialog, which, isChecked -> // user checked or unchecked a box
                //Toast.makeText(MainActivity.this, names[which], Toast.LENGTH_SHORT).show();
                checkedItems[which] = isChecked
            }
            builder.setPositiveButton(
                "OK"
            ) { dialog, which -> // System.out.println("status:"+ Arrays.toString(checkedItems));
                for (i in checkedItems.indices) {
                    //System.out.println("status:"+checkedItems[i]);
                    if (checkedItems[i]) {
                        //                                Toast.makeText(MainActivity.this, names[i], Toast.LENGTH_SHORT).show();
                        registered!!.remove(names[i]!!)
                    }
                }
                registered?.let {
                    insertToSP(it, 2)
                }
                Toast.makeText(this, "Recognitions Updated", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Cancel", null)

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun hyperparameters() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Euclidean Distance")
        builder.setMessage("0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:")
        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)
        val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
        input.setText(distance.toString())
        // Set up the buttons
        builder.setPositiveButton(
            "Update"
        ) { dialog, which -> //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();
            distance = input.text.toString().toFloat()
            val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putFloat("distance", distance)
            editor.apply()
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun loadphoto() {
        start = false
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            SELECT_PICTURE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                val selectedImageUri = data?.data
                try {
                    val impphoto = selectedImageUri?.let { getBitmapFromUri(it)?.let { InputImage.fromBitmap(it, 0) } }
                    detector!!.process(impphoto!!).addOnSuccessListener { faces ->
                        if (faces.size != 0) {
                            binding.button3.text = "Recognize"
                            binding.imageButton.visibility = View.VISIBLE
                            binding.textView.visibility = View.INVISIBLE
                            binding.imageView.visibility = View.VISIBLE
                            binding.textView2.text = "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                            val face = faces[0]
                            //                                System.out.println(face);

                            //write code to recreate bitmap from source
                            //Write code to show bitmap to canvas
                            var frame_bmp: Bitmap? = null
                            try {
                                frame_bmp = getBitmapFromUri(selectedImageUri)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                            val frame_bmp1: Bitmap? = rotateBitmap(frame_bmp!!, 0, flipX, false)

                            //face_preview.setImageBitmap(frame_bmp1);
                            val boundingBox = RectF(face.boundingBox)
                            val cropped_face: Bitmap? = getCropBitmapByCPU(frame_bmp1, boundingBox)
                            val scaled = getResizedBitmap(cropped_face!!, 112, 112)
                            // face_preview.setImageBitmap(scaled);
                            recognizeImage(scaled!!)
                            addFace()
                            //                                System.out.println(boundingBox);
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }
                    }.addOnFailureListener {
                        start = true
                        Toast.makeText(this, "Failed to add", Toast.LENGTH_SHORT).show()
                    }
                    binding.imageView.setImageBitmap(getBitmapFromUri(selectedImageUri))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun insertToSP(
        jsonMap: java.util.HashMap<String, SimilarityClassifier.Recognition>,
        mode: Int
    ) {
        if (mode == 1) jsonMap.clear() else if (mode == 0) jsonMap.putAll(readFromSP()!!)
        val jsonString = Gson().toJson(jsonMap)
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("map", jsonString)
        editor.apply()
        Toast.makeText(this, "Recognitions Saved", Toast.LENGTH_SHORT).show()
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()
        return image
    }
}