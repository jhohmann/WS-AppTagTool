package com.example.tagtool

enum class TagListType {
    OLD,
    NEW
}

data class TagRecord(
    val number: Int,
    val code: Long,
    val listType: TagListType
) {
    // Little Endian im NFC-Speicher
    fun bytes(): ByteArray = byteArrayOf(
        (code and 0xFF).toByte(),
        ((code ushr 8) and 0xFF).toByte(),
        ((code ushr 16) and 0xFF).toByte(),
        ((code ushr 24) and 0xFF).toByte()
    )

    // Darstellung in der ursprünglichen Byte-Reihenfolge
    fun hexCode(): String =
        "%08X".format(code)
}