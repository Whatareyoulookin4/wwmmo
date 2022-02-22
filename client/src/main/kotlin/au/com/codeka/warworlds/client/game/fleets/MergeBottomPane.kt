package au.com.codeka.warworlds.client.game.fleets

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import au.com.codeka.warworlds.client.R
import au.com.codeka.warworlds.client.game.world.StarManager
import au.com.codeka.warworlds.common.proto.Fleet
import au.com.codeka.warworlds.common.proto.Star
import au.com.codeka.warworlds.common.proto.StarModification
import com.google.common.collect.Iterables
import com.google.common.collect.Lists

/**
 * Bottom pane of the fleets view that contains the "merge" function.
 */
@SuppressLint("ViewConstructor") // Must be constructed in code.
class MergeBottomPane(
    context: Context?,
    private val adapter: FleetExpandableStarListAdapter,
    private val callback: () -> Unit) : RelativeLayout(context, null) {

  /** The fleet we're splitting, may be null if [.setFleet] hasn't been called.  */
  var fleet: Fleet? = null

  /** The star of the fleet we're splitting.  */
  var star: Star? = null
  public override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    adapter.setMultiSelect(true)
    adapter.disableFleets { testFleet: Fleet? -> fleet != null && testFleet!!.design_type != fleet!!.design_type }
  }

  public override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    adapter.setMultiSelect(false)
    adapter.enableAllFleets()
  }

  /** Set the fleet we're merging to the one with the given ID on the given star.  */
  fun setFleet(star: Star, fleetId: Long) {
    for (fleet in star.fleets) {
      if (fleet.id == fleetId) {
        setFleet(star, fleet)
      }
    }
  }

  private fun setFleet(star: Star, fleet: Fleet) {
    this.star = star
    this.fleet = fleet

//    update(leftCount, rightCount);
  }

  private fun onMergeClick() {
    if (star == null || fleet == null) {
      return
    }
    if (adapter.selectedFleetId == null) {
      return
    }
    val selectedFleetId: Long = adapter.selectedFleetId!!
    val additionalFleetIds: List<Long> = Lists.newArrayList(
        Iterables.filter(adapter.getSelectedFleetIds()) { fleetId -> selectedFleetId != fleetId })
    if (additionalFleetIds.isNotEmpty()) {
      StarManager.updateStar(star!!, StarModification(
          type = StarModification.Type.MERGE_FLEET,
          fleet_id = fleet!!.id,
          additional_fleet_ids = additionalFleetIds))
    }
    callback()
  }

  private fun onCancelClick() {
    callback()
  }

  init {
    View.inflate(context, R.layout.ctrl_fleet_merge_bottom_pane, this)
    findViewById<View>(R.id.merge_btn).setOnClickListener { onMergeClick() }
    findViewById<View>(R.id.cancel_btn).setOnClickListener { onCancelClick() }
  }
}