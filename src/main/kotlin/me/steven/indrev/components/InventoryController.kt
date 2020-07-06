package me.steven.indrev.components

import me.steven.indrev.inventories.IRInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.util.math.Direction

class InventoryController(supplier: () -> IRInventory) {
    val inventory: IRInventory = supplier().also { it.controller = this }

    val itemConfig: MutableMap<Direction, Mode> = mutableMapOf<Direction, Mode>().also { map ->
        Direction.values().forEach { dir -> map[dir] = Mode.NONE }
    }

    fun fromTag(tag: CompoundTag?) {
        val tagList = tag?.get("Inventory") as ListTag? ?: ListTag()
        tagList.indices.forEach { i ->
            val stackTag = tagList.getCompound(i)
            val slot = stackTag.getInt("Slot")
            inventory.setStack(slot, ItemStack.fromTag(stackTag))
        }
        if (tag?.contains("ItemConfig") == true) {
            val icTag = tag.getCompound("ItemConfig")
            Direction.values().forEach { dir ->
                val value = icTag.getString(dir.toString()).toUpperCase()
                if (value.isNotEmpty()) {
                    val mode = Mode.valueOf(value)
                    itemConfig[dir] = mode
                }
            }
        }
    }

    fun toTag(tag: CompoundTag): CompoundTag {
        val tagList = ListTag()
        for (i in 0 until inventory.size()) {
            val stackTag = CompoundTag()
            stackTag.putInt("Slot", i)
            tagList.add(inventory.getStack(i).toTag(stackTag))
        }
        tag.put("Inventory", tagList)
        val icTag = CompoundTag()
        itemConfig.forEach { (dir, mode) ->
            icTag.putString(dir.toString(), mode.toString())
        }
        tag.put("ItemConfig", icTag)
        return tag
    }

    enum class Mode(val rgb: Int) {
        INPUT(0x0000ff), OUTPUT(0xffA500), NONE(-1);

        fun next(): Mode = when (this) {
            INPUT -> OUTPUT
            OUTPUT -> NONE
            NONE -> INPUT
        }
    }
}