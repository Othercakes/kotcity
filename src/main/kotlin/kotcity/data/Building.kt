package kotcity.data

import javafx.scene.paint.Color
import kotcity.pathfinding.Path
import kotcity.pathfinding.Direction
import kotcity.util.randomElement
import kotlin.reflect.KClass
import java.util.UUID

/**
 * Represents something in (or external) to the city that can have [Tradeable]s in its possession.
 */
interface HasInventory {
    /**
     * How much [Tradeable.MONEY] that the given thing has.
     */
    fun balance(): Int
    fun addInventory(tradeable: Tradeable, quantity: Int): Int
    fun setInventory(tradeable: Tradeable, quantity: Int): Int
    fun subtractInventory(tradeable: Tradeable, quantity: Int): Int
    fun summarizeInventory(): String
    fun quantityOnHand(tradeable: Tradeable): Int
    fun transferInventory(to: TradeEntity, tradeable: Tradeable, quantity: Int): Int
}

/**
 * This is used for something in the [CityMap] that has REAL trackable inventory.
 * The reason we have "[HasConcreteInventory]" and not is because the outside [NationalTradeEntity] does not REALLY have anything.
 * It just pretends to.
 */
interface HasConcreteInventory : HasInventory {
    val inventory: Inventory

    override fun balance(): Int {
        return inventory.quantity(Tradeable.MONEY)
    }

    override fun addInventory(tradeable: Tradeable, quantity: Int): Int {
        return inventory.add(tradeable, quantity)
    }

    override fun setInventory(tradeable: Tradeable, quantity: Int): Int {
        return inventory.put(tradeable, quantity)
    }

    override fun subtractInventory(tradeable: Tradeable, quantity: Int): Int {
        if (inventory.has(tradeable, quantity)) {
            return inventory.subtract(tradeable, quantity)
        }
        return 0
    }

    /**
     * Used to print a textual description of the [Tradeable]s that this thing has.
     */
    override fun summarizeInventory(): String {
        val inventoryBuffer = StringBuffer()
        inventory.forEach { tradeable, qty ->
            inventoryBuffer.append("Has $qty $tradeable\n")
        }
        return inventoryBuffer.toString()
    }

    override fun quantityOnHand(tradeable: Tradeable): Int {
        return inventory.quantity(tradeable)
    }

    /**
     * Send [Tradeable] to another [TradeEntity] but only if we actually have it
     * @TODO implement partial fulfillment...
     * @param to other [TradeEntity] to send to
     * @param tradeable which [Tradeable] to transact in
     * @param quantity how many [Tradeable] to send
     * @return how many [Tradeable] were actually sent to [to]
     */
    override fun transferInventory(to: TradeEntity, tradeable: Tradeable, quantity: Int): Int {
        if (inventory.has(tradeable, quantity)) {
            inventory.subtract(tradeable, quantity)
            to.building()?.addInventory(tradeable, quantity)
            return quantity
        }
        return 0
    }
}

interface HasContracts {
    fun summarizeContracts(): String
    fun currentQuantityForSale(tradeable: Tradeable): Int
    fun currentQuantityWanted(tradeable: Tradeable): Int
    fun totalBeingSold(tradeable: Tradeable): Int
    fun totalBeingBought(tradeable: Tradeable): Int
    fun consumesQuantity(tradeable: Tradeable): Int
    fun producesQuantity(tradeable: Tradeable): Int
    fun productList(): List<Tradeable>

    fun needsAnyContracts(): Boolean {

        Tradeable.values().forEach { tradeable ->
            if (currentQuantityWanted(tradeable) > 0) {
                return true
            }
        }
        return false
    }
}

interface HasConcreteContacts : HasContracts {

    val contracts: MutableList<Contract>
    val consumes: MutableMap<Tradeable, Int>
    val produces: MutableMap<Tradeable, Int>
    val cityMap: CityMap

    override fun consumesQuantity(tradeable: Tradeable): Int {
        return consumes[tradeable] ?: 0
    }

    override fun producesQuantity(tradeable: Tradeable): Int {
        return produces[tradeable] ?: 0
    }

    override fun summarizeContracts(): String {
        val summaryBuffer = StringBuffer()

        consumes.keys.distinct().toList().forEach {
            summaryBuffer.append("Consumes: ${consumes[it]} $it\n")
        }

        produces.keys.distinct().toList().forEach {
            summaryBuffer.append("Produces: ${produces[it]} $it\n")
        }

        contracts.forEach {
            // summaryBuffer.append(it.toString() + "\n")
            if (it.to.building() == this) {
                summaryBuffer.append("Receiving ${it.quantity} ${it.tradeable} from ${it.from.description()}\n")
            }
            if (it.from.building() == this) {
                summaryBuffer.append("Sending ${it.quantity} ${it.tradeable} to ${it.to.description()}\n")
            }
        }
        return summaryBuffer.toString()
    }

    override fun currentQuantityForSale(tradeable: Tradeable): Int {
        val filter = { contract: Contract -> contract.from.building() }
        return calculateAvailable(produces.toMap(), tradeable, filter)
    }

