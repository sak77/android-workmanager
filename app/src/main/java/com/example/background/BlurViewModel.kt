/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.background

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.*
import com.example.background.workers.BlurWorker
import com.example.background.workers.CleanupWorker
import com.example.background.workers.SaveImageToFileWorker


class BlurViewModel(application: Application) : AndroidViewModel(application) {

    internal var imageUri: Uri? = null
    internal var outputUri: Uri? = null

    val workManager = WorkManager.getInstance(application)

    internal val outputWorkInfos: LiveData<List<WorkInfo>>

    init {
        outputWorkInfos = workManager.getWorkInfosByTagLiveData(TAG_OUTPUT)
    }

    val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()

    /**
     * Create the WorkRequest to apply the blur and save the resulting image
     * @param blurLevel The amount to blur the image
     */
    internal fun applyBlur(blurLevel: Int) {
        /*
        There are two types of WorkRequests:
            OneTimeWorkRequest: A WorkRequest that will only execute once.
            PeriodicWorkRequest: A WorkRequest that will repeat on a cycle.
         */

        /*
        To pass data to Worker class we can create instance of androidx.work.Data
         */
        val inputDataBuilder = Data.Builder()
        imageUri?.let { inputDataBuilder.putString(KEY_IMAGE_URI, imageUri.toString()) }
        val inputImageUri = inputDataBuilder.build()
/*
        val workRequest = OneTimeWorkRequestBuilder<BlurWorker>()
                .setInputData(inputImageUri)
                .build()
        workManager.enqueue(workRequest)
*/

        /*
        Instead of calling workManager.enqueue(), call workManager.beginWith().
        This returns a WorkContinuation, which defines a chain of WorkRequests.
        You can add to this chain of work requests by calling then() method.
         */
        //var continuation = workManager.beginWith(OneTimeWorkRequest.from(CleanupWorker::class.java))

        /*
        To create unique work chain, use beginUniqueWork instead of beginWith().
        Here pass the work request name which is used to identify all workers in the workrequest

        Also set the existingWorkPolicy to either REPLACE; KEEP or APPEND. In this case,
        i set it to replace, so if user selects another image to blur while current work is
        still going on, then the current work gets replaced by new workrequest.
         */
        var continuation = workManager.beginUniqueWork(IMAGE_MANIPULATION_WORK_NAME,
                ExistingWorkPolicy.REPLACE, OneTimeWorkRequest.from(CleanupWorker::class.java))

        //Add work request to blur the image..
        val blurRequest = OneTimeWorkRequest.Builder(BlurWorker::class.java)
                .setInputData(inputImageUri)
                .build()

        //continuation = continuation.then(blurRequest)
        // Add WorkRequests to blur the image the number of times requested
        for (i in 0 until blurLevel) {
            val blurBuilder = OneTimeWorkRequestBuilder<BlurWorker>()

            // Input the Uri if this is the first blur operation
            // After the first blur operation the input will be the output of previous
            // blur operations.
            if (i == 0) {
                blurBuilder.setInputData(inputImageUri)
            }

            continuation = continuation.then(blurBuilder.build())
        }

        //Add workrequest to save image to file system
        /*
        Here we can tag the work request to then track its work status.
        After tagging the workrequest, get WorkInfo instance to get status info.
        Here i have declared outputWorkInfos as an internal val and added an init
        block where i set outputWorkInfos value using workManager.getWorkInfosByTagLiveData()

        Using workInfo you can also get the outputData of the worker class. So here,
        in BlurActivity, observer for WorkInfosLiveData if the list is not empty or not null,
        then i use the synthetic accessor to get outputData from the workRequest.
         */

        /*
        Also, we can create constrains using Constraints.Builder. In this case, i have created
        a constraint that device should be charging and added to the saveImageToFile workRequest
        using setConstraints().
         */
        val saveImage = OneTimeWorkRequest.Builder(SaveImageToFileWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR)
                .addTag(TAG_OUTPUT)
                .build()
        continuation = continuation.then(saveImage)

        //actually start work
        continuation.enqueue()
    }

    private fun uriOrNull(uriString: String?): Uri? {
        return if (!uriString.isNullOrEmpty()) {
            Uri.parse(uriString)
        } else {
            null
        }
    }

    /**
     * Setters
     */
    internal fun setImageUri(uri: String?) {
        imageUri = uriOrNull(uri)
    }

    internal fun setOutputUri(outputImageUri: String?) {
        outputUri = uriOrNull(outputImageUri)
    }

    /*
    Workmanager provides ways to cancel a work by id, Tag or unique chain name.

    In this case, you'll want to cancel work by unique chain name,
    because you want to cancel all work in the chain, not just a particular step.
     */
    internal fun cancelWork() {
        workManager.cancelUniqueWork(IMAGE_MANIPULATION_WORK_NAME)
    }
}
