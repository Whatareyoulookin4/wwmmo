package au.com.codeka.warworlds.client.game.starfield

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import au.com.codeka.warworlds.client.App
import au.com.codeka.warworlds.client.R
import au.com.codeka.warworlds.client.ctrl.PlanetListSimple
import au.com.codeka.warworlds.client.game.fleets.FleetListSimple
import au.com.codeka.warworlds.client.game.fleets.FleetListSimple.FleetSelectedHandler
import au.com.codeka.warworlds.client.game.world.EmpireManager
import au.com.codeka.warworlds.client.game.world.ImageHelper
import au.com.codeka.warworlds.client.util.eventbus.EventHandler
import au.com.codeka.warworlds.common.proto.Empire
import au.com.codeka.warworlds.common.proto.Fleet
import au.com.codeka.warworlds.common.proto.Planet
import au.com.codeka.warworlds.common.proto.Star
import au.com.codeka.warworlds.common.proto.Star.CLASSIFICATION
import au.com.codeka.warworlds.common.sim.StarHelper
import com.google.common.base.Preconditions
import com.squareup.picasso.Picasso
import java.util.*

/**
 * The bottom pane when you have a star selected.
 */
@SuppressLint("ViewConstructor") // Must be constructed in code.
class StarSelectedBottomPane(context: Context, private var star: Star, callback: Callback)
  : ConstraintLayout(context, null) {

  interface Callback {
    fun onStarClicked(star: Star, planet: Planet?)
    fun onFleetClicked(star: Star, fleet: Fleet)
    fun onScoutReportClicked(star: Star)
  }

  private val planetList: PlanetListSimple
  private val fleetList: FleetListSimple
  private val starName: TextView
  private val starKind: TextView
  private val starIcon: ImageView
  private val renameButton: Button
  private val scoutReportButton: Button

  init {
    View.inflate(context, R.layout.starfield_bottom_pane_star, this)
    findViewById<View>(R.id.view_btn).setOnClickListener { callback.onStarClicked(this.star, null) }
    planetList = findViewById(R.id.planet_list)
    fleetList = findViewById(R.id.fleet_list)
    starName = findViewById(R.id.star_name)
    starKind = findViewById(R.id.star_kind)
    starIcon = findViewById(R.id.star_icon)
    renameButton = findViewById(R.id.rename_btn)
    scoutReportButton = findViewById(R.id.scout_report_btn)
    scoutReportButton.setOnClickListener { callback.onScoutReportClicked(this.star) }
    planetList.setPlanetSelectedHandler(object : PlanetListSimple.PlanetSelectedHandler {
      override fun onPlanetSelected(planet: Planet?) {
        callback.onStarClicked(star, planet)
      }
    })
    fleetList.setFleetSelectedHandler(object : FleetSelectedHandler {
      override fun onFleetSelected(fleet: Fleet?) {
        callback.onFleetClicked(star, fleet!!)
      }
    })
    if (!isInEditMode) {
      refresh()
    }
  }

  public override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (isInEditMode) {
      return
    }
    App.eventBus.register(eventListener)
  }

  public override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    if (isInEditMode) {
      return
    }
    App.eventBus.unregister(eventListener)
  }

  private fun refresh() {
    if (star.classification == CLASSIFICATION.WORMHOLE) {
      planetList.visibility = View.GONE
      findViewById<View>(R.id.wormhole_details).visibility = View.VISIBLE
      //      refreshWormholeDetails();
    } else {
      findViewById<View>(R.id.wormhole_details).visibility = View.GONE
      planetList.visibility = View.VISIBLE
      planetList.setStar(star)
    }
    fleetList.setStar(star)
    val myEmpire = Preconditions.checkNotNull(EmpireManager.getMyEmpire())
    var numMyEmpire = 0
    var numOtherEmpire = 0
    for (planet in star.planets) {
      val colony = planet.colony ?: continue
      if (colony.empire_id == 0L) {
        continue
      }
      if (colony.empire_id == myEmpire.id) {
        numMyEmpire++
      } else {
        numOtherEmpire++
      }
    }
    if (numMyEmpire > numOtherEmpire) {
      renameButton.visibility = View.VISIBLE
    } else {
      renameButton.visibility = View.GONE
    }
    scoutReportButton.isEnabled = star.scout_reports.isNotEmpty()
    starName.text = star.name
    starKind.text = String.format(Locale.ENGLISH, "%s %s", star.classification,
        StarHelper.getCoordinateString(star))
    Picasso.get()
        .load(ImageHelper.getStarImageUrl(context, star, 40, 40))
        .into(starIcon)
  }

  private val eventListener: Any = object : Any() {
    @EventHandler
    fun onStarUpdated(s: Star) {
      if (s.id == star.id) {
        star = s
        refresh()
      }
    }

    @EventHandler
    fun onEmpireUpdated(@Suppress("unused_parameter") empire: Empire?) {
      refresh()
    }
  }
}