    override fun currentQuantityWanted(tradeable: Tradeable): Int {
        val consumesCount = (consumes[tradeable] ?: 0)
        synchronized(contracts) {
            val contractCount =
                contracts.filter { it.to.building() == this && it.tradeable == tradeable }.map { it.quantity }.sum()
            return consumesCount - contractCount
        }
    }

    private fun calculateAvailable(
        hash: Map<Tradeable, Int>,
        tradeable: Tradeable,
        filter: (Contract) -> Building?
    ): Int {
        val inventoryCount = hash[tradeable] ?: 0
        synchronized(contracts) {
            val contractCount =
                contracts.toList().filter { filter(it) == this && it.tradeable == tradeable }.map { it.quantity }.sum()
            return inventoryCount - contractCount
        }
    }

    fun addContract(contract: Contract) {
        synchronized(contracts) {
            this.contracts.add(contract)
        }
    }

    fun voidContractsWith(otherEntity: TradeEntity) {
        synchronized(contracts) {
            contracts.removeAll {
                it.to == otherEntity || it.from == otherEntity
            }
        }
    }

    fun voidRandomContract() {
        if (contracts.count() > 0) {
            contracts.randomElement()?.let {
                val otherBuilding = it.to
                voidContractsWith(otherBuilding)
            }
        }
    }

    override fun totalBeingSold(tradeable: Tradeable): Int {
        return contracts.filter { it.from.building() == this && it.tradeable == tradeable }.map { it.quantity }.sum()
    }

    override fun totalBeingBought(tradeable: Tradeable): Int {
        synchronized(contracts) {
            return contracts.filter { it.to.building() == this && it.tradeable == tradeable }.map { it.quantity }.sum()
        }
    }

    override fun productList() = produces.keys.distinct()

    fun hasAnyContracts(): Boolean {
        synchronized(contracts) {
            return contracts.count() > 0
        }
    }
}

fun uuid(): String {
    val uuid = UUID.randomUUID()
    return uuid.toString()
}

/**
 * The basic unit of simulation in this game. Represents a building and all its attributes.
 * We currently count [Road] as building as well as [PowerLine].
 */
abstract class Building(override val cityMap: CityMap) : HasConcreteInventory, HasConcreteContacts {

    companion object {
        /**
         * Used by the [CityFileAdapter] to help turn the names in the datafiles to the actual classes.
         * @param name name of Building
         */
        fun classByString(name: String?): KClass<out Building>? {
            return when (name) {
                "Residential" -> Residential::class
                "Commercial" -> Commercial::class
                "Industrial" -> Industrial::class
                "Road" -> Road::class
                "Railroad" -> Railroad::class
                "TrainStation" -> TrainStation::class
                "RailDepot" -> RailDepot::class
                "Civic" -> Civic::class
                "PowerPlant" -> PowerPlant::class
                else -> {
                   null
                }
            }
        }
    }

    /**
     * Width in [BlockCoordinate] of the building
     */
    abstract var width: Int

    /**
     * Height in [BlockCoordinate] of the building
     */
    abstract var height: Int

    /**
     * Used for things like [PowerPlant] so we know what it is (coal/nuclear)
     */
    open val variety: String? = null

    /**
     * Friendly name (eg. "Slummy apartment") of building
     */
    open var name: String? = null

    /**
     * Filename of sprite asset used to paint this building (I guess maybe this should live in renderer?)
     * @TODO This kind of violates my idea to keep sim / renderer / UI separate
     */
    open var sprite: String? = null

    /**
     * Extended description of this building
     */
    open var description: String? = null

    /**
     * true if we have power, false if not
     */
    var powered = false

    /**
     * Unique ID of building
     * @TODO do we even need this?
     */
    val uuid = uuid()

    /**
     * How many units of power this building needs to be happy
     */
    open val powerRequired = 0

    /**
     * Eventually, how much $$$ is required to keep this building going. Probably going to be used for civic buildings
     * like town hall.
     */
    open var upkeep: Int = 0

    /**
     * How happy the building is. This can be positive or negative.
     */
    open var happiness: Int = 0

    /**
     * Default border color, used for rendering
     * @TODO should probably move this to renderer... this does not need to live "in" a [Building]
     */
    open var borderColor: Color = Color.PINK

    /**
     * How much pollution this building generates...
     */
    open var pollution: Double = 0.0

    /**
     * List of [Zot] that this building suffers from. Populated by... [ZotPopulator]
     */
    var zots = listOf<Zot>()

    /**
     * A list of [Tradeable] (with quantities) that this building consumes
     */
    override val consumes: MutableMap<Tradeable, Int> = mutableMapOf()
    override val produces: MutableMap<Tradeable, Int> = mutableMapOf()
    override val inventory: Inventory = Inventory()
    override val contracts: MutableList<Contract> = mutableListOf()
    var goodwill: Int = 0
    var level = 1

    override fun equals(other: Any?): Boolean {
        if (other !is Building) {
            return false
        }
        return uuid == other.uuid
    }

    override fun toString(): String {
        return "Building(class=${this.javaClass} uuid=$uuid)"
    }

