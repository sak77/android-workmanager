package com.example.background.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.TextUtils
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.background.KEY_IMAGE_URI
import com.example.background.R
import timber.log.Timber
import java.sql.Time

class BlurWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {
/*
            val picture = BitmapFactory.decodeResource(
                    applicationContext.resources,
                    R.drawable.test)

            val output = blurBitmap(picture, applicationContext)
*/
        //val outputUri = writeBitmapToFile(applicationContext, output)
        val outputUri = inputData.getString(KEY_IMAGE_URI)

        makeStatusNotification("Blurring image", applicationContext)
        //Add this to slow down worker..
        sleep()

        return try {
            if (TextUtils.isEmpty(outputUri)) {
                Timber.e("Invalid input uri")
                throw IllegalArgumentException("Invalid input uri")
            }

            val resolver = applicationContext.contentResolver
            val picture = BitmapFactory.decodeStream(
                    resolver.openInputStream(Uri.parse(outputUri)))
            val output = blurBitmap(picture, applicationContext)
            //Write bitmap to temp file
            val outputuri = writeBitmapToFile(applicationContext, output)
            //val outputData = Data.Builder().putString(KEY_IMAGE_URI, outputUri.toString()).build()
            val outputData = workDataOf(KEY_IMAGE_URI to  outputUri.toString())
            Result.success(outputData)
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Error applying blur")
            //Here you can also use Result.retry() to try to retry the failed operation
            //based upon backoff criteria 
            Result.failure()
        }
    }

}