package com.wip.kpsd

sealed interface DescriptorValue

data class LongValue(val value: Int) : DescriptorValue
data class DoubleValue(val value: Double) : DescriptorValue
data class BooleanValue(val value: Boolean) : DescriptorValue
data class TextValue(val value: String) : DescriptorValue
data class EnumValue(val type: String, val value: String) : DescriptorValue
data class UnitDoubleValue(val units: String, val value: Double) : DescriptorValue
data class UnitFloatValue(val units: String, val value: Float) : DescriptorValue
data class ListValue(val values: List<DescriptorValue>) : DescriptorValue

data class RawDataValue(val data: ByteArray) : DescriptorValue {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawDataValue) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

data class DescriptorStructure(
    val name: String,
    val classID: String,
    val properties: Map<String, DescriptorValue>
) : DescriptorValue

data class ClassStructure(val name: String, val classID: String) : DescriptorValue

sealed interface ReferenceItem
data class PropertyReference(val name: String, val classID: String, val keyID: String) : ReferenceItem
data class ClassReference(val name: String, val classID: String) : ReferenceItem
data class EnumReference(val name: String, val classID: String, val typeID: String, val value: String) : ReferenceItem
data class OffsetReference(val name: String, val classID: String, val offset: Int) : ReferenceItem
data class IdentifierReference(val id: Int) : ReferenceItem
data class IndexReference(val index: Int) : ReferenceItem
data class NameReference(val name: String, val classID: String) : ReferenceItem

data class ReferenceStructure(val items: List<ReferenceItem>) : DescriptorValue

object PsdDescriptor {

    val unitsMap = mapOf(
        "#Ang" to "Angle",
        "#Rsl" to "Density",
        "#Rlt" to "Distance",
        "#Nne" to "None",
        "#Prc" to "Percent",
        "#Pxl" to "Pixels",
        "#Mlm" to "Millimeters",
        "#Pnt" to "Points",
        "RrPi" to "Picas",
        "RrIn" to "Inches",
        "RrCm" to "Centimeters"
    )

    val unitsMapRev = unitsMap.entries.associate { it.value to it.key }

    fun readAsciiStringOrClassId(reader: PsdReader): String {
        val length = reader.readInt32()
        val readLen = if (length == 0) 4 else length
        return reader.readAsciiString(readLen)
    }

    fun writeAsciiStringOrClassId(writer: PsdWriter, value: String) {
        if (value.length == 4 && value != "warp" && value != "time" && value != "hold" && value != "list") {
            writer.writeInt32(0)
            writer.writeSignature(value)
        } else {
            writer.writeInt32(value.length)
            writer.writeAsciiString(value)
        }
    }

    fun readClassStructure(reader: PsdReader): ClassStructure {
        val name = reader.readUnicodeString()
        val classID = readAsciiStringOrClassId(reader)
        return ClassStructure(name, classID)
    }

    fun writeClassStructure(writer: PsdWriter, name: String, classID: String) {
        writer.writeUnicodeString(name)
        writeAsciiStringOrClassId(writer, classID)
    }

    fun readVersionAndDescriptor(reader: PsdReader): DescriptorStructure {
        val version = reader.readUint32()
        if (version != 16L) {
            println("ERROR: Invalid descriptor version $version at offset ${reader.offset - 4}")
            throw IllegalStateException("Invalid descriptor version: $version")
        }
        return readDescriptorStructure(reader)
    }

    fun writeVersionAndDescriptor(writer: PsdWriter, name: String, classID: String, descriptor: DescriptorStructure) {
        writer.writeUint32(16L) // version
        writeDescriptorStructure(writer, descriptor)
    }

    fun readDescriptorStructure(reader: PsdReader): DescriptorStructure {
        val clazz = readClassStructure(reader)
        val itemsCount = reader.readUint32().toInt()
        val properties = mutableMapOf<String, DescriptorValue>()

        for (i in 0 until itemsCount) {
            val key = readAsciiStringOrClassId(reader)
            val type = reader.readSignature()
            val data = readOSType(reader, type)
            properties[key] = data
        }

        return DescriptorStructure(clazz.name, clazz.classID, properties)
    }

