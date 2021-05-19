package me.bullet.ghasts;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;

@Mod(modid = "afkghast", name = "AfkGhastFarm", version = "0")
public class AfkGhasts {

    public static final Minecraft MC = Minecraft.getMinecraft();
    public static final Logger LOGGER = LogManager.getLogger("AfkGhasts");
    public static final String PREFIX = "\u00A7d" + "[" +  "\u00A7a" + "AfkGhasts" + "\u00A7d" + "]" + "\u00A7b ";

    private KeyBinding customBind;
    private KeyBinding auraBind;

    private boolean toggled = false;
    private boolean auraToggled = false;

    private BlockPos startingBlockPos;
    private final Set<EntityGhast> ghasts = new LinkedHashSet<>();

    public static final double RAD_TO_DEG = 180.0 / Math.PI;

    @Mod.EventHandler
    public void onInit(final FMLInitializationEvent event) {
        LOGGER.info("init");
        this.customBind = new KeyBinding("ToggleAfkWalking", -1, "AfkGhasts");
        this.auraBind = new KeyBinding("GhastAura", -1, "AfkGhasts");
        ClientRegistry.registerKeyBinding(this.customBind);
        ClientRegistry.registerKeyBinding(this.auraBind);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("didnt crash - stan loona");
    }

    @SubscribeEvent
    public void onKeyPressed(final InputEvent.KeyInputEvent event) {
        if (this.customBind.isPressed()) {
            this.toggled = !this.toggled;
            if (this.toggled) {
                this.startingBlockPos = MC.player.getPosition();
                MC.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(PREFIX + "Now Doing AFK ghast farming"));
            } else {
                MC.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(PREFIX + "No longer doing AFK farming"));
                BaritoneAPI
                        .getProvider()
                        .getPrimaryBaritone()
                        .getPathingBehavior()
                        .cancelEverything();
            }
        }
        if (this.auraBind.isPressed()) {
            this.auraToggled = !this.auraToggled;
            if (this.auraToggled) {
                this.startingBlockPos = MC.player.getPosition();
                MC.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(PREFIX + "Enabled Ghast KillAura"));
            } else {
                MC.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(PREFIX + "Disabled Ghast KillAura"));
            }
        }
    }

    @SubscribeEvent
    public void onUpdate(final LivingEvent.LivingUpdateEvent event) {
        if (event.getEntityLiving() == MC.player) {
            if (this.auraToggled) {
                for (final Entity entity : MC.world.loadedEntityList) {
                    if (entity instanceof EntityGhast) {
                        if (MC.player.getDistance(entity) < 6.0F) {
                            if (MC.player.inventory.getCurrentItem().getItem() instanceof ItemSword) {
                                if (!(MC.player.getCooledAttackStrength(0) >= 1.0F)) {
                                    return;
                                }
                                final Rotation rotation = this.calcRotationFromVec3d(MC.player.getPositionEyes(MC.getRenderPartialTicks()),
                                        entity.getPositionVector());
                                MC.player.rotationYaw = rotation.getYaw();
                                MC.player.rotationPitch = rotation.getPitch();
                                MC.playerController.attackEntity(MC.player, entity);
                                MC.player.swingArm(EnumHand.MAIN_HAND);
                            }
                        }
                    }
                }
            }
            if (!this.toggled) {
                return;
            }
            this.ghasts.clear();
            final boolean isBaritoneActive = BaritoneAPI
                    .getProvider()
                    .getPrimaryBaritone()
                    .getCustomGoalProcess()
                    .isActive();
            double maxDist = 512D;
            EntityGhast closestGhast = null;
            for (final Entity entity : MC.world.loadedEntityList) {
                if (entity instanceof EntityGhast) {
                    final EntityGhast ghast = (EntityGhast) entity;
                    final double distFromGhast = ghast.getDistance(MC.player);
                    this.ghasts.add(ghast);
                    if (distFromGhast <= maxDist) {
                        maxDist = distFromGhast;
                        closestGhast = ghast;
                    }
                    if (!isBaritoneActive && closestGhast != null) {
                        BaritoneAPI
                                .getProvider()
                                .getPrimaryBaritone()
                                .getCustomGoalProcess()
                                .setGoalAndPath(
                                        new GoalBlock(
                                                closestGhast.getPosition().getX(), closestGhast.getPosition().getY(), closestGhast.getPosition().getZ()
                                        )
                                );
                    }
                    if (ghast.isDead) {
                        BaritoneAPI
                                .getProvider()
                                .getPrimaryBaritone()
                                .getPathingBehavior()
                                .cancelEverything();
                    }
                }
            }
            if (this.ghasts.isEmpty() && MC.player.getPosition() != this.startingBlockPos) {
                if (!isBaritoneActive
                        && !MC.player.getEntityBoundingBox()
                        .intersects(new AxisAlignedBB(this.startingBlockPos))) {
                    BaritoneAPI
                            .getProvider()
                            .getPrimaryBaritone()
                            .getCustomGoalProcess()
                            .setGoalAndPath(
                                    new GoalBlock(
                                            this.startingBlockPos.getX(), this.startingBlockPos.getY(), this.startingBlockPos.getZ()
                                    )
                            );
                }
                if (MC.player.getPosition() == this.startingBlockPos) {
                    BaritoneAPI
                            .getProvider()
                            .getPrimaryBaritone()
                            .getPathingBehavior()
                            .cancelEverything();
                }
            }
        }
    }

    /**
     * Calculates the rotation from Vec<sub>dest</sub> to Vec<sub>orig</sub>
     *
     * @param orig The origin position
     * @param dest The destination position
     * @return The rotation from the origin to the destination
     */
    private Rotation calcRotationFromVec3d(Vec3d orig, Vec3d dest) {
        double[] delta = {orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
        double yaw = MathHelper.atan2(delta[0], -delta[2]);
        double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
        double pitch = MathHelper.atan2(delta[1], dist);
        return new Rotation(
                (float) (yaw * RAD_TO_DEG),
                (float) (pitch * RAD_TO_DEG)
        );
    }


}