    fun zone(): Zone? {
        return when {
            this is Residential -> return Zone.RESIDENTIAL
            this is Commercial -> return Zone.COMMERCIAL
            this is Industrial -> return Zone.INDUSTRIAL
            else -> {
                null
            }
        }
    }

    init {
        // everyone gets 10 dollars...
        addInventory(Tradeable.MONEY, DEFAULT_MONEY)
    }

    fun createContract(otherTradeEntity: TradeEntity, tradeable: Tradeable, quantity: Int, path: Path?) {
        val ourBlocks = cityMap.coordinatesForBuilding(this)
        if (ourBlocks == null) {
            println("Sorry, we couldn't find one of the buildings!")
            return
        }
        val ourLocation = CityTradeEntity(ourBlocks, this)
        val newContract = Contract(otherTradeEntity, ourLocation, tradeable, quantity, path)
        synchronized(contracts) {
            if (otherTradeEntity.currentQuantityForSale(tradeable) >= newContract.quantity) {
                contracts.add(newContract)
                otherTradeEntity.addContract(newContract)
            } else {
                println(
                    "Tried to make an invalid contract: $newContract but failed because ${otherTradeEntity.description()} doesn't have enough $tradeable (it has ${otherTradeEntity.currentQuantityForSale(
                        tradeable
                    )})"
                )
            }
        }
    }

    fun payWorkers() {
        val workContracts = contracts.filter { it.to.building() == this && it.tradeable == Tradeable.LABOR }.toList()
        workContracts.forEach { contract ->
            if (inventory.has(Tradeable.MONEY, contract.quantity)) {
                transferInventory(contract.from, Tradeable.MONEY, contract.quantity)
            } else {
                // whoops... we are bankrupt!
                inventory.put(Tradeable.MONEY, 0)
            }
        }
    }

}

class Residential(override val cityMap: CityMap) : LoadableBuilding(cityMap) {

    override fun currentQuantityWanted(tradeable: Tradeable): Int {
        val consumesCount = (consumes[tradeable] ?: 0) * 1.5
        synchronized(contracts) {
            val contractCount =
                contracts.filter { it.to.building() == this && it.tradeable == tradeable }.map { it.quantity }.sum()
            return (consumesCount - contractCount).toInt()
        }
    }

    override var borderColor: Color = Color.GREEN

}

class Commercial(override val cityMap: CityMap) : LoadableBuilding(cityMap) {
    override var borderColor: Color = Color.BLUE
}

class Industrial(override val cityMap: CityMap) : LoadableBuilding(cityMap) {
    override var borderColor: Color = Color.GOLD
}

class Civic(override val cityMap: CityMap) : LoadableBuilding(cityMap) {
    override var borderColor: Color = Color.DARKGRAY
}

const val DEFAULT_MONEY = 10
val POWER_PLANT_TYPES = listOf("coal", "nuclear")

class PowerPlant(override val variety: String, cityMap: CityMap) : Building(cityMap) {

    var powerGenerated: Int = 0

    override var width = 4
    override var height = 4
    override var borderColor: Color = Color.BLACK

    init {
        if (!POWER_PLANT_TYPES.contains(variety)) {
            throw RuntimeException("Invalid power plant type: $variety")
        }
        when (variety) {
            "coal" -> {
                this.powerGenerated = 2000; this.description = "Coal Power Plant"
            }
            "nuclear" -> {
                this.powerGenerated = 5000; this.description = "Nuclear Power Plant"
            }
        }
        // let's make it consume workers...
        this.consumes[Tradeable.LABOR] = 10
    }
}

class FireStation(cityMap: CityMap) : Building(cityMap) {
    override var width = 3
    override var height = 3
    override val powerRequired = 1
    override var description: String? = "Fire Station"
}

class PoliceStation(cityMap: CityMap) : Building(cityMap) {
    override var width = 3
    override var height = 3
    override val powerRequired = 1
    override var description: String? = "Police Station"
}

class TrainStation(cityMap: CityMap) : Building(cityMap) {
    override var width = 3
    override var height = 3
    override val powerRequired = 1
    override var description: String? = "Train Station"
}

class RailDepot(cityMap: CityMap) : Building(cityMap) {
    override var width = 3
    override var height = 3
    override val powerRequired = 1
    override var description: String? = "Rail Depot"
}

class Road(cityMap: CityMap, val direction: Direction = Direction.STATIONARY) : Building(cityMap) {
    override var width = 1
    override var height = 1
    override var borderColor: Color = Color.BLACK
    override var description: String? = "Road"
}

class Railroad(cityMap: CityMap) : Building(cityMap) {
    override var width = 1
    override var height = 1
    override var borderColor: Color = Color.GREY
    override var description: String? = "Railroad"
}

class PowerLine(cityMap: CityMap) : Building(cityMap) {
    override var width = 1
    override var height = 1
    override val powerRequired = 1
    override var borderColor: Color = Color.BLACK
    override var description: String? = "Power Line"
}

open class LoadableBuilding(cityMap: CityMap) : Building(cityMap) {
    override var height: Int = 1
    override var width: Int = 1
}