    fun writeDescriptorStructure(writer: PsdWriter, descriptor: DescriptorStructure) {
        writeUnicodeStringWithPadding(writer, descriptor.name)
        writeAsciiStringOrClassId(writer, descriptor.classID)

        val keys = descriptor.properties.keys
        writer.writeUint32(keys.size.toLong())

        for (key in keys) {
            val value = descriptor.properties[key] ?: continue
            writeAsciiStringOrClassId(writer, key)
            val typeSig = getOSTypeSignature(value)
            writer.writeSignature(typeSig)
            writeOSType(writer, typeSig, value)
        }
    }

    private fun getOSTypeSignature(value: DescriptorValue): String {
        return when (value) {
            is LongValue -> "long"
            is DoubleValue -> "doub"
            is BooleanValue -> "bool"
            is TextValue -> "TEXT"
            is EnumValue -> "enum"
            is UnitDoubleValue -> "UntF"
            is UnitFloatValue -> "UnFl"
            is RawDataValue -> "tdta"
            is ListValue -> "VlLs"
            is DescriptorStructure -> "Objc"
            is ReferenceStructure -> "obj "
            is ClassStructure -> "type"
        }
    }

    private fun readOSType(reader: PsdReader, type: String): DescriptorValue {
        return when (type) {
            "long" -> LongValue(reader.readInt32())
            "doub" -> DoubleValue(reader.readFloat64())
            "bool" -> BooleanValue(reader.readUint8() != 0)
            "TEXT" -> TextValue(reader.readUnicodeString())
            "enum" -> {
                val enumType = readAsciiStringOrClassId(reader)
                val value = readAsciiStringOrClassId(reader)
                EnumValue(enumType, value)
            }
            "UntF" -> {
                val units = reader.readSignature()
                val value = reader.readFloat64()
                val resolvedUnits = unitsMap[units] ?: units
                UnitDoubleValue(resolvedUnits, value)
            }
            "UnFl" -> {
                val units = reader.readSignature()
                val value = reader.readFloat32()
                val resolvedUnits = unitsMap[units] ?: units
                UnitFloatValue(resolvedUnits, value)
            }
            "tdta" -> {
                val length = reader.readInt32()
                RawDataValue(reader.readBytes(length))
            }
            "VlLs" -> {
                val length = reader.readInt32()
                val items = mutableListOf<DescriptorValue>()
                for (i in 0 until length) {
                    val itemType = reader.readSignature()
                    items.add(readOSType(reader, itemType))
                }
                ListValue(items)
            }
            "Objc", "GlbO" -> readDescriptorStructure(reader)
            "type", "GlbC" -> readClassStructure(reader)
            "obj " -> readReferenceStructure(reader)
            else -> throw java.lang.UnsupportedOperationException("Unsupported OSType: $type")
        }
    }

    private fun writeOSType(writer: PsdWriter, type: String, value: DescriptorValue) {
        when (type) {
            "long" -> writer.writeInt32((value as LongValue).value)
            "doub" -> writer.writeFloat64((value as DoubleValue).value)
            "bool" -> writer.writeUint8(if ((value as BooleanValue).value) 1 else 0)
            "TEXT" -> writeUnicodeStringWithPadding(writer, (value as TextValue).value)
            "enum" -> {
                val enumVal = value as EnumValue
                writeAsciiStringOrClassId(writer, enumVal.type)
                writeAsciiStringOrClassId(writer, enumVal.value)
            }
            "UntF" -> {
                val unitVal = value as UnitDoubleValue
                val unitSig = unitsMapRev[unitVal.units] ?: unitVal.units
                writer.writeSignature(unitSig)
                writer.writeFloat64(unitVal.value)
            }
            "UnFl" -> {
                val unitVal = value as UnitFloatValue
                val unitSig = unitsMapRev[unitVal.units] ?: unitVal.units
                writer.writeSignature(unitSig)
                writer.writeFloat32(unitVal.value)
            }
            "tdta" -> {
                val rawVal = value as RawDataValue
                writer.writeInt32(rawVal.data.size)
                writer.writeBytes(rawVal.data)
            }
            "VlLs" -> {
                val listVal = value as ListValue
                writer.writeInt32(listVal.values.size)
                for (v in listVal.values) {
                    val typeSig = getOSTypeSignature(v)
                    writer.writeSignature(typeSig)
                    writeOSType(writer, typeSig, v)
                }
            }
            "Objc" -> writeDescriptorStructure(writer, value as DescriptorStructure)
            "type" -> writeClassStructure(writer, (value as ClassStructure).name, (value as ClassStructure).classID)
            "obj " -> writeReferenceStructure(writer, value as ReferenceStructure)
            else -> throw java.lang.UnsupportedOperationException("Unsupported OSType write: $type")
        }
    }

