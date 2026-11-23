package com.bartekclk.jlfix.mixin;

import com.dplayend.justleveling.common.capability.AptitudeCapability;
import com.dplayend.justleveling.network.packet.client.SyncAptitudeCapabilityCP;
import com.dplayend.justleveling.registry.RegistryAttributes;
import com.dplayend.justleveling.registry.RegistryCapabilities;
import com.dplayend.justleveling.registry.RegistryCommonEvents;
import com.dplayend.justleveling.registry.RegistryEffects;
import com.dplayend.justleveling.registry.RegistrySkills;
import com.dplayend.justleveling.registry.RegistrySounds;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryCommonEvents.class)
public class RegistryCommonEventsMixin {

    /** Applies or resets the Counter Attack damage modifier and syncs capability data. */
    @Unique
    private static void jlfix$applyCounterAttack(ServerPlayer player, boolean isCounterAttack, float modifier) {
        AptitudeCapability aptitude = AptitudeCapability.get(player);
        aptitude.setCounterAttack(isCounterAttack);
        (new RegistryAttributes.registerAttribute(
                player,
                Attributes.ATTACK_DAMAGE,
                (double) modifier,
                UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15590")
        )).amplifyAttribute(true);
        if (!isCounterAttack) {
            aptitude.setCounterAttackTimer(0);
        }
        SyncAptitudeCapabilityCP.send(player);
    }

    /** Triggers Counter Attack when a real player takes melee damage. */
    @Overwrite(remap = false)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(LivingHurtEvent event) {
        if (event.getSource() != null) {
            Entity source = event.getSource().getEntity();
            if (source instanceof LivingEntity livingEntity) {
                if (livingEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    float sourceDamage = (float) livingEntity.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                        if (RegistrySkills.COUNTER_ATTACK.get().isEnabled(player)) {
                            player.getCapability(RegistryCapabilities.APTITUDE).ifPresent((aptitudeCapability) -> {
                                float bonusDamage = (float) ((double) sourceDamage * RegistrySkills.COUNTER_ATTACK.get().getValue()[1] / 100.0D);
                                jlfix$applyCounterAttack(player, true, bonusDamage);
                            });
                        }
                    }
                }
            }
        }
    }

    /** Handles weapon restrictions, Limit Breaker, and Counter Attack reset on player attack. */
    @Overwrite(remap = false)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (player == null || player instanceof FakePlayer) {
            return;
        }

        if (!player.isCreative()) {
            AptitudeCapability provider = AptitudeCapability.get(player);
            ItemStack item = player.getMainHandItem();
            if (!provider.canUseItem(player, item)) {
                event.setCanceled(true);
            }
        }

        int random = (int) Math.floor(Math.random() * RegistrySkills.LIMIT_BREAKER.get().getValue()[0]);
        if (RegistrySkills.LIMIT_BREAKER.get().isEnabled(player)) {
            if (event.getEntity().level() instanceof ServerLevel level) {
                if (random == 1) {
                    target.hurt(target.damageSources().playerAttack(player), (float) RegistrySkills.LIMIT_BREAKER.get().getValue()[1]);
                    level.playSound(null, player, RegistrySounds.LIMIT_BREAKER.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
                }
            }
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(RegistryCapabilities.APTITUDE).ifPresent((capability) -> {
                AptitudeCapability aptitudeCapability = AptitudeCapability.get(serverPlayer);
                if (aptitudeCapability.getCounterAttack()) {
                    jlfix$applyCounterAttack(serverPlayer, false, 0.0F);
                }
            });
        }
    }

    /** Updates passive attributes, active skill effects, and item restrictions each player tick. */
    @Overwrite(remap = false)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player == null || player instanceof FakePlayer) {
            return;
        }

        if (!player.isCreative()) {
            player.getCapability(RegistryCapabilities.APTITUDE).ifPresent((aptitudeCapability) -> {
                AptitudeCapability provider = AptitudeCapability.get(player);
                ItemStack hand = player.getMainHandItem();
                ItemStack offHand = player.getOffhandItem();
                if (provider.isDroppable(player, hand)) {
                    player.drop(hand.copy(), false);
                    hand.setCount(0);
                }

                if (provider.isDroppable(player, offHand)) {
                    player.drop(offHand.copy(), false);
                    offHand.setCount(0);
                }
            });
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(RegistryCapabilities.APTITUDE).ifPresent((aptitudeCapability) -> {
                AptitudeCapability provider = AptitudeCapability.get(serverPlayer);
                if (provider.getCounterAttack()) {
                    provider.setCounterAttackTimer(provider.getCounterAttackTimer() + 1);
                    if ((double) provider.getCounterAttackTimer() >= RegistrySkills.COUNTER_ATTACK.get().getValue()[0] * 40.0D) {
                        jlfix$applyCounterAttack(serverPlayer, false, 0.0F);
                    }
                }
            });
            (new RegistryAttributes.registerAttribute(serverPlayer, Attributes.ATTACK_DAMAGE, (double) ((float) RegistrySkills.ONE_HANDED.get().getValue()[0]), UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15555"))).amplifyAttribute(serverPlayer.getOffhandItem().getCount() == 0 && RegistrySkills.ONE_HANDED.get().isEnabled(serverPlayer));
            (new RegistryAttributes.registerAttribute(serverPlayer, Attributes.ARMOR, (double) ((float) RegistrySkills.DIAMOND_SKIN.get().getValue()[1]), UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15556"))).amplifyAttribute(serverPlayer.isShiftKeyDown() && RegistrySkills.DIAMOND_SKIN.get().isEnabled(serverPlayer));
            RegistryAttributes.modifierAttributes(serverPlayer);
            if (serverPlayer.getHealth() > serverPlayer.getMaxHealth()) {
                serverPlayer.setHealth(serverPlayer.getMaxHealth());
            }

            (new RegistryEffects.addEffect(serverPlayer, RegistrySkills.CAT_EYES.get().isEnabled(player), MobEffects.NIGHT_VISION)).add(210);
            (new RegistryEffects.addEffect(serverPlayer, RegistrySkills.DIAMOND_SKIN.get().isEnabled(player), MobEffects.DAMAGE_RESISTANCE)).add(210, (int) (RegistrySkills.DIAMOND_SKIN.get().getValue()[0] - 1.0D));
        }
    }

    /** Skips aptitude checks when a fake player right-clicks a block. */
    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$skipFakeRightClickBlock(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }

    /** Skips aptitude checks when a fake player left-clicks a block. */
    @Inject(method = "onLeftClickBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$skipFakeLeftClickBlock(PlayerInteractEvent.LeftClickBlock event, CallbackInfo ci) {
        if (event.getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }

    /** Skips aptitude checks when a fake player uses an item. */
    @Inject(method = "onRightClickItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$skipFakeRightClickItem(PlayerInteractEvent.RightClickItem event, CallbackInfo ci) {
        if (event.getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }

    /** Skips aptitude checks when a fake player interacts with an entity. */
    @Inject(method = "onRightClickEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$skipFakeRightClickEntity(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }
}