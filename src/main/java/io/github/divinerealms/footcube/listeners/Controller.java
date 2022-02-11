package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.utils.Manager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class Controller implements Listener {
  public final ArrayList<Player> immune;
  public final HashMap<Player, BukkitTask> immuneMap;
  private final Manager manager;
  private final HashMap<UUID, Vector> velocities;
  private final HashMap<UUID, Long> kicked;
  private final HashMap<UUID, Double> charges;
  private final double regularKick;
  private final double chargedKick;
  private final double maxCharge;
  private final double kickPower;
  private final boolean ballEffect;
  private final boolean debugEnabled;
  private final Sound soundControl;
  private final Sound soundKick;
  private final EntityEffect entityEffect;
  private final PotionEffect potionEffect;
  public HashSet<Slime> cubes;
  private double finalSpeed = 0.0;
  private double finalLocation = 0.0;

  public Controller(Manager manager) {
    this.manager = manager;
    this.cubes = new HashSet<>();
    this.velocities = new HashMap<>();
    this.kicked = new HashMap<>();
    this.charges = new HashMap<>();
    this.immune = new ArrayList<>();
    this.immuneMap = new HashMap<>();
    this.regularKick = this.manager.getPlugin().getConfig().getDouble("Cube.Power_Limit.Regular_Kick");
    this.chargedKick = this.manager.getPlugin().getConfig().getDouble("Cube.Power_Limit.Charged_Kick");
    this.maxCharge = this.manager.getPlugin().getConfig().getDouble("Cube.Power_Limit.Total_Kick_Power");
    this.kickPower = this.manager.getPlugin().getConfig().getDouble("Cube.Kick_Power");
    this.ballEffect = this.manager.getPlugin().getConfig().getBoolean("Cube.Effect.Enabled");
    this.soundControl = Sound.valueOf(this.manager.getPlugin().getConfig().getString("Cube.Sounds.Move"));
    this.soundKick = Sound.valueOf(this.manager.getPlugin().getConfig().getString("Cube.Sounds.Kick"));
    this.entityEffect = EntityEffect.valueOf(this.manager.getPlugin().getConfig().getString("Cube.Effect.Effect"));
    this.debugEnabled = this.manager.getPlugin().getConfig().getBoolean("Debug");
    this.potionEffect = new PotionEffect(PotionEffectType.JUMP, 10, -3, true);
    this.removeCubes();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    this.charges.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onUnloadChunk(ChunkUnloadEvent event) {
    Entity[] entities;
    for (int length = (entities = event.getChunk().getEntities()).length, i = 0; i < length; ++i) {
      Entity entity = entities[i];
      if (!(entity instanceof Slime)) return;
      if (!this.cubes.contains(entity)) return;

      this.cubes.remove((Slime) entity);
      entity.remove();
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Slime)) return;
    if (!this.cubes.contains((Slime) event.getEntity())) return;
    if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

    event.setCancelled(true);
  }

  @EventHandler
  public void onRightClick(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof Slime)) return;
    if (!this.cubes.contains((Slime) event.getRightClicked())) return;
    if (this.kicked.containsKey(event.getPlayer().getUniqueId())) return;
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;

    Slime cube = (Slime) event.getRightClicked();
    cube.setVelocity(cube.getVelocity().add(new Vector(0.0D, 0.7D, 0.0D)));
    cube.getWorld().playSound(cube.getLocation(), this.soundControl, 0.75F, 1.0F);
    this.kicked.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    //TODO this.organization.ballTouch(event.getPlayer());
  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent event) {
    Player player = event.getPlayer();
    if (event.isSneaking()) this.charges.put(player.getUniqueId(), 0.0D);
    else {
      player.setExp(0.0F);
      this.charges.remove(player.getUniqueId());
    }
  }

  private double format(Double value) {
    double formattedValue = (int) ((value * 100) + 0.5);
    return formattedValue / 100;
  }

  private double getSpeed(Player player) {
    Bukkit.getScheduler().runTaskAsynchronously(this.manager.getPlugin(), () -> this.finalSpeed = player.getVelocity().setY(player.getVelocity().getY() / 2.0).length());
    return this.finalSpeed;
  }

  public double getDistance(Location start, Location end) {
    Bukkit.getScheduler().runTaskAsynchronously(this.manager.getPlugin(), () -> {
      start.setY(-0.25);
      start.subtract(end).setY(-1.25);
      if (start.getY() < 0.0) start.setY(0.0);
      this.finalLocation = start.length();
    });
    return this.finalLocation;
  }

  @EventHandler
  public void onSlamSlime(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Slime)) return;
    if (!this.cubes.contains((Slime) event.getEntity())) return;
    if (!(event.getDamager() instanceof Player)) return;
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

    Slime cube = (Slime) event.getEntity();
    Player player = (Player) event.getDamager();

    if (player.getGameMode() != GameMode.SURVIVAL) {
      if (!player.hasPermission("nfootcube.clearcube")) event.setCancelled(true);
      else {
        // TODO: disable removal in matches
        this.cubes.remove(cube);
        this.manager.getMessage().send(player, "CUBE_CLEARED");
      }
      return;
    }

    double charge = 1.0D;
    double power = 0.4D;
    double total;

    Vector kick;
    String message = colorize("&6[&eDebug&6] %server_tps_1_colored% TPS &7: &b" + player.getName());

    if (this.charges.containsKey(player.getUniqueId())) charge += this.charges.get(player.getUniqueId()) * 7.0D;
    power += this.getSpeed(player) * 2.0D + 0.4D;

    if (this.manager.getCooldown().isCubeKickCooldownEnabled()) {
      long timeLeft = this.manager.getCooldown().getTimeleftMillis(player.getUniqueId(), this.manager.getCooldown().getCubeKickCooldown());
      if (timeLeft > 0.0) {
        message = message + colorize("&c is on cooldown (" + (double) timeLeft / 1000.0 + "s)");
        message = PlaceholderAPI.setPlaceholders(player, message);
        if (this.debugEnabled) for (Player onlinePlayers : this.manager.getPlugin().getServer().getOnlinePlayers())
          if (onlinePlayers.hasPermission("nfootcube.debug")) onlinePlayers.sendMessage(message);
        event.setCancelled(true);
        return;
      }
    }

    if (charge > 1.0D && charge <= 4.0) {
      if (power >= this.chargedKick && charge > 2.0D) {
        total = this.chargedKick * charge * this.kickPower;
        kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
        message = message + colorize("&r with &a" + format(charge) + "&f charge and &c" + format(power) + " &fpower &7[" + format(total) + " total kp]");
      } else {
        total = power * charge * this.kickPower;
        kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
        message = message + colorize("&r with &a" + format(charge) + "&f charge and &a" + format(power) + " &fpower &7[" + format(total) + " total kp]");
      }
    } else if (charge > this.maxCharge) {
      if (power >= this.chargedKick) {
        total = this.chargedKick * this.maxCharge * this.kickPower;
        kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
        message = message + colorize("&r with &c" + format(charge) + "&f charge and &c" + format(power) + " &fpower &7[" + format(total) + " total kp]");
      } else {
        total = power * this.maxCharge * this.kickPower;
        kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
        message = message + colorize("&r with &c" + format(charge) + "&f charge and &a" + format(power) + " &fpower &7[" + format(total) + " total kp]");
      }
    } else if (power >= this.regularKick && charge <= 2.0D) {
      total = this.regularKick * charge * this.kickPower;
      kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
      message = message + colorize("&r with &c" + format(power) + " &fpower &7[" + format(total) + " total kp]");
    } else {
      total = power * charge * this.kickPower;
      kick = player.getLocation().getDirection().normalize().multiply(total).setY(0.3D);
      message = message + colorize("&r with &a" + format(power) + " &fpower");
    }

    //this.organization.ballTouch(player);

    cube.setVelocity(cube.getVelocity().add(kick));
    cube.getWorld().playSound(cube.getLocation(), this.soundKick, 0.75F, 1.0F);

    this.manager.getCooldown().setCooldown(player.getUniqueId(), System.currentTimeMillis());
    message = PlaceholderAPI.setPlaceholders(player, message);
    if (this.debugEnabled) for (Player onlinePlayers : this.manager.getPlugin().getServer().getOnlinePlayers())
      if (onlinePlayers.hasPermission("nfootcube.debug")) onlinePlayers.sendMessage(message);

    event.setCancelled(true);
  }

  public void spawnCube(Location location) {
    Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
    cube.setRemoveWhenFarAway(false);
    cube.setSize(1);
    this.cubes.add(cube);
  }

  public void update() {
    if (this.kicked.size() > 0)
      this.kicked.entrySet().removeIf(entry -> System.currentTimeMillis() > this.kicked.get(entry.getKey()) + 1000L);

    Collection<? extends Player> onlinePlayers = this.manager.getPlugin().getServer().getOnlinePlayers();

    for (UUID uuid : this.charges.keySet()) {
      Player player = this.manager.getPlugin().getServer().getPlayer(uuid);
      double charge = this.charges.get(uuid);
      final double nextCharge = 1.0D - (1.0D - charge) * 0.95D;
      this.charges.put(uuid, nextCharge);
      player.setExp((float) nextCharge);
    }

    Iterator<Slime> cubesIterator = this.cubes.iterator();

    while (cubesIterator.hasNext()) {
      Slime cube = cubesIterator.next();
      UUID id = cube.getUniqueId();
      Vector oldV = cube.getVelocity();
      if (this.velocities.containsKey(id)) oldV = this.velocities.get(id);
      if (cube.isDead()) cubesIterator.remove();

      boolean sound = false;
      boolean kicked = false;
      Vector newV = cube.getVelocity();

      for (Player player : onlinePlayers) {
        if (!this.immune.contains(player) && player.getGameMode() == GameMode.SURVIVAL) {
          double delta = this.getDistance(cube.getLocation(), player.getLocation());
          if (delta < 1.2D) {
            if (delta < 0.8D && newV.length() > 0.5D) newV.multiply(0.5D / newV.length());
            double speed = this.getSpeed(player);
            double power = speed / 3.0D + oldV.length() / 6.0D;
            newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
            //this.organization.ballTouch(p3);
            kicked = true;
            if (power > 0.15D) sound = true;
          }
        }
      }

      if (newV.getX() == 0.0D) {
        newV.setX(-oldV.getX() * 0.8D);
        if (Math.abs(oldV.getX()) > 0.3D) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < 0.1D) newV.setX(oldV.getX() * 0.98D);

      if (newV.getZ() == 0.0D) {
        newV.setZ(-oldV.getZ() * 0.8D);
        if (Math.abs(oldV.getZ()) > 0.3D) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < 0.1) newV.setZ(oldV.getZ() * 0.98D);

      if (newV.getY() < 0.0 && oldV.getY() < 0.0D && oldV.getY() < newV.getY() - 0.05D) {
        newV.setY(-oldV.getY() * 0.8D);
        if (Math.abs(oldV.getY()) > 0.3D) sound = true;
      }

      if (sound) cube.getWorld().playSound(cube.getLocation(), this.soundControl, 0.5F, 1.0F);

      for (Player player : onlinePlayers) {
        double delta2 = this.getDistance(cube.getLocation(), player.getLocation());
        if (delta2 < newV.length() * 1.3D) {
          Vector loc = cube.getLocation().toVector();
          Vector nextLoc = new Vector(loc.getX(), loc.getY(), loc.getZ()).add(newV);
          boolean rightDirection = true;
          Vector pDir = new Vector(player.getLocation().getX() - loc.getX(), 0.0D, player.getLocation().getZ() - loc.getZ());

          Vector cDir = new Vector(newV.getX(), 0.0D, newV.getZ()).normalize();
          int px = 1;
          if (pDir.getX() < 0.0D) px = -1;
          int pz = 1;
          if (pDir.getZ() < 0.0D) pz = -1;
          int cx = 1;
          if (cDir.getX() < 0.0D) cx = -1;
          int cz = 1;
          if (cDir.getZ() < 0.0D) cz = -1;
          if ((px != cx && pz != cz) || ((px != cx || pz != cz) && (cx * pDir.getX() <= cx * cz * px * cDir.getZ() || cz * pDir.getZ() <= cz * cx * pz * cDir.getX())))
            rightDirection = false;
          if (rightDirection && loc.getY() < player.getLocation().getY() + 2.0D && loc.getY() > player.getLocation().getY() - 1.0D && nextLoc.getY() < player.getLocation().getY() + 2.0D && nextLoc.getY() > player.getLocation().getY() - 1.0D) {
            double a = newV.getZ() / newV.getX();
            double b = loc.getZ() - a * loc.getX();
            double c = player.getLocation().getX();
            double d = player.getLocation().getZ();
            double D = Math.abs(a * c - d + b) / Math.sqrt(a * a + 1.0D);
            if (D < 0.8D) newV.multiply(delta2 / newV.length());
          }
        }
      }

      cube.setVelocity(newV);
      cube.addPotionEffect(this.potionEffect);

      if (this.ballEffect) cube.playEffect(this.entityEffect);

      this.velocities.put(id, newV);
    }
  }

  public void removeCubes() {
    List<Entity> entities = this.manager.getPlugin().getServer().getWorlds().get(0).getEntities();

    for (Entity entity : entities)
      if (entity instanceof Slime) entity.remove();
  }

  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}