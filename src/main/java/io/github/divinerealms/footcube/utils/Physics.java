package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Messages;
import io.github.divinerealms.footcube.configs.Settings;
import io.github.divinerealms.footcube.managers.UtilManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class Physics {
  @Getter private final Plugin plugin;
  @Getter private final Server server;
  @Getter private final Messages messages;
  @Getter private final Settings settings;
  @Getter private final PotionEffect potionEffect = new PotionEffect(PotionEffectType.JUMP, 10, -3, true);
  @Getter private final HashMap<UUID, Vector> velocities = new HashMap<>();
  @Getter private final HashMap<UUID, Long> kicked = new HashMap<>();
  @Getter private final HashMap<UUID, Double> charges = new HashMap<>();
  @Getter private final Map<UUID, Deque<Location>> lastLocations = new HashMap<>();
  @Getter private final HashSet<Slime> cubes = new HashSet<>();
  @Getter private double regularKickLimit, chargedKickLimit, kickPower;
  @Getter @Setter private double power = 0.4, charge = 0, total;
  @Getter private boolean isSoundEnabled, isCubeEffectEnabled, isDebugEnabled;
  @Getter private Sound soundMove, soundKick;
  @Getter private EntityEffect cubeEffect;

  public Physics(final Plugin plugin, final UtilManager utilManager) {
    this.plugin = plugin;
    this.server = plugin.getServer();
    this.messages = utilManager.getMessages();
    this.settings = utilManager.getSettings();
  }

  public void reload() {
    this.kickPower = getSettings().getDouble("cube.kick-power");
    this.chargedKickLimit = getSettings().getDouble("cube.power-limit.charged-kick");
    this.regularKickLimit = getSettings().getDouble("cube.power-limit.regular-kick");
    this.isSoundEnabled = getSettings().getBoolean("cube.sounds.enabled");
    this.isCubeEffectEnabled = getSettings().getBoolean("cube.effect.enabled");
    this.soundMove = getSettings().getSound("cube.sounds.move");
    this.soundKick = getSettings().getSound("cube.sounds.kick");
    this.cubeEffect = getSettings().getEntityEffect("cube.effect.type");
    this.isDebugEnabled = getSettings().getBoolean("debug.ball-hits");
    removeCubes();
  }

  public double format(final Double value) {
    return (double) Math.round(value * 100) / 100;
  }

  public Vector getLastMoveVector(final UUID playerID) {
    final Vector defaultLocation = new Vector(0, 0, 0);
    final Deque<Location> locations = getLastLocations().get(playerID);
    if (locations == null) return defaultLocation;
    final Location last = locations.poll();
    final Location secondLast = locations.poll();
    if (last == null | secondLast == null) return defaultLocation;
    final Vector vector = last.toVector().subtract(secondLast.toVector());
    locations.add(last);
    locations.add(secondLast);
    return vector;
  }

  public double getTotalKickPower(final UUID playerID) {
    setPower(0.4 + getLastMoveVector(playerID).length() * 2 + 0.4);
    setCharge(getCharges().containsKey(playerID) ? getCharges().get(playerID) * 8 : 0);
    setTotal(((getCharge() != 0) ? (getCharge() * Math.min(getPower(), getChargedKickLimit())) : Math.min(getPower(), getRegularKickLimit())) * getKickPower());
    return getTotal();
  }

  public void spawnCube(final Location location) {
    final Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
    cube.setRemoveWhenFarAway(false);
    cube.setSize(1);
    getCubes().add(cube);
  }

  public Location getDistance(final Location firstLocation, final Location secondLocation) {
    firstLocation.add(0, -1, 0);
    firstLocation.subtract(secondLocation).add(0, -1.5, 0);
    return firstLocation;
  }

  public void update() {
    if (getKicked().size() > 0)
      getKicked().entrySet().removeIf(entry -> System.currentTimeMillis() > getKicked().get(entry.getKey()) + 1000L);

    for (final UUID playerID : getCharges().keySet()) {
      final Player player = getServer().getPlayer(playerID);
      final double charge = getCharges().get(playerID);
      final double nextCharge = 1 - (1 - charge) * 0.925;
      getCharges().put(playerID, nextCharge);
      player.setExp((float) nextCharge);
    }

    final Iterator<Slime> cubesIterator = getCubes().iterator();

    while (cubesIterator.hasNext()) {
      final Slime cube = cubesIterator.next();
      final UUID cubeID = cube.getUniqueId();
      Vector oldV = cube.getVelocity();
      if (getVelocities().containsKey(cubeID)) oldV = getVelocities().get(cubeID);
      if (cube.isDead()) cubesIterator.remove();

      boolean sound = false;
      boolean kicked = false;
      Vector newV = cube.getVelocity();

      final Collection<? extends Player> onlinePlayers = getServer().getOnlinePlayers();

      for (final Player player : onlinePlayers) {
        if (player.getGameMode() == GameMode.SURVIVAL) {
//          double delta = cube.getLocation().distance(player.getLocation());
          final Location distance = getDistance(cube.getLocation(), player.getLocation());
          if (distance.getY() < 0.0) distance.add(0, 2.5, 0);
          final double delta = distance.length();
          if (delta < 1.2) {
            if (delta < 0.8 && newV.length() > 0.5) newV.multiply(0.5 / newV.length());
            double power = getLastMoveVector(player.getUniqueId()).length() / 3 + oldV.length() / 6;
            newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
            //TODO: this.organization.ballTouch(p3);
            kicked = true;
            if (power > 0.15) sound = true;
          }
        }
      }

      if (newV.getX() == 0) {
        newV.setX(-oldV.getX() * 0.8);
        if (Math.abs(oldV.getX()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < 0.1) newV.setX(oldV.getX() * 0.98);

      if (newV.getZ() == 0) {
        newV.setZ(-oldV.getZ() * 0.8D);
        if (Math.abs(oldV.getZ()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < 0.1) newV.setZ(oldV.getZ() * 0.98);

      if (newV.getY() < 0 && oldV.getY() < 0 && oldV.getY() < newV.getY() - 0.05) {
        newV.setY(-oldV.getY() * 0.8);
        if (Math.abs(oldV.getY()) > 0.3) sound = true;
      }

      if (sound) cube.getWorld().playSound(cube.getLocation(), getSoundMove(), 0.5F, 1F);

      for (final Player player : onlinePlayers) {
        if (player.getGameMode() == GameMode.SURVIVAL) {
          double delta2 = getDistance(cube.getLocation(), player.getLocation()).length();
          if (delta2 < newV.length() * 1.3) {
            final Vector loc = cube.getLocation().toVector();
            final Vector nextLoc = new Vector(loc.getX(), loc.getY(), loc.getZ()).add(newV);
            boolean rightDirection = true;
            final Vector pDir = new Vector(player.getLocation().getX() - loc.getX(), 0, player.getLocation().getZ() - loc.getZ());
            final Vector cDir = new Vector(newV.getX(), 0, newV.getZ()).normalize();
            int px = 1, pz = 1, cx = 1, cz = 1;
            if (pDir.getX() < 0) px = -1;
            if (pDir.getZ() < 0) pz = -1;
            if (cDir.getX() < 0) cx = -1;
            if (cDir.getZ() < 0) cz = -1;
            if ((px != cx && pz != cz) || ((px != cx || pz != cz) && (cx * pDir.getX() <= cx * cz * px * cDir.getZ() || cz * pDir.getZ() <= cz * cx * pz * cDir.getX())))
              rightDirection = false;
            if (rightDirection && loc.getY() < player.getLocation().getY() + 2 && loc.getY() > player.getLocation().getY() - 1 && nextLoc.getY() < player.getLocation().getY() + 2 && nextLoc.getY() > player.getLocation().getY() - 1) {
              double a = newV.getZ() / newV.getX();
              double b = loc.getZ() - a * loc.getX();
              double c = player.getLocation().getX();
              double d = player.getLocation().getZ();
              final double D = Math.abs(a * c - d + b) / Math.sqrt(a * a + 1);
              if (D < 0.8D) newV.multiply(delta2 / newV.length());
            }
          }
        }
      }

      cube.setVelocity(newV);
      cube.addPotionEffect(getPotionEffect());

      if (isCubeEffectEnabled()) cube.playEffect(getCubeEffect());

      getVelocities().put(cubeID, newV);
    }
  }

  public void removeCubes() {
    final List<Entity> entities = getServer().getWorlds().get(0).getEntities();
    for (final Entity entity : entities) if (entity instanceof Slime) entity.remove();
  }

  public void debug(final Player player) {
    if (isDebugEnabled()) getMessages().ballHitsDebug(player.getName(), format(getPower()), format(getCharge()), format(getKickPower()), format(getTotal()));
  }

  public void playSound(final Slime cube, final boolean isKick) {
    if (isSoundEnabled())
      cube.getWorld().playSound(cube.getLocation(), (isKick ? getSoundKick() : getSoundMove()), 0.75F, 1F);
  }
}