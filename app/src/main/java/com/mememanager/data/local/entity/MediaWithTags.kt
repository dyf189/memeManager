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
        )
    )
    val tags: List<TagEntity> = emptyList(),
    /** 所属组别（0/1 个） */
    @Relation(
        parentColumn = "groupId",
        entityColumn = "id"
    )
    val group: GroupEntity? = null
) {
    /** 按 sortOrder 排序后的标签列表 */
    val sortedTags: List<TagEntity> get() = tags.sortedBy { it.sortOrder }
}
