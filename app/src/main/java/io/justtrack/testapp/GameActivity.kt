package io.justtrack.testapp

import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import io.justtrack.AppEvent
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdUnit
import io.justtrack.events.JtProgressionEvent
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.testapp.databinding.ActivityGameBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.MessageFormat

class GameActivity : BaseActivity<ActivityGameBinding>() {
    private var publisher: Thread? = null

    override fun getViewBinding(): ActivityGameBinding {
        return ActivityGameBinding.inflate(layoutInflater)
    }

    override fun initialize() {
        binding.restartButton.setOnClickListener {
            onRestartClick()
        }
        binding.winButton.setOnClickListener {
            onWinClick()
        }
        binding.failButton.setOnClickListener {
            onFailClick()
        }
        publisher = Thread { this.runPublisher() }
        publisher!!.start()
    }

    private fun onRestartClick() {
        if (MainApplication.sdk != null) {
            MainApplication.sdk!!.track(JtProgressionEvent("start", "level 1", null, null))
        }
    }

    private fun onWinClick() =
        CoroutineScope(Dispatchers.IO).launch {
            if (MainApplication.sdk != null) {
                MainApplication.sdk!!.track(
                    JtProgressionEvent(
                        "finish",
                        "level 1",
                        null,
                        null,
                    ),
                )

                try {
                    val resultAdcolony =
                        MainApplication.sdk!!.forwardAdImpression(
                            AdImpression(
                                unit = "impression",
                                sdkName = "adColony",
                                network = "adColony",
                            ),
                        ).await()
                    Log.d("TAG", "onWinClick: $resultAdcolony")

                    val resultApplovin =
                        MainApplication.sdk!!.forwardAdImpression(
                            AdImpression(
                                unit = "impression",
                                sdkName = "appLovin",
                                network = "appLovin",
                            ),
                        ).await()
                    Log.d("TAG", "onWinClick: $resultApplovin")

                    MainApplication.sdk!!.forwardAdImpression(
                        AdImpression(
                            unit = "impression",
                            sdkName = "chartboost",
                            network = "chartboost",
                        ),
                    )

                    MainApplication.sdk!!.forwardAdImpression(
                        AdImpression(
                            unit = AdUnit.Banner,
                            sdkName = "unity",
                            network = "Banner_Android",
                        ),
                    )
                    MainApplication.sdk!!.forwardAdImpression(
                        AdImpression(
                            unit = "impression",
                            sdkName = "unity",
                            network = "Interstitial_Android",
                        ),
                    )
                    MainApplication.sdk!!.forwardAdImpression(
                        AdImpression(
                            unit = "impression",
                            sdkName = "unity",
                            network = "Rewarded_Android",
                        ),
                    )
                } catch (exception: SdkNotTrackingException) {
                    Log.e("TAG", "onWinClick: ", exception)
                }
            }
            finish()
        }

    private fun onFailClick() {
        if (MainApplication.sdk != null) {
            MainApplication.sdk!!.track(JtProgressionEvent("fail", "level 1", null, null))
        }
        finish()
    }

    override fun finish() {
        if (publisher != null) {
            publisher!!.interrupt()
            publisher = null
        }
        super.finish()
    }

    private fun runPublisher() {
        var published = 0
        val lbl: TextView = binding.publishedEventsCountTextView
        val enablePublishing: SwitchCompat = binding.publishEventSwitch
        while (true) {
            try {
                Thread.sleep(1000L)
                if (!enablePublishing.isChecked) {
                    continue
                }
                if (MainApplication.sdk != null) {
                    MainApplication.sdk!!.track(AppEvent("random_user_event"))
                }
                published++
                val publishedCount = published
                runOnUiThread {
                    if (publishedCount == 1) {
                        lbl.setText(R.string.published_1_event)
                    } else {
                        lbl.text =
                            MessageFormat.format(
                                getString(R.string.published_n_events),
                                publishedCount,
                            )
                    }
                }
            } catch (e: InterruptedException) {
                // we should terminate
                return
            }
        }
    }
}