    private fun readReferenceStructure(reader: PsdReader): ReferenceStructure {
        val itemsCount = reader.readInt32()
        val items = mutableListOf<ReferenceItem>()

        for (i in 0 until itemsCount) {
            val type = reader.readSignature()
            when (type) {
                "prop" -> {
                    val clazz = readClassStructure(reader)
                    val keyID = readAsciiStringOrClassId(reader)
                    items.add(PropertyReference(clazz.name, clazz.classID, keyID))
                }
                "Clss" -> {
                    val clazz = readClassStructure(reader)
                    items.add(ClassReference(clazz.name, clazz.classID))
                }
                "Enmr" -> {
                    val clazz = readClassStructure(reader)
                    val typeID = readAsciiStringOrClassId(reader)
                    val value = readAsciiStringOrClassId(reader)
                    items.add(EnumReference(clazz.name, clazz.classID, typeID, value))
                }
                "rele" -> {
                    val clazz = readClassStructure(reader)
                    val offset = reader.readInt32()
                    items.add(OffsetReference(clazz.name, clazz.classID, offset))
                }
                "Idnt" -> items.add(IdentifierReference(reader.readInt32()))
                "indx" -> items.add(IndexReference(reader.readInt32()))
                "name" -> {
                    val clazz = readClassStructure(reader)
                    val name = reader.readUnicodeString()
                    items.add(NameReference(name, clazz.classID))
                }
                else -> throw java.lang.UnsupportedOperationException("Invalid descriptor reference type: $type")
            }
        }
        return ReferenceStructure(items)
    }

    private fun writeReferenceStructure(writer: PsdWriter, ref: ReferenceStructure) {
        writer.writeInt32(ref.items.size)
        for (item in ref.items) {
            when (item) {
                is PropertyReference -> {
                    writer.writeSignature("prop")
                    writeClassStructure(writer, item.name, item.classID)
                    writeAsciiStringOrClassId(writer, item.keyID)
                }
                is ClassReference -> {
                    writer.writeSignature("Clss")
                    writeClassStructure(writer, item.name, item.classID)
                }
                is EnumReference -> {
                    writer.writeSignature("Enmr")
                    writeClassStructure(writer, item.name, item.classID)
                    writeAsciiStringOrClassId(writer, item.typeID)
                    writeAsciiStringOrClassId(writer, item.value)
                }
                is OffsetReference -> {
                    writer.writeSignature("rele")
                    writeClassStructure(writer, item.name, item.classID)
                    writer.writeInt32(item.offset)
                }
                is IdentifierReference -> {
                    writer.writeSignature("Idnt")
                    writer.writeInt32(item.id)
                }
                is IndexReference -> {
                    writer.writeSignature("indx")
                    writer.writeInt32(item.index)
                }
                is NameReference -> {
                    writer.writeSignature("name")
                    writeClassStructure(writer, item.name, item.classID)
                    writer.writeUnicodeString(item.name + "\u0000")
                }
            }
        }
    }

    private fun writeUnicodeStringWithPadding(writer: PsdWriter, text: String) {
        writer.writeUint32(text.length + 1L)
        for (char in text) {
            writer.writeUint16(char.code)
        }
        writer.writeUint16(0) // padding \0
    }
}
