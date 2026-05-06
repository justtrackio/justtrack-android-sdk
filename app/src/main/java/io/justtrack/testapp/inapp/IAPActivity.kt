package io.justtrack.testapp.inapp

import io.justtrack.testapp.BaseActivity
import io.justtrack.testapp.databinding.ActivityIapBinding

class IAPActivity : BaseActivity<ActivityIapBinding>() {
    private val presenter: IAPPresenter = IAPPresenterImpl()

    override fun getViewBinding(): ActivityIapBinding {
        return ActivityIapBinding.inflate(layoutInflater)
    }

    override fun initialize() {
        super.initialize()

        val isAutoIap = intent.getBooleanExtra("auto_iap", false)

        presenter.start(this, isAutoIap)
        binding.consumableButton.setOnClickListener {
            presenter.displaySelectionDialogConsumable()
        }

        binding.nonConsumableButton.setOnClickListener {
            presenter.displaySelectionDialogNonConsumable()
        }

        binding.subscriptionButton.setOnClickListener {
            presenter.displaySelectionDialogSubs()
        }

        binding.consumeAllButton.setOnClickListener {
            presenter.consumeAllProduct()
        }
    }
}
