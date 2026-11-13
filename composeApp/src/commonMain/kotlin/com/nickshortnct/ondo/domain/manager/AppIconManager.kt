package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.domain.models.settings.AppIconVariant

expect class AppIconManager {
	fun setVariant(newVariant: AppIconVariant)
	fun getIcon(variant: AppIconVariant): Any?
}
