package com.flowerencee9.myplayground.fr2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.flowerencee9.myplayground.R
import com.flowerencee9.myplayground.databinding.ActivityFrBinding
import com.flowerencee9.myplayground.fr.SimilarityClassifier.Recognition
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
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.experimental.inv
import kotlin.math.sqrt

class FRActivity2 : AppCompatActivity() {
    companion object {
        fun myIntent(context: Context) = Intent(context, FRActivity2::class.java)
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val OUTPUT_SIZE = 192 //Output size of model

        private const val SELECT_PICTURE = 1
        private const val MY_CAMERA_REQUEST_CODE = 100

        private const val modelFile = "mobile_face_net.tflite" //model name
    }

    private lateinit var detector: FaceDetector

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var tfLite: Interpreter
    private lateinit var cameraSelector: CameraSelector
    private var developerMode = false
    private var distance = 1.0f
    private var start = true
    private var flipX: Boolean = false
    var context: Context = this
    private var camFace = CameraSelector.LENS_FACING_BACK //Default Back Camera


    private lateinit var intValues: IntArray
    private var inputSize = 112 //Input size for model

    private var isModelQuantized = false
    private lateinit var embeddings: Array<FloatArray>

    private lateinit var cameraProvider: ProcessCameraProvider

    private lateinit var registered: HashMap<String, Recognition>


