package com.nickshortnct.ondo.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.nickshortnct.ondo.wear.OndoWearApp

/** Keeps the login and phone Now Playing current even while the watch app is closed. */
class PhoneDataListenerService : WearableListenerService() {
	override fun onDataChanged(events: DataEventBuffer) {
		val link = (application as OndoWearApp).phoneLink
		events.forEach { event ->
			if (event.type == DataEvent.TYPE_CHANGED) {
				link.handle(event.dataItem.uri.path, DataMapItem.fromDataItem(event.dataItem).dataMap)
			}
		}
	}
}
