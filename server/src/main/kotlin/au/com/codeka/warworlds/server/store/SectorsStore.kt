package au.com.codeka.warworlds.server.store

import au.com.codeka.warworlds.common.Log
import au.com.codeka.warworlds.common.proto.Sector
import au.com.codeka.warworlds.common.proto.SectorCoord
import au.com.codeka.warworlds.common.proto.Star
import au.com.codeka.warworlds.server.store.base.BaseStore
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.sqrt

/**
 * Sectors store is a special store for storing the details of the sectors.
 *
 *
 * We don't serialize the actual [Sector] object, since that includes a list of derived
 * objects (stars and colonies, etc). We also need to be able to store things like the list of
 * currently-empty sectors, the current bounds of the universe and so on.
 */
class SectorsStore(fileName: String) : BaseStore(fileName) {
  private val log = Log("SectorsStore")

  enum class SectorState(val value: Int) {
    /** A brand new sector, hasn't even had stars generated for it yet.  */
    New(0),

    /** The sector is being generated, don't use yet (but also don't try to re-generate it)  */
    Generating(4),

    /** An empty sector, only stars with native colonies exist.  */
    Empty(1),

    /** A normal sector, has stars with colonies.  */
    NonEmpty(2),

    /** An abandoned sector, has stars with colonies that have been abandoned by a player.  */
    Abandoned(3);

    companion object {
      fun fromValue(value: Int): SectorState {
        for (state in values()) {
          if (state.value == value) {
            return state
          }
        }
        return Empty
      }
    }

  }

  /**
   * Gets the sector at the given x,y coordinates. Returns a blank sector in state "new" if we
   * don't have one created yet.
   */
  fun getSector(x: Long, y: Long): Sector {
    var state: SectorState
    newReader()
        .stmt("SELECT state FROM sectors WHERE x = ? AND y = ?")
        .param(0, x)
        .param(1, y)
        .query().use { res ->
          if (res.next()) {
            state = SectorState.fromValue(res.getInt(0))
            log.info("Got sector state: %d", state.value)
          } else {
            // No sector yet.
            log.info("No sector at (%d, %d)", x, y)
            state = SectorState.New
          }
        }
    val stars: List<Star>? =
      if (state != SectorState.New)
        DataStore.i.stars().getStarsForSector(x, y)
      else
        null
    return Sector(
        x = x,
        y = y,
        state = state.value,
        stars = stars ?: ArrayList(),
        num_colonies = 0)
  }

  /**
   * Finds a sector in the given state, as close to the center of the universe as possible.
   *
   * @return The [SectorCoord] of a sector in the given state, or null if no such sector is
   * found.
   */
  fun findSectorByState(state: SectorState): SectorCoord? {
    val sectorCoords = findSectorsByState(state, 1)
    return if (sectorCoords.isEmpty()) {
      null
    } else sectorCoords[0]
  }

  /**
   * Find the top `count` sectors in the given state, ordered by how far they are from the
   * center of the universe.
   *
   * @param state The [SectorState] you want to find sectors in.
   * @param count The number of sectors to return.
   * @return An array of [SectorCoord] of at most `count` sectors, ordered by their
   * distance to the center of the universe.
   */
  fun findSectorsByState(state: SectorState, count: Int): ArrayList<SectorCoord> {
    newReader()
        .stmt("SELECT x, y FROM sectors WHERE state = ? ORDER BY distance_to_centre ASC")
        .param(0, state.value)
        .query().use { res ->
          val coords = ArrayList<SectorCoord>(count)
          while (res.next() && coords.size < count) {
            coords.add(SectorCoord(x = res.getLong(0), y = res.getLong(1)))
          }
          return coords
        }
  }

  /**
   * Update the given sector's state.
   *
   * @return true if the sector was updated, false if it was not (because the current state didn't
   * match most likely).
   */
  fun updateSectorState(
      coord: SectorCoord, currState: SectorState, newState: SectorState): Boolean {
    val count = newWriter()
        .stmt("UPDATE sectors SET state = ? WHERE x = ? AND y = ? AND state = ?")
        .param(0, newState.value)
        .param(1, coord.x)
        .param(2, coord.y)
        .param(3, currState.value)
        .execute()
    if (count == 0 && currState == SectorState.New) {
      insertNewSector(coord)
      return true
    }
    return (count == 1)
  }

