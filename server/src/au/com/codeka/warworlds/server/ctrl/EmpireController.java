package au.com.codeka.warworlds.server.ctrl;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.DateTime;

import com.google.common.collect.Iterables;

import javax.annotation.Nullable;

import au.com.codeka.common.Log;
import au.com.codeka.common.model.BaseColony;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.Configuration;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStateTranslater;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.AllianceRequest;
import au.com.codeka.warworlds.server.model.Colony;
import au.com.codeka.warworlds.server.model.Empire;
import au.com.codeka.warworlds.server.model.EmpireBattleRank;
import au.com.codeka.warworlds.server.model.Planet;
import au.com.codeka.warworlds.server.model.Star;
import au.com.codeka.warworlds.server.utils.NameValidator;

public class EmpireController {
  private static final Log log = new Log("EmpireController");
  private DataBase db;

  public static final double STARTING_CASH_BONUS = 250000;
  public static final double RESET_CASH_BONUS = 50000;

  /** The order we want the empires returned in. */
  public enum Order {
    /** Unspecified order, it can just be the default. */
    UNSPECIFIED,

    /** Return oldest empires first. */
    OLDEST_FIRST,

    /** Return newest empires first. */
    NEWEST_FIRST,

    /** Order by rank, 1st place 1st. */
    RANK,
  }

  public EmpireController() {
    db = new DataBase();
  }

  public EmpireController(Transaction trans) {
    db = new DataBase(trans);
  }

