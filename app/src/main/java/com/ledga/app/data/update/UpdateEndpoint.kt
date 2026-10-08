package com.ledga.app.data.update

/** Where the update check reads releases, and whether this build may install what it finds (R140). */
data class UpdateEndpoint(val releasesUrl: String, val offersUpdates: Boolean) {
    companion object {
        /** Spec §13.4: the newest 30 releases, enough for Version history. */
        const val GITHUB = "https://api.github.com/repos/ngenohkevin/ledga/releases?per_page=30"
    }
}

/** Read at each check: Ledga dev's local test source can change while the app runs (owner call A). */
fun interface UpdateEndpoints {
    fun current(): UpdateEndpoint
}
