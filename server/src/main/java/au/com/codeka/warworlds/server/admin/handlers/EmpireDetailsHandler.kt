package au.com.codeka.warworlds.server.admin.handlers

import au.com.codeka.warworlds.common.proto.Empire
import au.com.codeka.warworlds.common.proto.Notification
import au.com.codeka.warworlds.common.proto.Star
import au.com.codeka.warworlds.server.handlers.RequestException
import au.com.codeka.warworlds.server.proto.PatreonInfo
import au.com.codeka.warworlds.server.store.DataStore
import au.com.codeka.warworlds.server.world.NotificationManager
import au.com.codeka.warworlds.server.world.StarManager
import au.com.codeka.warworlds.server.world.WatchableObject
import com.google.common.collect.ImmutableMap
import java.util.*

/**
 * Handler for /admin/empires/xxx which shows details about the empire with id xxx.
 */
class EmpireDetailsHandler : AdminHandler() {
  @Throws(RequestException::class)
  public override fun get() {
    val id = getUrlParameter("id")!!.toLong()
    val empire: Empire = DataStore.i.empires()[id] ?: throw RequestException(404)
    complete(empire, HashMap())
  }

  @Throws(RequestException::class)
  public override fun post() {
    val id = getUrlParameter("id")!!.toLong()
    val empire: Empire = DataStore.i.empires()[id] ?: throw RequestException(404)
    val msg = request.getParameter("msg")
    if (msg.isEmpty()) {
      val data = HashMap<String, Any>()
      data["error"] = "You need to specify a message."
      complete(empire, data)
      return
    }

    // TODO: send it
    NotificationManager.i.sendNotification(empire, Notification.Builder()
        .debug_message(msg)
        .build())
    redirect("/admin/empires/$id")
  }

  private fun complete(empire: Empire, mapBuilder: HashMap<String, Any>) {
    mapBuilder["empire"] = empire
    val stars = ArrayList<Star?>()
    for (starId in DataStore.i.stars().getStarsForEmpire(empire.id)) {
      val star: WatchableObject<Star>? = StarManager.i.getStar(starId)
      if (star != null) {
        stars.add(star.get())
      }
    }
    mapBuilder["stars"] = stars
    mapBuilder["devices"] = DataStore.i.empires().getDevicesForEmpire(empire.id)
    val patreonInfo: PatreonInfo? = DataStore.i.empires().getPatreonInfo(empire.id)
    if (patreonInfo != null) {
      mapBuilder["patreon"] = patreonInfo
    }
    render("empires/details.html", mapBuilder)
  }
}