  /**
   * Expands the universe, making it one bigger than before, and creating a bunch of sectors to be
   * generated.
   */
  fun expandUniverse() {
    newTransaction().use { trans ->
      // Find the current bounds of the universe.
      var minX: Long = 0
      var minY: Long = 0
      var maxX: Long = 0
      var maxY: Long = 0
      newReader(trans)
          .stmt("SELECT MIN(x), MIN(y), MAX(x), MAX(y) FROM sectors")
          .query().use { res ->
            if (res.next()) {
              minX = res.getLong(0)
              minY = res.getLong(1)
              maxX = res.getLong(2)
              maxY = res.getLong(3)
            }
          }

      // Find any sectors that are missing within that bounds.
      val missing = ArrayList<SectorCoord>()
      for (y in minY..maxY) {
        val xs: MutableSet<Long> = HashSet()
        newReader(trans)
            .stmt("SELECT x FROM sectors WHERE y = ?")
            .param(0, y)
            .query().use { res ->
              while (res.next()) {
                xs.add(res.getLong(0))
              }
            }
        for (x in minX..maxX) {
          if (!xs.contains(x)) {
            missing.add(SectorCoord(x = x, y = y))
          }
        }
      }

      // If there's no (or not many) gaps, expand the universe by one and add all of those instead.
      if (missing.size < 10) {
        for (x in minX - 1..maxX + 1) {
          missing.add(SectorCoord(x = x, y = minY - 1))
          missing.add(SectorCoord(x = x, y = maxY + 1))
        }
        for (y in minY..maxY) {
          missing.add(SectorCoord(x = minX - 1, y = y))
          missing.add(SectorCoord(x = maxX + 1, y = y))
        }
      }

      // Now add all the new sectors.
      for (coord in missing) {
        insertNewSector(coord)
      }
      trans.commit()
    }
  }

  fun resetSector(coord: SectorCoord) {
    val stars = DataStore.i.stars().getStarsForSector(coord.x, coord.y)
    for (star in stars) {
      DataStore.i.stars().delete(star.id)
    }

    newWriter()
        .stmt("UPDATE sectors SET state = ? WHERE x = ? AND y = ?")
        .param(0, SectorState.New.value)
        .param(1, coord.x)
        .param(2, coord.y)
        .execute()
  }

  private fun insertNewSector(coord: SectorCoord) {
    newWriter()
        .stmt("INSERT INTO sectors (x, y, distance_to_centre, state) VALUES (?, ?, ?, ?)")
        .param(0, coord.x)
        .param(1, coord.y)
        .param(2, sqrt(coord.x * coord.x + coord.y * coord.y.toDouble()))
        .param(3, SectorState.New.value)
        .execute()
  }

  override fun onOpen(diskVersion: Int): Int {
    var version = diskVersion
    if (version == 0) {
      newWriter()
          .stmt("CREATE TABLE sectors (" +
              "  x INTEGER," +
              "  y INTEGER," +
              "  distance_to_centre FLOAT," +
              "  state INTEGER)")
          .execute()
      version++
    }

    // Note: regardless of disk version, we'll reset all "generating" states back to New so they
    // can re-generated (they are probably in a bad state, so delete any stars in there as well)
    val sectorsToReset = ArrayList<SectorCoord>()
    val res = newReader()
        .stmt("SELECT x, y FROM sectors WHERE state = ?")
        .param(0, SectorState.Generating.value)
        .query()
    while (res.next()) {
      val sectorX = res.getLong(0)
      val sectorY = res.getLong(1)
      sectorsToReset.add(SectorCoord(x = sectorX, y = sectorY))
    }
    for (coord in sectorsToReset) {
      resetSector(coord)
    }

    return version
  }
}
