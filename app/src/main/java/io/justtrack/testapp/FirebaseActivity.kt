package io.justtrack.testapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import io.justtrack.JustTrack
import io.justtrack.JustTrackSdk
import io.justtrack.testapp.databinding.ActivityFirebaseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

class FirebaseActivity : BaseActivity<ActivityFirebaseBinding>() {
    private val storeFBID = MutableLiveData<String?>(null)
    private val cacheFBID = MutableLiveData<String?>(null)
    private val installId = MutableLiveData<String?>(null)
    private val sdk: JustTrackSdk = JustTrack.getInstance()!!

    override fun getViewBinding(): ActivityFirebaseBinding {
        return ActivityFirebaseBinding.inflate(layoutInflater)
    }

    @SuppressLint("SetTextI18n")
    override fun initialize() {
        super.initialize()
        storeFBID.observeForever {
            binding.storeFirebaseTextView.text = "store : $it"
        }

        cacheFBID.observeForever {
            binding.cacheFirebaseTextView.text = "cache : $it"
        }

        installId.observeForever {
            binding.installIdTextView.text = "installID : $it"
        }

        binding.fetchBtn.setOnClickListener {
            fetchData()
        }

        binding.setId.setOnClickListener {
            displaySetIdDialog()
        }

        binding.changeStoreIdBtn.setOnClickListener {
            displaySetStoreId()
        }

        binding.changeCacheIdBtn.setOnClickListener {
            displaySetCacheId()
        }

        binding.checkInstallIdBtn.setOnClickListener {
            checkInstallIdChange()
        }

        fetchData()
    }

    private fun fetchData() {
        val preference = getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        storeFBID.postValue(preference.getString(KEY_STORED_ID, null))
        cacheFBID.postValue(preference.getString(KEY_PENDING_ID, null))
        installId.postValue(preference.getString(KEY_INSTALL_ID, null))
    }

    private fun checkInstallIdChange() {
        AlertDialog.Builder(this).apply {
            title = "Change Attribution InstallID"
            val input = EditText(this@FirebaseActivity)
            input.setText(installId.value ?: "")
            setView(input)
            setPositiveButton(
                "change",
            ) { _, _ ->
                val text = input.text.toString()
                runBlocking {
                    val justTrackClass = Class.forName("io.justtrack.JustTrackSdkImpl")
                    justTrackClass.getDeclaredMethod(
                        "checkInstallIdChange",
                        UUID::class.java,
                    ).apply {
                        isAccessible = true
                        invoke(sdk, UUID.fromString(text))
                        isAccessible = false
                    }
                }
            }
        }.create().show()
    }

    private fun displaySetIdDialog() {
        AlertDialog.Builder(this).apply {
            title = "Change Firebase ID"
            val input = EditText(this@FirebaseActivity)
            input.setText(storeFBID.value ?: "")
            setView(input)
            setPositiveButton(
                "change",
            ) { _, _ ->
                val text = input.text.toString()
                runBlocking {
                    withContext(Dispatchers.IO) {
                        sdk.setFirebaseAppInstanceId(text).get()
                        displayToast("Send Complete")
                    }
                }
            }
        }.create().show()
    }

    private fun displaySetCacheId() {
        AlertDialog.Builder(this).apply {
            title = "Change Cached Firebase ID"
            val input = EditText(this@FirebaseActivity)
            input.setText(cacheFBID.value ?: "")
            setView(input)
            setPositiveButton(
                "change",
            ) { _, _ ->
                val text = input.text.toString()
                getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).edit().apply {
                    putString(KEY_PENDING_ID, text)
                }.apply()
            }
        }.create().show()
    }

    private fun displaySetStoreId() {
        AlertDialog.Builder(this).apply {
            title = "Change Store Firebase ID"
            val input = EditText(this@FirebaseActivity)
            input.setText(storeFBID.value ?: "")
            setView(input)
            setPositiveButton(
                "change",
            ) { _, _ ->
                val text = input.text.toString()
                getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).edit().apply {
                    putString(KEY_STORED_ID, text)
                }.apply()
            }
        }.create().show()
    }

    private fun displayToast(text: String) {
        this.runOnUiThread {
            Toast.makeText(this@FirebaseActivity, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "FirebaseActivity"
        const val STORE_NAME = "justtrack-attribution-firebase-store"

        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_PENDING_ID = "pending_id"
        private const val KEY_STORED_ID = "stored_id"
    }
}
