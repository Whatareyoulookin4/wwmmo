package au.com.codeka.warworlds.server.store

import au.com.codeka.warworlds.common.Log
import au.com.codeka.warworlds.common.proto.AdminUser
import au.com.codeka.warworlds.server.store.StoreException
import au.com.codeka.warworlds.server.store.base.BaseStore
import java.util.*

/**
 * A store for storing admin users in the backend.
 */
class AdminUsersStore internal constructor(fileName: String) : BaseStore(fileName) {
  private var count = -1

  /** Returns the total number of users in the admin users store.  */
  fun count(): Int {
    if (count == -1) {
      count = 0
      try {
        newReader().stmt("SELECT COUNT(*) FROM users")!!.query().use { res ->
          if (res.next()) {
            count = res.getInt(0)
          }
        }
      } catch (e: Exception) {
        log.error("Unexpected.", e)
      }
    }
    return count
  }

  /** Gets the [AdminUser] with the given identifier, or null if the user doesn't exist.  */
  operator fun get(email: String?): AdminUser? {
    try {
      newReader()
          .stmt("SELECT user FROM users WHERE email = ?")
          .param(0, email)
          .query().use { res ->
            if (res.next()) {
              return AdminUser.ADAPTER.decode(res.getBytes(0))
            }
          }
    } catch (e: Exception) {
      log.error("Unexpected.", e)
    }
    return null
  }

  fun search(): List<AdminUser> {
    try {
      newReader().stmt("SELECT user FROM users")!!.query().use { res ->
        val users = ArrayList<AdminUser>()
        while (res.next()) {
          users.add(AdminUser.ADAPTER.decode(res.getBytes(0)))
        }
        return users
      }
    } catch (e: Exception) {
      log.error("Unexpected.", e)
      return ArrayList()
    }
  }

  /** Saves the given [AdminUser], indexed by email address, to the store.  */
  fun put(email: String?, adminUser: AdminUser) {
    try {
      newWriter()
          .stmt("INSERT OR REPLACE INTO users (email, user) VALUES (?, ?)")
          .param(0, email)
          .param(1, adminUser.encode())
          .execute()
      count = -1 // Reset it so it gets recaculated.
    } catch (e: StoreException) {
      log.error("Unexpected.", e)
    }
  }

  /** Delete the admin user with the given email address.  */
  fun delete(email: String?) {
    try {
      newWriter()
          .stmt("DELETE FROM users WHERE email = ?")
          .param(0, email)
          .execute()
    } catch (e: StoreException) {
      log.error("Unexpected.", e)
    }
  }

  @Throws(StoreException::class)
  override fun onOpen(diskVersion: Int): Int {
    var diskVersion = diskVersion
    if (diskVersion == 0) {
      newWriter()
          .stmt("CREATE TABLE users (email STRING PRIMARY KEY, user BLOB)")
          .execute()
      diskVersion++
    }
    return diskVersion
  }

  companion object {
    private val log = Log("AdminUsersStore")
  }
}