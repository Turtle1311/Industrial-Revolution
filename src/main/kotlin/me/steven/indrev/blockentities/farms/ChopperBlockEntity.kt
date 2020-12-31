package me.steven.indrev.blockentities.farms

import dev.technici4n.fasttransferlib.api.energy.EnergyIo
import me.steven.indrev.api.machines.Tier
import me.steven.indrev.blockentities.crafters.UpgradeProvider
import me.steven.indrev.config.BasicMachineConfig
import me.steven.indrev.inventories.inventory
import me.steven.indrev.items.upgrade.Upgrade
import me.steven.indrev.registry.MachineRegistry
import me.steven.indrev.utils.*
import net.fabricmc.fabric.api.tool.attribute.v1.FabricToolTags
import net.minecraft.block.*
import net.minecraft.item.*
import net.minecraft.loot.context.LootContext
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.server.world.ServerWorld
import net.minecraft.tag.BlockTags
import net.minecraft.tag.ItemTags
import net.minecraft.util.ItemScatterer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.Chunk

class ChopperBlockEntity(tier: Tier) : AOEMachineBlockEntity<BasicMachineConfig>(tier, MachineRegistry.CHOPPER_REGISTRY), UpgradeProvider {
    init {
        this.inventoryComponent = inventory(this) {
            input {
                slots = intArrayOf(2, 3, 4, 5)
                2 filter { (_, item) -> item.isIn(FabricToolTags.AXES) }
                3 filter { (_, item) -> item is BoneMealItem }
                4..5 filter { (_, item), _ -> item.isIn(ItemTags.SAPLINGS) || (item is BlockItem && item.block is MushroomPlantBlock) }
            }
            output { slots = intArrayOf(6, 7, 8, 9, 10, 11, 12, 13, 14) }
            coolerSlot = 1
        }
    }

    override val maxInput: Double = config.maxInput
    override val maxOutput: Double = 0.0

    private var scheduledBlocks = mutableListOf<BlockPos>().iterator()
    override var range = 5
    var cooldown = 0.0

    override fun machineTick() {
        if (world?.isClient == true) return
        val inventory = inventoryComponent?.inventory ?: return
        val upgrades = getUpgrades(inventory)
        cooldown += Upgrade.getSpeed(upgrades, this)
        val energyCost = Upgrade.getEnergyCost(upgrades, this)
        if (cooldown < config.processSpeed || ticks % 15 != 0 || !canUse(energyCost))
            return
        val area = getWorkingArea()
        if (!scheduledBlocks.hasNext()) {
            // includes tree branches that goes outside the actual area
            val fullArea = area.expand(4.0)
            scheduledBlocks = fullArea.map(::BlockPos).iterator()
        } else {
            var currentChunk: Chunk? = null
            var performedActions = 0
            val axeStack = inventory.getStack(2)
            val axeStackHandler = energyOf(axeStack)
            val brokenBlocks = hashMapOf<BlockPos, BlockState>()
            outer@ while (scheduledBlocks.hasNext() && cooldown > config.processSpeed) {
                val pos = scheduledBlocks.next()
                if (pos.x shr 4 != currentChunk?.pos?.x || pos.z shr 4 != currentChunk.pos.z) {
                    currentChunk = world?.getChunk(pos)
                }
                val blockState = currentChunk?.getBlockState(pos) ?: continue
                if (axeStack != null
                    && !axeStack.isEmpty
                    && tryChop(axeStack, axeStackHandler, pos, blockState)
                ) {
                    cooldown -= config.processSpeed
                    if (!use(energyCost)) break
                    brokenBlocks[pos] = blockState
                    performedActions++
                }
                if (pos.y == this.pos.y && pos in area) {
                    for (slot in 3..5) {
                        val stack = inventory.getStack(slot)
                        if (stack.isEmpty || !tryUse(blockState, stack, pos)) continue
                        cooldown -= config.processSpeed
                        if (!use(energyCost)) break
                        brokenBlocks[pos] = blockState
                        performedActions++
                    }
                }
            }
            brokenBlocks.forEach { (blockPos, blockState) ->
                val droppedStacks = blockState.getDroppedStacks(
                    LootContext.Builder(world as ServerWorld).random(world?.random)
                        .parameter(LootContextParameters.ORIGIN, blockPos.toVec3d())
                        .parameter(LootContextParameters.TOOL, axeStack)
                )
                droppedStacks.forEach {
                    if (!inventory.output(it))
                        ItemScatterer.spawn(world, blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(), it)
                }
            }
            temperatureComponent?.tick(performedActions > 0)
            workingState = performedActions > 0
        }
        cooldown = 0.0
    }

    private fun tryChop(
        axeStack: ItemStack,
        axeEnergyHandler: EnergyIo?,
        blockPos: BlockPos,
        blockState: BlockState,
    ): Boolean {
        val block = blockState.block
        when {
            block.isIn(BlockTags.LOGS) || block is MushroomBlock || block == Blocks.MUSHROOM_STEM -> {
                if (axeEnergyHandler != null && !axeEnergyHandler.use(1.0))
                    return false
                else {
                    axeStack.damage(1, world?.random, null)
                    if (axeStack.damage >= axeStack.maxDamage)
                        axeStack.decrement(1)
                }
                world?.setBlockState(blockPos, Blocks.AIR.defaultState, 3)
            }
            block is LeavesBlock -> {
                world?.setBlockState(blockPos, Blocks.AIR.defaultState, 3)
            }
            else -> return false
        }
        return true
    }

    private fun tryUse(blockState: BlockState, itemStack: ItemStack, pos: BlockPos): Boolean {
        val item = itemStack.item
        val block = blockState.block
        when {
            item is BoneMealItem && itemStack.count > 1
                    && (block.isIn(BlockTags.SAPLINGS) || block is MushroomPlantBlock)
                    && block is Fertilizable
                    && block.isFertilizable(world, pos, blockState, false)
                    && block.canGrow(world, world?.random, pos, blockState) -> {
                block.grow(world as ServerWorld, world?.random, pos, blockState)
                world?.syncWorldEvent(2005, pos, 0)
                itemStack.decrement(1)
            }
            block == Blocks.AIR
                    && item is BlockItem
                    && (item.isIn(ItemTags.SAPLINGS) || item.block is MushroomPlantBlock)
                    && item.block.defaultState.canPlaceAt(world, pos)
                    && itemStack.count > 1 -> {
                world?.setBlockState(pos, item.block.defaultState, 3)
                itemStack.decrement(1)
            }
            else -> return false
        }
        return true
    }

    override fun getUpgradeSlots(): IntArray = intArrayOf(15, 16, 17, 18)

    override fun getAvailableUpgrades(): Array<Upgrade> = Upgrade.DEFAULT

    override fun getBaseValue(upgrade: Upgrade): Double =
        when (upgrade) {
            Upgrade.ENERGY -> config.energyCost
            Upgrade.SPEED -> 1.0
            Upgrade.BUFFER -> config.maxEnergyStored
            else -> 0.0
        }

    override fun getEnergyCapacity(): Double = Upgrade.getBuffer(this)
}