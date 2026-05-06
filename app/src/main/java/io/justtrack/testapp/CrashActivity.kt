package io.justtrack.testapp

import android.util.Log
import android.widget.Toast
import io.justtrack.AppEvent
import io.justtrack.JustTrack
import io.justtrack.testapp.databinding.ActivityCrashBinding

class CrashActivity : BaseActivity<ActivityCrashBinding>() {
    override fun getViewBinding(): ActivityCrashBinding {
        return ActivityCrashBinding.inflate(layoutInflater)
    }

    override fun initialize() {
        System.loadLibrary("lib-crash")
        super.initialize()

        JustTrack.getInstance()?.installUncaughtExceptionHandler()

        binding.crashNormallyButton.setOnClickListener {
            throw NullPointerException("Exception")
        }

        binding.crashAnrButton.setOnClickListener {
            val lock1 = Any()
            val lock2 = Any()

            synchronized(lock1) {
                Thread { synchronized(lock2) { synchronized(lock1) {} } }
                    .start()
                Thread.sleep(1000)
                synchronized(lock2) {}
            }
        }

        binding.softAnrButton.setOnClickListener {
            Thread.sleep(10000)
            Log.e(TAG, "ANR Finished")
            Toast.makeText(this, "ANR finished", Toast.LENGTH_SHORT).show()
        }

        binding.fastAnrButton.setOnClickListener {
            Thread.sleep(1000)
            Log.e(TAG, "ANR Finished")
            Toast.makeText(this, "ANR finished", Toast.LENGTH_SHORT).show()
        }

        binding.crashDeadlockButton.setOnClickListener {
            val lockA = Object()
            val lockB = Object()

            synchronized(lockA) {
                val t =
                    Thread {
                        synchronized(lockB) {
                            synchronized(lockA) {
                                // never reached
                            }
                        }
                    }.apply {
                        name = "deadlockingThread"
                        start()
                    }

                Thread.sleep(1000L)

                synchronized(lockB) {
                    // never reached
                }

                t.join()
            }
        }

        binding.crashNativeAbortButton.setOnClickListener {
            crashAbort()
        }

        binding.crashNativeIllButton.setOnClickListener {
            crashILL()
        }

        binding.crashNativeSegfaultButton.setOnClickListener {
            crashSEGV()
        }

        binding.crashNativeFpeButton.setOnClickListener {
            crashFPE()
        }

        binding.crashNativeBusButton.setOnClickListener {
            crashBUS()
        }

        binding.crashNativePipeButton.setOnClickListener {
            crashPIPE()
        }

        binding.crashNativeTrapButton.setOnClickListener {
            crashTRAP()
        }

        binding.generateEventThenCrashButton.setOnClickListener {
            for (i: Int in 0 until 15) {
                MainApplication.sdk?.track(AppEvent("test"))
            }
            throw NullPointerException("Exception")
        }
    }

    private external fun crashAbort()

    private external fun crashILL()

    private external fun crashSEGV()

    private external fun crashFPE()

    private external fun crashBUS()

    private external fun crashPIPE()

    private external fun crashTRAP()

    companion object {
        const val TAG = "CrashReport"
    }
}
