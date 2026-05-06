package io.justtrack

internal interface BreadCrumbReporter {
    fun addBreadCrumb(breadCrumb: BreadCrumb)
}
