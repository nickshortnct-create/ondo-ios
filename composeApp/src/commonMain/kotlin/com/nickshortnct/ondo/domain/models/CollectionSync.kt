package com.nickshortnct.ondo.domain.models

/** Album and playlist list syncs store summary data; tracks load when opened. */
fun DomainSongCollection.needsRemoteSongSync(): Boolean =
	songs.size < songCount
