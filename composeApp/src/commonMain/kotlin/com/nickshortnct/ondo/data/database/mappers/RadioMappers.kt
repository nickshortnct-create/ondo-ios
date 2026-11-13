package com.nickshortnct.ondo.data.database.mappers

import com.nickshortnct.ondo.data.database.entities.RadioEntity
import com.nickshortnct.ondo.domain.models.DomainRadio
import dev.zt64.subsonic.api.model.InternetRadioStation as ApiRadio

fun ApiRadio.toEntity() = RadioEntity(
	radioId = id,
	name = name,
	streamUrl = streamUrl,
	homepageUrl = homepageUrl
)

fun RadioEntity.toDomainModel() = DomainRadio(
	id = radioId,
	name = name,
	streamUrl = streamUrl,
	homepageUrl = homepageUrl
)
