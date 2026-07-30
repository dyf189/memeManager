package com.mememanager.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MediaWithTags(
    @Embedded
    val media: MediaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MediaTagCrossRef::class,
            parentColumn = "mediaId",
            entityColumn = "tagId"
        ),
        orderBy = "sortOrder ASC"
    )
    val tags: List<TagEntity> = emptyList()
)
