package com.example.tagtool

import android.content.Context
import org.json.JSONArray

class TagRepository(context: Context) {

    val oldRecords: List<TagRecord>
    val newRecords: List<TagRecord>

    private val recordsByCode: Map<Long, TagRecord>

    init {
        oldRecords = load(
            context,
            "tag_list_old.json",
            TagListType.OLD
        )

        newRecords = load(
            context,
            "tag_list_new.json",
            TagListType.NEW
        )

        val allRecords = oldRecords + newRecords

        require(
            allRecords.map { it.code }.distinct().size ==
                    allRecords.size
        ) {
            "Doppelte Tag-Codes gefunden"
        }

        recordsByCode = allRecords.associateBy { it.code }
    }

    private fun load(
        context: Context,
        filename: String,
        type: TagListType
    ): List<TagRecord> {

        val json = context.assets.open(filename)
            .bufferedReader()
            .use { it.readText() }

        val array = JSONArray(json)

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)

            TagRecord(
                number = item.getInt("number"),
                code = item.getString("data").toLong(16),
                listType = type
            )
        }.sortedBy { it.number }
    }

    fun findByCode(code: Long): TagRecord? =
        recordsByCode[code]

    fun findByBytes(bytes: ByteArray): TagRecord? {
        require(bytes.size == 4)

        val code = bytes.foldIndexed(0L) {
                index, result, byte ->
            result or (
                    (byte.toLong() and 0xFF) shl (index * 8)
                    )
        }

        return findByCode(code)
    }

    fun getRecords(type: TagListType): List<TagRecord> =
        when (type) {
            TagListType.OLD -> oldRecords
            TagListType.NEW -> newRecords
        }

    fun findByNumber(
        type: TagListType,
        number: Int
    ): TagRecord? =
        getRecords(type).find { it.number == number }
}