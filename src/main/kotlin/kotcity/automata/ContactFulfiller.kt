package kotcity.automata

import kotcity.data.*
import kotcity.pathfinding.Path
import kotcity.util.Debuggable
import kotcity.util.randomElements
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking

fun <A>Collection<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
    map { async(CommonPool) { f(it) } }.forEach { it.await() }
}

class ContactFulfiller(val cityMap: CityMap) : Debuggable {

    override var debug = false
    set(value) {
        field = value
        resourceFinder.debug = value
    }
    private val resourceFinder = ResourceFinder(cityMap)

    init {
        resourceFinder.debug = debug
    }

    fun signContracts(shuffled: Boolean = true, maxMillis: Long = 5000) {

        val contractCollection = if (shuffled) {
            locationsNeedingContracts().shuffled()
        } else {
            locationsNeedingContracts()
        }

        val howManyNeedContracts = contractCollection.size
        var howManyProcessed = 0

        val startAt = System.currentTimeMillis()

        contractCollection.toList().shuffled().takeWhile { System.currentTimeMillis() - startAt < maxMillis }.forEach { location: Location ->
            if (location != null) {
                val delta = System.currentTimeMillis() - startAt
                debug("Still ${maxMillis - delta} millis left to sign contracts...")
                handleBuilding(location)
                howManyProcessed += 1
            }
        }

        debug("$howManyNeedContracts needed contracts and we processed $howManyProcessed")

    }

    private fun handleBuilding(entry: Location) {
        val coordinate = entry.coordinate
        val building = entry.building

        val buildingTradeEntity = CityTradeEntity(coordinate, building)

        val buildingBlocks = cityMap.buildingBlocks(coordinate, building)
        building.consumes.forEach { tradeable, _ ->
            var done = false
            while (building.currentQuantityWanted(tradeable) > 0 && !done) {
                val needsCount = building.currentQuantityWanted(tradeable)
                debug("Building $building needs $needsCount $tradeable")
                val bestSource: Pair<TradeEntity, Path>? = findNearbySource(buildingBlocks, tradeable, needsCount)

                if (bestSource == null) {
                    debug("Could not find a source for $tradeable")
                    done = true
                }

                if (bestSource != null) {
                    debug("Found best source for $tradeable... ${bestSource.first.description()}!")
                    val otherTradeEntity = bestSource.first
                    val pathToOther = bestSource.second
                    val quantity = building.currentQuantityWanted(tradeable).coerceAtMost(otherTradeEntity.currentQuantityForSale(tradeable))

                    if (quantity > 0) {
                        building.createContract(otherTradeEntity, tradeable, quantity, pathToOther)
                        debug("")
                        debug("${building.name}: Signed contract with ${otherTradeEntity.description()} to buy $quantity $tradeable")
                        debug("${building.name} now requires ${building.currentQuantityWanted(tradeable)} $tradeable")
                        debug("${otherTradeEntity.description()} has ${otherTradeEntity.currentQuantityForSale(tradeable)} left.")
                        // debug("New setup: ${building.summarizeContracts()}")
                    }

                } else {
                    debug("Could not find $needsCount $tradeable for ${building.name} at $coordinate")
                }
            }
        }

        building.produces.forEach { tradeable, _ ->
            var done = false
            while (building.currentQuantityForSale(tradeable) > 0 && !done) {
                val forSaleCount = building.currentQuantityForSale(tradeable)
                debug("${building.description} trying to sell $forSaleCount $tradeable")
                if (resourceFinder.quantityWantedNearby(tradeable, coordinate) > 0) {
                    val entityAndPath = resourceFinder.nearestBuyingTradeable(tradeable, buildingBlocks, MAX_RESOURCE_DISTANCE)

                    if (entityAndPath == null) {
                        debug("Want to sell $tradeable but can't find a path to a buyer!")
                        done = true
                    }

                    if (entityAndPath != null) {
                        val otherEntity = entityAndPath.first
                        val path = entityAndPath.second

                        val quantity = otherEntity.currentQuantityWanted(tradeable).coerceAtMost(forSaleCount)

                        debug("Found a buyer for our $tradeable. It wants $quantity and we are selling $forSaleCount")

                        if (quantity > 0) {
                            val newContract = Contract(buildingTradeEntity, otherEntity, tradeable, quantity, path)
                            debug("")
                            debug("${building.name}: Signed contract with ${otherEntity.description()} to sell $quantity $tradeable")
                            otherEntity.addContract(newContract)
                            buildingTradeEntity.addContract(newContract)
                            debug("${building.name} now has ${building.currentQuantityForSale(tradeable)} $tradeable left to provide.")
                            // debug("${otherEntity.description()} still wants to buy ${otherEntity.currentQuantityWanted(tradeable)} $tradeable")
                        }


                    }
                } else {
                    debug("Cannot find any place to sell $tradeable nearby. Won't bother with pathfinding...")
                    done = true
                }

            }
        }
    }

    // TODO: this is most likely bugged...
    private fun findNearbySource(buildingBlocks: List<BlockCoordinate>, tradeable: Tradeable, needsCount: Int): Pair<TradeEntity, Path>? {
        (1..needsCount).reversed().forEach {
            val source = resourceFinder.findSource(buildingBlocks, tradeable, it)
            if (source != null) {
                return source
            }
        }
        return null
    }

    private fun locationsNeedingContracts(): List<Location> {
        return cityMap.locations().filter { it.building.needsAnyContracts() }
    }

    private fun entitiesWithContracts(): List<TradeEntity> {
        return cityMap.locations().map { CityTradeEntity(it.coordinate, it.building) }.filter { it.hasAnyContracts() }
    }

    fun terminateRandomContracts() {

        val totalBuildings = cityMap.locations().count()

        if (totalBuildings == 0) {
            return
        }

        if (entitiesWithContracts().size > 100)
        {
            val howMany = 5
            cityMap.locations().randomElements(howMany)?.forEach { location ->
                val buildings = cityMap.cachedLocationsIn(location.coordinate)
                val blockAndBuilding = buildings.toList().randomElements(1)?.first()
                if (blockAndBuilding != null) {
                    val building = blockAndBuilding.building
                    building.voidRandomContract()
                }
            }
        }

    }
}