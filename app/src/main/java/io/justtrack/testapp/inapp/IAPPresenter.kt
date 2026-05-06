package io.justtrack.testapp.inapp

import android.app.Activity

interface IAPPresenter {
    fun start(
        context: Activity,
        isAutoIap: Boolean,
    )

    fun displaySelectionDialogConsumable()

    fun displaySelectionDialogNonConsumable()

    fun displaySelectionDialogSubs()

    fun consumeAllProduct()
}