  @Nullable
  public Empire getEmpire(int id) throws RequestException {
    try {
      Collection<Empire> empires = db.getEmpires(new int[] { id });
      if (empires.isEmpty()) {
        return null;
      }
      return Iterables.getOnlyElement(empires);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Empire> getAllEmpires() throws RequestException {
    try {
      return db.getEmpires((int[]) null);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Empire> getEmpires(int[] ids) throws RequestException {
    try {
      return db.getEmpires(ids);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Messages.EmpireLoginInfo> getRecentLogins(int empireID) throws RequestException {
    try {
      return db.getRecentLogins(empireID, 25);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Messages.EmpireLoginInfo> getAllRecentLogins() throws RequestException {
    try {
      return db.getRecentLogins(0, 100);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public Map<Integer, Double> getTaxCollectedPerHour(Collection<Integer> empireIDs)
      throws RequestException {
    try {
      return db.getTaxCollectedPerHour(empireIDs);
    } catch (Exception e) {
      throw new RequestException(e);
    }

  }

  public Empire getEmpireByEmail(String email) throws RequestException {
    try {
      return db.getEmpireByEmail(email);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Empire> getEmpiresByName(String name, Order order, int limit) throws RequestException {
    try {
      return db.getEmpiresByName(name, order, limit);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public List<Empire> getEmpiresByRank(int minRank, int maxRank) throws RequestException {
    try {
      return db.getEmpiresByRank(minRank, maxRank);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public ArrayList<EmpireBattleRank> getEmpireBattleRanks(
      int numDays, int offset, int count) throws RequestException {

    try {
      return db.getEmpireBattleRanks(numDays, offset, count);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public boolean isDeviceAccessDenied(
      int empireId, String deviceModel, String deviceBuild) throws RequestException {
    try {
      return db.isDeviceAccessDenied(empireId, deviceModel, deviceBuild);
    } catch (SQLException e) {
      throw new RequestException(e);
    }
  }

  /** Marks an empire active, that was previously marked abandoned. */
  public void markActive(Empire empire) throws RequestException {
    try (SqlStmt stmt = db.prepare("UPDATE empires SET state = ? WHERE id = ? AND state = ?")) {
      stmt.setInt(1, Empire.State.ACTIVE.getValue());
      stmt.setInt(2, empire.getID());
      stmt.setInt(3, Empire.State.ABANDONED.getValue());
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }

    // TODO: remove the empire's stars from the "abandoned stars" list...
  }

  /** Returns true if the given empire has any colonies at all, false if they've been wiped out. */
  public boolean hasAnyColonies(int empireID) throws RequestException {
    try {
      return db.hasAnyColonies(empireID);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public ArrayList<Integer> getStarsForEmpire(int empireId, EmpireStarsFilter filter, String search)
      throws RequestException {
    try {
      return db.getStarsForEmpire(empireId, filter, search);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public void update(Empire empire) throws RequestException {
    String newName = NameValidator.validate(
        empire.getDisplayName(),
        Configuration.i.getLimits().maxEmpireNameLength());

    try (SqlStmt stmt = db.prepare("UPDATE empires SET name = ? WHERE id = ?")) {
      stmt.setString(1, newName);
      stmt.setInt(2, empire.getID());
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public void changeEmpireShield(int empireID, byte[] pngImage) throws RequestException {
    String sql = "INSERT INTO empire_shields (empire_id, create_date, rejected, image) VALUES (?, NOW(), 0, ?)";
    try (SqlStmt stmt = db.prepare(sql)) {
      stmt.setInt(1, empireID);
      stmt.setBytes(2, pngImage);
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public byte[] getEmpireShield(int empireID, Integer shieldID) throws RequestException {
    String sql = "SELECT image FROM empire_shields " + " WHERE empire_id = ? AND rejected = 0 ";
    if (shieldID != null) {
      sql += " AND id = ?";
    }
    sql += " ORDER BY create_date DESC LIMIT 1";
    try (SqlStmt stmt = db.prepare(sql)) {
      stmt.setInt(1, empireID);
      if (shieldID != null) {
        stmt.setInt(2, shieldID);
      }
      SqlResult res = stmt.select();
      if (res.next()) {
        return res.getBytes(1);
      }
    } catch (Exception e) {
      throw new RequestException(e);
    }

    return null;
  }

  /** Associates the given empire with the given user, overwriting the previous association. */
  public void associateEmpire(int empireId, String userEmail) throws RequestException {
    try {
      db.associateEmpire(empireId, userEmail);
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  public boolean withdrawCash(int empireId, float amount,
      Messages.CashAuditRecord.Builder audit_record_pb) throws RequestException {
    return adjustBalance(empireId, -amount, audit_record_pb);
  }

  public void depositCash(int empireId, float amount,
      Messages.CashAuditRecord.Builder audit_record_pb) throws RequestException {
    adjustBalance(empireId, amount, audit_record_pb);
  }

  public boolean adjustBalance(int empireId, float amount,
      Messages.CashAuditRecord.Builder audit_record_pb) throws RequestException {
    if (Float.isNaN(amount)) {
      throw new RequestException(500, "Amount is NaN!");
    }
    if (Float.isInfinite(amount)) {
      throw new RequestException(500, "Amount is Infinite!");
    }

    Transaction t = db.getTransaction();
    boolean existingTransaction = (t != null);
    if (t == null) {
      try {
        t = DB.beginTransaction();
      } catch (SQLException e) {
        throw new RequestException(e);
      }
    }

    try {
      SqlStmt stmt = t.prepare("SELECT cash FROM empires WHERE id = ?");
      stmt.setInt(1, empireId);
      double cashBefore = stmt.selectFirstValue(Double.class);
      if (amount < 0 && cashBefore <= Math.abs(amount)) {
        return false;
      }

      audit_record_pb.setBeforeCash((float) cashBefore);
      audit_record_pb.setAfterCash((float) (cashBefore + amount));
      audit_record_pb.setTime(DateTime.now().getMillis() / 1000);

      stmt = t.prepare("UPDATE empires SET cash = cash + ? WHERE id = ? RETURNING cash");
      stmt.setDouble(1, amount);
      stmt.setInt(2, empireId);
      SqlResult res = stmt.updateAndSelect();
      if (res.next()) {
        double totalCash = res.getDouble(1);

        // Send a notification that cash has been updated.
        new NotificationController().sendNotificationToOnlineEmpire(
            empireId, "cash", Double.toString(totalCash));
      }

      stmt = t.prepare("INSERT INTO empire_cash_audit (empire_id, cash_before, cash_after,"
          + " time, reason) VALUES (?, ?, ?, ?, ?)");
      stmt.setInt(1, empireId);
      stmt.setDouble(2, cashBefore);
      stmt.setDouble(3, cashBefore - amount);
      stmt.setDateTime(4, DateTime.now());
      stmt.setBytes(5, audit_record_pb.build().toByteArray());
      stmt.update();

      if (!existingTransaction) {
        t.commit();
      }


      return true;
    } catch (Exception e) {
      throw new RequestException(e);
    } finally {
      if (!existingTransaction) {
        try {
          t.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }
  }

  public void createEmpire(Empire empire) throws RequestException {
    if (empire.getDisplayName().trim().equals("")) {
      throw new RequestException(400, Messages.GenericError.ErrorCode.CannotCreateEmpireBlankName,
          "You must give your empire a name.");
    }

    String name = NameValidator.validate(
        empire.getDisplayName(),
        Configuration.i.getLimits().maxEmpireNameLength());

    NewEmpireStarFinder starFinder = new NewEmpireStarFinder();
    if (!starFinder.findStarForNewEmpire()) {
      throw new RequestException(500); // todo: expand universe
    }
    Star star = new StarController().getStar(starFinder.getStarID());
    empire.setHomeStar(star);

    // create the empire
    db.createEmpire(empire, name);

    // empty the star of it's current (native) inhabitants
    String sql = "DELETE FROM colonies WHERE star_id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, star.getID());
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
    sql = "DELETE FROM fleets WHERE star_id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, star.getID());
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
    sql = "UPDATE stars SET time_emptied = ? WHERE id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setDateTime(1, DateTime.now());
      stmt.setInt(2, star.getID());
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }

    // re-fetch the star with it's new details...
    star = new StarController().getStar(star.getID());
    Planet planet = (Planet) star.getPlanets()[starFinder.getPlanetIndex() - 1];

    // TODO: we could probably use the user's configured defaults for focus here too.
    new ColonyController(db.getTransaction()).colonize(empire, star, starFinder.getPlanetIndex(),
        planet.getPopulationCongeniality() * 0.8f, 0.25f, 0.25f, 0.25f, 0.25f);

    new FleetController(db.getTransaction()).createFleet(empire, star, "colonyship", 2.0f);
    new FleetController(db.getTransaction()).createFleet(empire, star, "scout", 10.0f);
    new FleetController(db.getTransaction()).createFleet(empire, star, "fighter", 50.0f);
    new FleetController(db.getTransaction()).createFleet(empire, star, "troopcarrier", 150.0f);

    // update the last simulation time for the star so that it doesn't simulate
    // until we
    // actually arrived...
    star.setLastSimulation(DateTime.now());
    new StarController().update(star);
  }

  /** Bans an empire. */
  public void banEmpire(int empireID) throws RequestException {
    String sql = "UPDATE empires SET state = 2 WHERE id = ?";
    try (SqlStmt stmt = db.prepare(sql)) {
      stmt.setInt(1, empireID);
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  /**
   * Resets the given empire. This is obviously fairly destructive, so be careful!
   *
   * @param empireID The ID of the empire to reset.
   * @param resetReason The string reason to give the empire when we reset them.
   * @param ignoreLimits If true, ignore the number of times the empire has been reset recently.
   */
  public void resetEmpire(
      int empireID,
      String resetReason,
      boolean ignoreLimits) throws RequestException {

    if (!ignoreLimits) {
      Configuration.ResetsConfig config = Configuration.i.getResets();

      String sql = "SELECT * FROM empire_resets WHERE empire_id = ? ORDER BY reset_date DESC";
      int numAllowed = config.getMaxResetsPerPeriod();
      DateTime minTime = DateTime.now().minusDays(config.getResetPeriodDays());
      try (SqlStmt stmt = DB.prepare(sql)) {
        stmt.setInt(1, empireID);
        SqlResult res = stmt.select();

        while (res.next() && numAllowed > 0) {
          DateTime dt = res.getDateTime("reset_date");
          if (dt.isBefore(minTime)) {
            break;
          }
          log.debug("decrementing numAllowed (currently: %d", numAllowed);
          numAllowed --;
        }

        if (numAllowed == 0) {

          throw new RequestException(
              400,
              Messages.GenericError.ErrorCode.CannotReset,
              "You cannot reset your empire now, too many recent resets. If you really need to reset, you can connect me on discord.");
        }
      } catch (Exception e) {
        throw new RequestException(e);
      }
    } else {
      log.info("Skipping reset limits checking.");
    }

    String[] sqls = { "DELETE FROM empire_presences WHERE empire_id = ?",
        "DELETE FROM build_requests WHERE empire_id = ?",
        "DELETE FROM buildings WHERE empire_id = ?",
        "DELETE FROM colonies WHERE empire_id = ?",
        "DELETE FROM fleets WHERE empire_id = ?",
        "DELETE FROM fleet_upgrades WHERE fleet_id IN (SELECT id FROM fleets WHERE empire_id = ?)",
        "DELETE FROM alliance_join_requests WHERE empire_id = ?",
        "DELETE FROM scout_reports WHERE empire_id = ?",
        "DELETE FROM situation_reports WHERE empire_id = ?", };

    try (Transaction t = DB.beginTransaction()) {
      for (String sql : sqls) {
        try (SqlStmt stmt = t.prepare(sql)) {
          stmt.setInt(1, empireID);
          stmt.update();
        }
      }

      String sql = "UPDATE empires SET alliance_id = NULL, cash = 2000, reset_reason = ? WHERE id = ?";
      try (SqlStmt stmt = t.prepare(sql)) {
        stmt.setString(1, resetReason);
        stmt.setInt(2, empireID);
        stmt.update();
      }

      sql = "INSERT INTO empire_resets (empire_id, reset_reason, reset_date) VALUES (?, ?, ?)";
      try (SqlStmt stmt = t.prepare(sql)) {
        stmt.setInt(1, empireID);
        stmt.setString(2, resetReason);
        stmt.setDateTime(3, DateTime.now());
        stmt.update();
      }

      t.commit();
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  /**
   * Gets the reason (if any) for the resetting of the given empire. Once we're
   * returned this, we'll reset the reason back to NULL.
   */
  public String getResetReason(int empireID) throws RequestException {
    String reason;

    // empty the star of it's current (native) inhabitants
    String sql = "SELECT reset_reason FROM empires WHERE id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, empireID);
      reason = stmt.selectFirstValue(String.class);
    } catch (Exception e) {
      throw new RequestException(e);
    }

    sql = "UPDATE empires SET reset_reason = NULL WHERE id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, empireID);
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }

    return reason;
  }

  /**
   * If your old home star has been destroyed, for example, this will find us a new one.
   */
  public void findNewHomeStar(int empireID, int excludingStarId) throws RequestException {
    ArrayList<Integer> starIds = new ArrayList<>();
    String sql = "SELECT DISTINCT star_id FROM colonies WHERE empire_id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, empireID);
      SqlResult res = stmt.select();
      while (res.next()) {
        int starId = res.getInt(1);
        if (starId != excludingStarId) {
          starIds.add(res.getInt(1));
        }
      }
    } catch (Exception e) {
      throw new RequestException(e);
    }

    // find the star with the biggest population, that'll be our new home.
    Star bestStar = null;
    float bestStarPopulation = 0;
    for (Star star : new StarController().getStars(starIds)) {
      if (bestStar == null) {
        bestStar = star;
        bestStarPopulation = getTotalPopulation(star, empireID);
      } else {
        float thisStarPopulation = getTotalPopulation(star, empireID);
        if (thisStarPopulation > bestStarPopulation) {
          bestStar = star;
          bestStarPopulation = thisStarPopulation;
        }
      }
    }

    if (bestStar != null) {
      setHomeStar(empireID, bestStar.getID());
    }
  }

  public void setHomeStar(int empireID, int starID) throws RequestException {
    String sql = "UPDATE empires SET home_star_id = ? WHERE id = ?";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.setInt(1, starID);
      stmt.setInt(2, empireID);
      stmt.update();
    } catch (Exception e) {
      throw new RequestException(e);
    }
  }

  private static float getTotalPopulation(Star star, int empireID) {
    float population = 0;
    for (BaseColony baseColony : star.getColonies()) {
      Colony colony = (Colony) baseColony;
      if (colony.getEmpireID() != null && colony.getEmpireID() == empireID) {
        population += colony.getPopulation();
      }
    }
    return population;
  }

  private static class DataBase extends BaseDataBase {
    public DataBase() {
      super();
    }

    public DataBase(Transaction trans) {
      super(trans);
    }

    public void createEmpire(Empire empire, String validatedName) throws RequestException {
      String sql;
      boolean isReset = false;
      if (empire.getKey() == null || empire.getID() == 0) {
        sql = "INSERT INTO empires (name, cash, home_star_id, user_email, signup_date) VALUES (?, ?, ?, ?, NOW())";
      } else {
        sql = "UPDATE empires SET name = ?, cash = ?, home_star_id = ?, user_email = ? WHERE id = ?";
        isReset = true;
      }
      try (SqlStmt stmt = prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
        stmt.setString(1, validatedName);
        if (isReset) {
          stmt.setDouble(2, RESET_CASH_BONUS);
        } else {
          stmt.setDouble(2, STARTING_CASH_BONUS);
        }
        stmt.setInt(3, ((Star) empire.getHomeStar()).getID());
        stmt.setString(4, empire.getEmailAddr());
        if (empire.getKey() != null && empire.getID() != 0) {
          stmt.setInt(5, empire.getID());
        }
        stmt.update();
        if (empire.getKey() == null || empire.getID() == 0) {
          empire.setID(stmt.getAutoGeneratedID());
        }
      } catch (SQLException e) {
        if (SqlStateTranslater.isConstraintViolation(e)) {
          // this can actually be one of two things, either the empire name is already taken, or the
          // user's email address is not actually unique. The former is far more likely than the
          // latter, though
          throw new RequestException(
              400,
              Messages.GenericError.ErrorCode.EmpireNameExists,
              String
                  .format(
                      "The empire name you've chosen, '%s' already exists. Please choose a different name.",
                      validatedName));
        } else {
          throw new RequestException(e);
        }
      } catch (Exception e) {
        throw new RequestException(e);
      }
    }

    public List<Empire> getEmpires(@Nullable int[] ids) throws Exception {
      if (ids != null && ids.length == 0) {
        return new ArrayList<>();
      }

      String sql;
      if (ids == null) {
        sql = getSelectEmpire("1 = 1", true, Order.UNSPECIFIED, null);
      } else {
        sql = getSelectEmpire("empires.id IN " + buildInClause(ids), true, Order.UNSPECIFIED, null);
      }

      try (SqlStmt stmt = prepare(sql)) {
        SqlResult res = stmt.select();

        HashMap<Integer, Empire> empires = new HashMap<>();
        while (res.next()) {
          Empire empire = new Empire(res);
          empires.put(empire.getID(), empire);
        }

        populateEmpires(empires);
        return sortEmpires(empires.values(), Order.UNSPECIFIED);
      }
    }

    public Collection<Empire> getEmpires(Collection<Integer> ids) throws Exception {
      if (ids.size() == 0) {
        return new ArrayList<>();
      }

      int[] arrayIds = new int[ids.size()];
      int i = 0;
      for (Integer id : ids) {
        arrayIds[i] = id;
        i++;
      }

      return getEmpires(arrayIds);
    }

    public List<Messages.EmpireLoginInfo> getRecentLogins(int empireID, int limit) throws Exception {
      String sql =
          "SELECT empire_id, date, device_model, device_manufacturer, device_build, " +
                 "device_version, accessibility_service_infos, version, client_id, " +
                 "safetynet_attestation_statement, success, failure_reason, ip_address " +
          "FROM empire_logins " + (empireID == 0 ? "" : "WHERE empire_id = ? ") +
          "ORDER BY date DESC LIMIT ?";
      try (SqlStmt stmt = prepare(sql)) {
        if (empireID == 0) {
          stmt.setInt(1, limit);
        } else {
          stmt.setInt(1, empireID);
          stmt.setInt(2, limit);
        }
        SqlResult res = stmt.select();

        ArrayList<Messages.EmpireLoginInfo> empireLoginInfos = new ArrayList<>();
        while (res.next()) {
          Messages.EmpireLoginInfo.Builder empireLoginInfoBuilder =
              Messages.EmpireLoginInfo.newBuilder()
                  .setEmpireId(res.getInt(1))
                  .setDate(res.getDateTime(2).getMillis())
                  .setDeviceModel(res.getString(3))
                  .setDeviceManufacturer(res.getString(4))
                  .setDeviceBuild(res.getString(5))
                  .setDeviceVersion(res.getString(6));
          if (res.getBytes(7) != null) {
            empireLoginInfoBuilder.setAccessibilitySettings(
                Messages.AccessibilitySettingsInfo.parseFrom(res.getBytes(7)));
          }
          if (res.getString(8) != null) {
            empireLoginInfoBuilder.setVersion(res.getString(8));
          }
          if (res.getString(9) != null) {
            empireLoginInfoBuilder.setClientId(res.getString(9));
          }
          if (res.getString(10) != null) {
            empireLoginInfoBuilder.setSafetynetAttestationStatement(res.getString(10));
          }
          if (res.getInt(11) != null) {
            empireLoginInfoBuilder.setSuccess(res.getInt(11) > 0);
          }
          if (res.getString(12) != null) {
            empireLoginInfoBuilder.setFailureReason(res.getString(12));
          }
          if (res.getString(13) != null) {
            empireLoginInfoBuilder.setIpAddress(res.getString(13));
          }
          empireLoginInfos.add(empireLoginInfoBuilder.build());
        }

        return empireLoginInfos;
      }
    }

    public Map<Integer, Double> getTaxCollectedPerHour(Collection<Integer> empireIDs)
        throws Exception {
      String sql = "SELECT empire_id, sum(tax_per_hour)" + " FROM empire_presences"
          + " WHERE empire_id IN " + buildInClause(empireIDs) + " GROUP BY empire_id";

      try (SqlStmt stmt = prepare(sql)) {
        SqlResult res = stmt.select();

        Map<Integer, Double> taxRates = new TreeMap<>();
        while (res.next()) {
          taxRates.put(res.getInt(1), res.getDouble(2));
        }
        return taxRates;
      }
    }

    public Empire getEmpireByEmail(String email) throws Exception {
      String sql = getSelectEmpire("user_email = ?", true, Order.UNSPECIFIED, null);
      try (SqlStmt stmt = prepare(sql)) {
        stmt.setString(1, email);
        SqlResult res = stmt.select();

        HashMap<Integer, Empire> empires = new HashMap<>();
        Integer empireID = null;
        while (res.next()) {
          Empire empire = new Empire(res);
          empireID = empire.getID();
          empires.put(empireID, empire);
        }
        if (empires.isEmpty()) {
          return null;
        }

        populateEmpires(empires);
        return empires.get(empireID);
      }
    }

    public List<Empire> getEmpiresByName(String name, Order order, int limit) throws Exception {
      String select = name.isEmpty()
          ? ""
          : "empires.name ~* ?";
      String sql = getSelectEmpire(select, false, order, limit);
      try (SqlStmt stmt = prepare(sql)) {
        if (!select.isEmpty()) {
          stmt.setString(1, name);
        }
        SqlResult res = stmt.select();

        HashMap<Integer, Empire> empires = new HashMap<>();
        while (res.next()) {
          Empire empire = new Empire(res);
          empires.put(empire.getID(), empire);
        }

        populateEmpires(empires);
        return sortEmpires(empires.values(), order);
      }
    }

    public List<Empire> getEmpiresByRank(int minRank, int maxRank) throws Exception {
      String sql = getSelectEmpire(
          "empires.id IN (SELECT empire_id FROM empire_ranks WHERE rank BETWEEN ? AND ?)",
          false, Order.RANK, null);
      try (SqlStmt stmt = prepare(sql)) {
        stmt.setInt(1, minRank);
        stmt.setInt(2, maxRank);
        SqlResult res = stmt.select();

        HashMap<Integer, Empire> empires = new HashMap<>();
        while (res.next()) {
          Empire empire = new Empire(res);
          empires.put(empire.getID(), empire);
        }

        populateEmpires(empires);
        return sortEmpires(empires.values(), Order.RANK);
      }
    }

    public ArrayList<EmpireBattleRank> getEmpireBattleRanks(
        int numDays, int offset, int count) throws Exception {
      DateTime dt = new DateTime().plusDays(-numDays);
      int day = dt.getYear() * 10000 + dt.getMonthOfYear() * 100 + dt.getDayOfMonth();

      String sql = "SELECT" +
          " empire_id, SUM(ships_destroyed) AS ships_destroyed," +
          " SUM(population_destroyed) AS population_destroyed," +
          " SUM(colonies_destroyed) AS colonies_destroyed " +
          "FROM empire_battle_ranks " +
          "WHERE day >= ? GROUP BY empire_id " +
          "ORDER BY colonies_destroyed DESC, ships_destroyed DESC";
      try (SqlStmt stmt = prepare(sql)) {
        stmt.setInt(1, day);
        SqlResult res = stmt.select();

        Set<Integer> empireIDs = new HashSet<>();
        ArrayList<EmpireBattleRank> result = new ArrayList<>();
        while (res.next()) {
          if (offset > 0) {
            offset--;
            continue;
          }

          EmpireBattleRank battleRank = new EmpireBattleRank(res);
          result.add(battleRank);
          empireIDs.add(battleRank.getEmpireID());
          if (result.size() == count) {
            break;
          }
        }

        Collection<Empire> empires = getEmpires(empireIDs);
        for (EmpireBattleRank battleRank : result) {
          battleRank.updateEmpire(empires);
        }

        return result;
      }
    }

    public void associateEmpire(int empireID, String userEmail) throws Exception {
      String sql = "UPDATE empires SET user_email = ? WHERE id = ?";
      try (SqlStmt stmt = DB.prepare(sql)) {
        stmt.setString(1, userEmail);
        stmt.setInt(2, empireID);
        stmt.update();
      }
    }

    private String getSelectEmpire(
        String whereClause, boolean includeBanned, Order order, Integer limit) {
      String sql = "SELECT empires.*, empire_ranks.*, alliances.id AS alliance_id,"
          + " alliances.name as alliance_name, alliances.description as alliance_description,"
          + " alliances.creator_empire_id, alliances.created_date, alliances.bank_balance,"
          + " alliances.image_updated_date, alliances.is_active,"
          + " alliances.total_stars AS alliance_total_stars, patreon.max_pledge,"
          + " (SELECT COUNT(*) FROM empires WHERE alliance_id = empires.alliance_id) AS num_empires,"
          + " (SELECT MAX(create_date) FROM empire_shields WHERE empire_shields.empire_id = empires.id AND rejected = 0) AS shield_last_update,"
          + " (SELECT COUNT(*) FROM alliance_requests WHERE alliance_id = alliances.id AND state = "
          + AllianceRequest.RequestState.PENDING.getNumber() + ") AS num_pending_requests"
          + " FROM empires"
          + " LEFT JOIN alliances ON empires.alliance_id = alliances.id"
          + " LEFT JOIN empire_ranks ON empires.id = empire_ranks.empire_id"
          + " LEFT JOIN patreon ON empires.id = patreon.empire_id"
          + " WHERE ";
      if (!includeBanned) {
        sql += "state != 2 AND ";
      }
      sql += whereClause.isEmpty() ? "1 = 1" : whereClause;
      switch(order) {
        case UNSPECIFIED:
          // Nothing
          break;
        case NEWEST_FIRST:
          sql += " ORDER BY signup_date DESC";
          break;
        case OLDEST_FIRST:
          sql += " ORDER BY signup_date ASC";
          break;
        case RANK:
          sql += " ORDER BY empire_ranks.rank ASC";
          break;
      }
      if (limit != null) {
        sql += " LIMIT " + limit;
      }
      return sql;
    }

    private void populateEmpires(Map<Integer, Empire> empires) throws Exception {
      HashSet<Integer> notOnlineEmpireIDs = new HashSet<>();
      DateTime now = DateTime.now();
      for (Empire empire : empires.values()) {
        if (empire.getHomeStarID() != 0) {
          empire.setHomeStar(new StarController().getStar(empire.getHomeStarID()));
        }
        if (new NotificationController().isEmpireOnline(empire.getID())) {
          empire.setLastSeen(now);
        } else {
          notOnlineEmpireIDs.add(empire.getID());
        }
      }

      if (!notOnlineEmpireIDs.isEmpty()) {
        String sql = "SELECT empire_id, MAX(date) FROM empire_logins WHERE empire_id IN "
            + buildInClause(notOnlineEmpireIDs) + " GROUP BY empire_id";
        try (SqlStmt stmt = prepare(sql)) {
          SqlResult result = stmt.select();
          while (result.next()) {
            int empireID = result.getInt(1);
            DateTime lastSeen = result.getDateTime(2);
            empires.get(empireID).setLastSeen(lastSeen);
          }
        }
      }
    }

    private List<Empire> sortEmpires(Collection<Empire> empires, Order order) {
      ArrayList<Empire> sorted = new ArrayList<>(empires);
      sorted.sort((lhs, rhs) -> {
        switch (order) {
          case UNSPECIFIED:
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
          case RANK:
            if (lhs.getRank() != null && rhs.getRank() != null) {
              return lhs.getRank().getRank() - rhs.getRank().getRank();
            }
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
          case NEWEST_FIRST:
            return rhs.getSignupDate().compareTo(lhs.getSignupDate());
          case OLDEST_FIRST:
            return lhs.getSignupDate().compareTo(rhs.getSignupDate());
          default:
            return lhs.getID() - rhs.getID();
        }
      });
      return sorted;
    }

    private boolean hasAnyColonies(int empireID) throws Exception {
      String sql = "SELECT 1 FROM colonies WHERE empire_id=? LIMIT 1";
      try (SqlStmt stmt = prepare(sql)) {
        stmt.setInt(1, empireID);
        SqlResult result = stmt.select();
        if (result.next()) {
          return true;
        }
        return false;
      }
    }

    private ArrayList<Integer> getStarsForEmpire(int empireId, EmpireStarsFilter filter,
        String search) throws Exception {
      String sql;
      int numEmpireIds = 1;
      switch (filter) {
      case Colonies:
        sql = "SELECT id FROM stars WHERE id IN ("
            + " SELECT star_id FROM colonies WHERE empire_id = ?" + ")";
        break;
      case Fleets:
        sql = "SELECT id FROM stars WHERE id IN ("
            + " SELECT star_id FROM fleets WHERE empire_id = ?" + ")";
        break;
      case Building:
        sql = "SELECT id FROM stars WHERE id IN ("
            + " SELECT star_id FROM build_requests WHERE empire_id = ?" + ")";
        break;
      case NotBuilding:
        sql = "SELECT id FROM stars WHERE id NOT IN ("
            + " SELECT star_id FROM build_requests WHERE empire_id = ?" + ") AND id IN ("
            + " SELECT star_id FROM colonies WHERE empire_id = ?" + ")";
        numEmpireIds = 2;
        break;
      default: // case Everything:
        sql = "SELECT id FROM stars" + " INNER JOIN ("
            + " SELECT star_id FROM fleets WHERE empire_id = ?"
            + " UNION SELECT star_id FROM colonies WHERE empire_id = ?"
            + ") AS ids ON ids.star_id = stars.id";
        numEmpireIds = 2;
        break;
      }
      if (search != null) {
        sql += " AND name ~* ?";
      }
      sql += " ORDER BY name";
      try (SqlStmt stmt = prepare(sql)) {
        for (int i = 0; i < numEmpireIds; i++) {
          stmt.setInt(i + 1, empireId);
        }
        if (search != null) {
          stmt.setString(numEmpireIds + 1, search);
        }
        SqlResult res = stmt.select();

        ArrayList<Integer> starIds = new ArrayList<>();
        while (res.next()) {
          starIds.add(res.getInt(1));
        }

        return starIds;
      }
    }

    boolean isDeviceAccessDenied(
        int empireId, String deviceModel, String deviceBuild) throws SQLException {
      String sql =
          "SELECT deny_access " +
          "FROM def.devices " +
          "INNER JOIN def.empires " +
          "   ON devices.user_email = empires.user_email " +
          "WHERE device_model = ? " +
          "  AND device_build = ? " +
          "  AND empires.id = ?";
      try (SqlStmt stmt = prepare(sql)) {
        stmt.setString(1, deviceModel);
        stmt.setString(2, deviceBuild);
        stmt.setInt(3, empireId);
        SqlResult result = stmt.select();
        while (result.next()) {
          if (result.getInt(1) != null && result.getInt(1) == 1) {
            return true;
          }
        }
      }

      return false;
    }
  }

  public enum EmpireStarsFilter {
    Everything, Colonies, Fleets, Building, NotBuilding
  }
}
