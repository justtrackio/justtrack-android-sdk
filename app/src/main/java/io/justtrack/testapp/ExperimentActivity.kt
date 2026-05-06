package io.justtrack.testapp

import android.util.Log
import android.view.View
import android.widget.Toast
import io.justtrack.Callback
import io.justtrack.config.JusttrackRemoteConfigSettings
import io.justtrack.testapp.databinding.ActivityExperimentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExperimentActivity : BaseActivity<ActivityExperimentBinding>() {
    override fun getViewBinding(): ActivityExperimentBinding {
        return ActivityExperimentBinding.inflate(layoutInflater)
    }

    override fun initialize() {
        super.initialize()

        binding.setTestGroupButton.setOnClickListener(this::onSetTestGroupClick)
        binding.fetchButton.setOnClickListener(this::fetchExperiments)
        binding.activateButton.setOnClickListener(this::activateExperiments)
        binding.fetchAndActivateButton.setOnClickListener(this::fetchAndActivateExperiments)
        binding.testIntervalButton.setOnClickListener(this::setTestInterval)
    }

    private fun onSetTestGroupClick(view: View) {
        val experiment = binding.experimentEditText.text.toString()
        val variant = binding.variantEditText.text.toString()
        val tags = ArrayList<String?>()
        tags.add("test1")
        tags.add("test2")
        MainApplication.sdk!!.setExperimentVariant(experiment, variant, tags, null)
            .registerCallback(
                object : Callback<Void> {
                    override fun resolve(response: Void) {
                        Log.i(TAG, "Set test group completed.")
                    }

                    override fun reject(exception: Throwable) {
                        Log.e(TAG, "Set test group failed", exception)
                    }
                },
            )
    }

    private fun fetchExperiments(view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MainApplication.sdk!!.remoteConfig.fetch().await()
                Log.i(TAG, "fetch experiments complete")
                MainApplication.sdk!!.remoteConfig.getAll()?.forEach {
                    Log.i(TAG, "fetchExperiments: config ${it.experimentId} ${it.configKey} ${it.configValue}")
                }
            } catch (exception: Exception) {
                Log.e(TAG, "fetch experiments failed", exception)
            }
        }
    }

    private fun activateExperiments(view: View) {
        val experimentIds = MainApplication.sdk!!.remoteConfig.getAll()?.map { it.experimentId }
        if (experimentIds == null) {
            Toast.makeText(this, "Please fetch first", Toast.LENGTH_SHORT).show()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    MainApplication.sdk!!.remoteConfig.activate(experimentIds).await()
                    Log.i(TAG, "activate experiments complete")
                } catch (exception: Exception) {
                    Log.e(TAG, "activate experiments failed", exception)
                }
            }
        }
    }

    private fun fetchAndActivateExperiments(view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MainApplication.sdk!!.remoteConfig.fetchAndActivate().await()
                Log.i(TAG, "fetch and activate experiments complete")
            } catch (exception: Exception) {
                Log.e(TAG, "fetch and activate experiments failed", exception)
            }
        }
    }

    private fun setTestInterval(view: View) {
        MainApplication.sdk!!.remoteConfig.setConfig(JusttrackRemoteConfigSettings(60))
    }

    companion object {
        const val TAG = "ExperimentActivity"
    }
}