    private lateinit var binding: ActivityFrBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registered = readFromSP() //Load saved faces from memory when app starts
        binding = ActivityFrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
        with(binding) {
            imageView.visibility = View.VISIBLE
            textAbovePreview.text = "Recognized Face:"
            imageButton.visibility = View.INVISIBLE
            button2.setOnClickListener {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Select Action:")

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
                builder.setItems(names) { _, which ->
                    when (which) {
                        0 -> displayNameListview()
                        1 -> updateNameListview()
                        2 -> insertToSP(registered, 0) //mode: 0:save all, 1:clear all, 2:update all
                        3 -> registered.putAll(readFromSP())
                        4 -> clearNameList()
                        5 -> loadPhoto()
                        6 -> testHyperParameter()
                        7 -> developerMode()
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
            button5.setOnClickListener {
                if (camFace == CameraSelector.LENS_FACING_BACK) {
                    camFace = CameraSelector.LENS_FACING_FRONT
                    flipX = true
                } else {
                    camFace = CameraSelector.LENS_FACING_BACK
                    flipX = false
                }
                cameraProvider.unbindAll()
                cameraBind()
            }
            imageButton.setOnClickListener { addFace() }
            button3.setOnClickListener {
                if (button3.text.toString() == "Recognize") {
                    start = true
                    textAbovePreview.text = "Recognized Face:"
                    button3.text = "Add Face"
                    imageButton.visibility = View.INVISIBLE
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.INVISIBLE
                    textView2.text = ""
                    //binding.textView2.setVisibility(View.INVISIBLE);
                } else {
                    textAbovePreview.text = "Face Preview: "
                    button3.text = "Recognize"
                    imageButton.visibility = View.VISIBLE
                    textView.visibility = View.INVISIBLE
                    imageView.visibility = View.VISIBLE
                    textView2.text =
                        "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                }
            }
        }
        //Load model
        try {
            tfLite = Interpreter(loadModelFile(this, modelFile))
        } catch (e: IOException) {
            e.printStackTrace()
        }
        //Initialize Face Detector
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(highAccuracyOpts)

        cameraBind()

    }

    private fun testHyperParameter() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select Hyperparameter:")

        // add a checkbox list
        val names = arrayOf("Maximum Nearest Neighbour Distance")
        builder.setItems(names) { _, which ->
            when (which) {
                0 -> hyperParameters()
            }
        }
        builder.setPositiveButton(
            "OK"
        ) { _, _ -> }
        builder.setNegativeButton("Cancel", null)

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun developerMode() {
        if (developerMode) {
            developerMode = false
            Toast.makeText(context, "Developer Mode OFF", Toast.LENGTH_SHORT).show()
        } else {
            developerMode = true
            Toast.makeText(context, "Developer Mode ON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFace() {
        run {
            start = false
            val builder =
                AlertDialog.Builder(context)
            builder.setTitle("Enter Name")

            // Set up the input
            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            // Set up the buttons
            builder.setPositiveButton(
                "ADD"
            ) { _, _ -> //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

                //Create and Initialize new object with Face embeddings and Name.
                val result = Recognition(
                    "0", "", -1f
                )
                result.extra = embeddings
                registered[input.text.toString()] = result
                start = true
            }
            builder.setNegativeButton(
                "Cancel"
            ) { dialog, _ ->
                start = true
                dialog.cancel()
            }
            builder.show()
        }
    }

    private fun clearNameList() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Do you want to delete all Recognitions?")
        builder.setPositiveButton(
            "Delete All"
        ) { _, _ ->
            registered.clear()
            Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show()
        }
        insertToSP(registered, 1)
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun updateNameListview() {
        val builder = AlertDialog.Builder(context)
        if (registered.isEmpty()) {
            builder.setTitle("No Faces Added!!")
            builder.setPositiveButton("OK", null)
        } else {
            builder.setTitle("Select Recognition to delete:")

            // add a checkbox list
            val names = arrayOfNulls<String>(registered.size)
            val checkedItems = BooleanArray(registered.size)
            var i = 0
            registered.entries.forEach { entry: Map.Entry<String, Recognition> ->
                names[i] = entry.key
                checkedItems[i] = false
                i += 1
            }
            builder.setMultiChoiceItems(
                names, checkedItems
            ) { _, which, isChecked -> // user checked or unchecked a box
                checkedItems[which] = isChecked
            }
            builder.setPositiveButton(
                "OK"
            ) { _, which ->
                for (index in checkedItems.indices) {
                    if (checkedItems[index]) {
                        registered.remove(names[index])
                    }
                }
                insertToSP(registered, 2) //mode: 0:save all, 1:clear all, 2:update all
                Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Cancel", null)

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun hyperParameters() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Euclidean Distance")
        builder.setMessage("0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:")
        // Set up the input
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)
        val sharedPref = getSharedPreferences("Distance", MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)
        input.setText(distance.toString())
        // Set up the buttons
        builder.setPositiveButton(
            "Update"
        ) { _, _ -> //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();
            distance = input.text.toString().toFloat()
            val sp = getSharedPreferences("Distance", MODE_PRIVATE)
            val editor = sp.edit()
            editor.putFloat("distance", distance)
            editor.apply()
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, _ -> dialog.cancel() }
        builder.show()
    }


    private fun displayNameListview() {
        val builder = AlertDialog.Builder(context)
        // System.out.println("Registered"+registered);
        if (registered.isEmpty()) builder.setTitle("No Faces Added!!") else builder.setTitle("Recognitions:")

        // add a checkbox list
        val names = arrayOfNulls<String>(registered.size)
        val checkedItems = BooleanArray(registered.size)
        var i = 0
        registered.entries.forEach { entry: Map.Entry<String, Recognition> ->
            names[i] = entry.key
            checkedItems[i] = false
            i += 1
        }
        builder.setItems(names, null)
        builder.setPositiveButton(
            "OK"
        ) { _, _ -> }

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): ByteBuffer {
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
        previewView = findViewById(R.id.previewView)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(camFace)
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
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
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            }

            //Process acquired image to detect faces
            val result = detector!!.process(
                image!!
            )
                .addOnSuccessListener { faces ->
                    if (faces.size != 0) {
                        val face = faces[0] //Get first face from detected faces

                        //mediaImage to Bitmap
                        val frameBmp = toBitmap(mediaImage)
                        val rot = imageProxy.imageInfo.rotationDegrees

                        //Adjust orientation of Face
                        val frameBmp1 = rotateBitmap(frameBmp, rot, false, false)

                        //Get bounding box of face
                        val boundingBox = RectF(face.boundingBox)

                        //Crop out bounding box from whole Bitmap(image)
                        var croppedFace = getCropBitmapByCPU(frameBmp1, boundingBox)
                        if (flipX) croppedFace = rotateBitmap(croppedFace, 0, flipX, false)
                        //Scale the acquired Face to 112*112 which is required input for model
                        val scaled = getResizedBitmap(croppedFace, 112, 112)
                        if (start) recognizeImage(scaled) //Send scaled bitmap to create face embeddings.

                    } else {
                        if (registered.isEmpty()) binding.textView.text =
                            "Add Face" else binding.textView.text = "No Face Detected!"
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

    private fun recognizeImage(bitmap: Bitmap) {

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
        val outputMap: MutableMap<Int, Any> = HashMap()
        embeddings =
            Array(1) { FloatArray(OUTPUT_SIZE) } //output of model will be stored in this variable
        outputMap[0] = embeddings
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap) //Run model
        var distanceLocal = Float.MAX_VALUE
        val id = "0"
        val label = "?"

        //Compare new face with saved Faces.
        if (registered.size > 0) {
            val nearest = findNearest(embeddings[0]) //Find 2 closest matching face
            if (nearest[0] != null) {
                val name = nearest[0]!!.first //get name and distance of closest matching face
                // label = name;
                distanceLocal = nearest[0]!!.second
                if (developerMode) {
                    binding.textView.text = if (distanceLocal < distance) {
                        """
                                            Nearest: $name
                                            Dist: ${String.format("%.3f", distanceLocal)}
                                            2nd Nearest: ${nearest[1]!!.first}
                                            Dist: ${String.format("%.3f", nearest[1]!!.second)}
                                            """.trimIndent()
                    } else {
                        """
                                                                Unknown 
                                                                Dist: ${
                            String.format(
                                "%.3f",
                                distanceLocal
                            )
                        }
                                                                Nearest: $name
                                                                Dist: ${
                            String.format(
                                "%.3f",
                                distanceLocal
                            )
                        }
                                                                2nd Nearest: ${nearest[1]!!.first}
                                                                Dist: ${
                            String.format(
                                "%.3f",
                                nearest[1]!!.second
                            )
                        }
                                                                """.trimIndent()
                    }
                } else {
                    binding.textView.text = if (distanceLocal < distance) name else "Unknown"
                }
            }
        }


    }

    //Compare Faces by distance between face embeddings
    private fun findNearest(emb: FloatArray): List<Pair<String, Float>?> {
        val neighbourList: MutableList<Pair<String, Float>?> = ArrayList()
        var ret: Pair<String, Float>? = null //to get closest match
        var prevRet: Pair<String, Float>? = null //to get second closest match
        registered.entries.forEach { entry: Map.Entry<String, Recognition> ->
            val name = entry.key
            val knownEmb = (entry.value.extra as Array<FloatArray?>)[0]
            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb!![i]
                distance += diff * diff
            }
            distance = sqrt(distance)
            if (ret == null || distance < ret!!.second) {
                prevRet = ret
                ret = Pair(name, distance)
            }
        }
        if (prevRet == null) prevRet = ret
        neighbourList.add(ret)
        neighbourList.add(prevRet)
        return neighbourList
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int = 112, newHeight: Int = 112): Bitmap {
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

    private fun getCropBitmapByCPU(source: Bitmap?, cropRectF: RectF): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        canvas.drawRect(
            RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)
        canvas.drawBitmap(source!!, matrix, paint)
        if (!source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }

    private fun rotateBitmap(
        bitmap: Bitmap?,
        rotationDegrees: Int,
        flipX: Boolean,
        flipY: Boolean = false
    ): Bitmap {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private fun convertYUV420888toNV21(image: Image?): ByteArray {
        val width = image!!.width
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
                vBuffer.put(1, savePixel.inv())
                if (uBuffer[0] == savePixel.inv()) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                ex.printStackTrace()
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

    private fun toBitmap(image: Image?): Bitmap {
        val nv21 = convertYUV420888toNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image!!.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    //Save Faces to Shared Preferences.Conversion of Recognition objects to json string
    private fun insertToSP(jsonMap: HashMap<String, Recognition>, mode: Int) {
        if (mode == 1) //mode: 0:save all, 1:clear all, 2:update all
            jsonMap.clear() else if (mode == 0) jsonMap.putAll(readFromSP()!!)
        val jsonString = Gson().toJson(jsonMap)
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("map", jsonString)
        editor.apply()
        Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show()
    }

    //Load Faces from Shared Preferences.Json String to Recognition object
    private fun readFromSP(): HashMap<String, Recognition> {
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val defValue = Gson().toJson(HashMap<String, Recognition>())
        val json = sharedPreferences.getString("map", defValue)

        val token = object : TypeToken<HashMap<String, Recognition>>() {}
        val retrievedMap = Gson().fromJson<HashMap<String, Recognition>>(json, token.type)


        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
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
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }

    //Load Photo from phone storage
    private fun loadPhoto() {
        start = false
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            SELECT_PICTURE
        )
    }

    //Similar Analyzing Procedure
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                val selectedImageUri = data?.data
                try {
                    val inputImage = InputImage.fromBitmap(getBitmapFromUri(selectedImageUri)!!, 0)
                    detector.process(inputImage).addOnSuccessListener { faces ->
                        if (faces.size != 0) {
                            with(binding) {
                                button3.text = "Recognize"
                                imageButton.visibility = View.VISIBLE
                                textView.visibility = View.INVISIBLE
                                imageView.visibility = View.VISIBLE
                                textView2.text =
                                    "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                            }

                            val face = faces[0]

                            //write code to recreate bitmap from source
                            //Write code to show bitmap to canvas
                            var frameBmp: Bitmap? = null
                            try {
                                frameBmp = getBitmapFromUri(selectedImageUri)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                            val frameBmp1 = rotateBitmap(frameBmp, 0, flipX, false)

                            val boundingBox = RectF(face.boundingBox)
                            val croppedFace = getCropBitmapByCPU(frameBmp1, boundingBox)
                            val scaled = getResizedBitmap(croppedFace, 112, 112)
                            recognizeImage(scaled)
                            addFace()
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }
                    }.addOnFailureListener {
                        start = true
                        Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show()
                    }
                    binding.imageView.setImageBitmap(getBitmapFromUri(selectedImageUri))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri?): Bitmap? {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(uri!!, "r")
        val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()
        return image
    }

}