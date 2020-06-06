package me.steven.indrev.blockentities.generators

import me.steven.indrev.components.InventoryController
import me.steven.indrev.components.Property
import me.steven.indrev.components.TemperatureController
import me.steven.indrev.inventories.DefaultSidedInventory
import me.steven.indrev.items.CoolerItem
import me.steven.indrev.items.rechargeable.RechargeableItem
import me.steven.indrev.registry.MachineRegistry
import me.steven.indrev.utils.Tier
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.container.ArrayPropertyDelegate
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag

class CoalGeneratorBlockEntity :
    GeneratorBlockEntity(Tier.MK1, MachineRegistry.COAL_GENERATOR_REGISTRY) {

    init {
        this.propertyDelegate = ArrayPropertyDelegate(5)
        this.inventoryController = InventoryController({ this }) {
            DefaultSidedInventory(3, intArrayOf(2), intArrayOf()) { slot, stack ->
                val item = stack?.item
                when {
                    item is RechargeableItem && item.canOutput -> slot == 0
                    item is CoolerItem -> slot == 1
                    slot == 2 -> BURN_TIME_MAP.containsKey(stack?.item)
                    else -> false
                }
            }
        }
        this.temperatureController = TemperatureController({ this }, 0.08, 900..2000, 2500.0)
    }

    private var burnTime: Int by Property(3, 0)
    private var maxBurnTime: Int by Property(4, 0)

    override fun shouldGenerate(): Boolean {
        if (burnTime > 0) burnTime--
        else if (maxStoredPower > energy) {
            val inventory = inventoryController?.getInventory() ?: return false
            val invStack = inventory.getInvStack(2)
            if (!invStack.isEmpty && BURN_TIME_MAP.containsKey(invStack.item)) {
                burnTime = BURN_TIME_MAP[invStack.item] ?: return false
                maxBurnTime = burnTime
                invStack.count--
                if (invStack.isEmpty) inventory.setInvStack(2, ItemStack.EMPTY)
                else inventory.setInvStack(2, invStack)
            }
        }
        markDirty()
        return burnTime > 0 && energy < maxStoredPower
    }

    override fun getGenerationRatio(): Double = 0.15

    override fun fromTag(tag: CompoundTag?) {
        super.fromTag(tag)
        burnTime = tag?.getInt("BurnTime") ?: 0
        maxBurnTime = tag?.getInt("MaxBurnTime") ?: 0
    }

    override fun toTag(tag: CompoundTag?): CompoundTag {
        tag?.putInt("BurnTime", burnTime)
        tag?.putInt("MaxBurnTime", maxBurnTime)
        return super.toTag(tag)
    }

    override fun fromClientTag(tag: CompoundTag?) {
        super.fromClientTag(tag)
        burnTime = tag?.getInt("BurnTime") ?: 0
        maxBurnTime = tag?.getInt("MaxBurnTime") ?: 0
    }

    override fun toClientTag(tag: CompoundTag?): CompoundTag {
        tag?.putInt("BurnTime", burnTime)
        tag?.putInt("MaxBurnTime", maxBurnTime)
        return super.toClientTag(tag)
    }

    companion object {
        private val BURN_TIME_MAP = AbstractFurnaceBlockEntity.createFuelTimeMap()
    }
}