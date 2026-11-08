package com.dimaulyana.eightblades;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MagicSwordItem extends SwordItem {
    private final int tier;
    private final String abilityName;
    private final String abilityDesc;
    private final int baseCooldownTicks;

    public MagicSwordItem(int tier, int attackDamageBonus, float attackSpeed, String abilityName, String abilityDesc, int baseCooldownTicks, Settings settings) {
        super(ToolMaterials.NETHERITE, attackDamageBonus, attackSpeed, settings);
        this.tier = tier;
        this.abilityName = abilityName;
        this.abilityDesc = abilityDesc;
        this.baseCooldownTicks = baseCooldownTicks;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return tier >= 4;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient() && attacker.getWorld() instanceof ServerWorld serverWorld) {
            switch (this.tier) {
                case 1 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 0));
                    serverWorld.spawnParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
                case 2 -> {
                    serverWorld.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    attacker.heal(0.5f);
                }
                case 3 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1));
                    serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
                }
                case 4 -> {
                    serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                }
                case 5 -> {
                    target.setFrozenTicks(Math.min(target.getFrozenTicks() + 80, 240));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                    serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.04);
                }
                case 6 -> {
                    target.setOnFireFor(5);
                    serverWorld.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                }
                case 7 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 1));
                    serverWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                }
                case 8 -> {
                    target.setOnFireFor(6);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
                    attacker.heal(1.5f);
                    serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.35, 0.35, 0.35, 0.06);
                }
            }
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        StoryManager manager = StoryManager.get(serverWorld.getServer());
        String role = manager.getPlayerRole(player.getUuid());
        boolean isWarrior = "WARRIOR".equals(role);
        boolean isMage = "MAGE".equals(role);

        float damageMultiplier = isWarrior ? 1.35f : 1.0f;
        int cooldown = isMage ? Math.max(20, (int) (this.baseCooldownTicks * 0.65f)) : this.baseCooldownTicks;

        // Role active triggers
        if (isWarrior) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0, false, false, true));
        } else if (isMage) {
            for (ServerPlayerEntity ally : serverWorld.getPlayers()) {
                if (ally.squaredDistanceTo(player) <= 256.0) {
                    ally.heal(4.0f);
                    serverWorld.spawnParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2.0, ally.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
                }
            }
        }

        executeSwordAbility(serverWorld, player, damageMultiplier, isMage);
        player.getItemCooldownManager().set(this, cooldown);
        return TypedActionResult.success(stack);
    }

    private void executeSwordAbility(ServerWorld world, ServerPlayerEntity player, float dmgMult, boolean applyMageDebuff) {
        Vec3d look = player.getRotationVec(1.0f);
        Box area = player.getBoundingBox().expand(6.5);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !StoryManager.isBlacksmith(e));

        switch (this.tier) {
            case 1 -> {
                // Грязевой взрыв
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.2f, 0.7f);
                world.spawnParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 1.0, player.getZ(), 40, 2.5, 0.8, 2.5, 0.15);
                world.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 35, 2.5, 0.8, 2.5, 0.2);
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 8.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                    Vec3d push = t.getPos().subtract(player.getPos()).normalize().multiply(0.9);
                    t.addVelocity(push.x, 0.35, push.z);
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 2 -> {
                // Кольцо кактусовых игл
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 1.3f);
                world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 55, 3.0, 0.6, 3.0, 0.25);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 11.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 3 -> {
                // Ядовитый рывок паука
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.2f, 0.8f);
                player.setVelocity(look.x * 1.4, 0.35, look.z * 1.4);
                player.velocityModified = true;
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 45, 2.5, 0.8, 2.5, 0.1);
                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 25, 2.0, 0.4, 2.0, 0.05);
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 14.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 140, 2));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 4 -> {
                // Цепная молния
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.2f);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 60, 3.5, 1.2, 3.5, 0.25);
                int count = 0;
                for (LivingEntity t : targets) {
                    if (count++ >= 5) break;
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    t.damage(t.getDamageSources().playerAttack(player), 18.0f * dmgMult);
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 5 -> {
                // Абсолютный ноль
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.4f, 0.7f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 80, 4.0, 1.2, 4.0, 0.08);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 160, 1));
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 22.0f * dmgMult);
                    t.setFrozenTicks(300);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 4));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 6 -> {
                // Взрыв Сверхновой Феникса
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.4f, 0.8f);
                world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 90, 4.0, 1.2, 4.0, 0.12);
                world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 25, 3.0, 1.0, 3.0, 0.1);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 256.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0));
                        ally.heal(6.0f);
                    }
                }
                for (LivingEntity t : targets) {
                    t.setOnFireFor(8);
                    t.damage(t.getDamageSources().playerAttack(player), 26.0f * dmgMult);
                    t.addVelocity(0, 0.55, 0);
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 7 -> {
                // Астральная Сингулярность
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.4f, 0.6f);
                Vec3d center = player.getPos().add(look.multiply(4.0));
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1.0, center.z, 90, 2.0, 1.5, 2.0, 0.25);
                world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 40, 2.0, 1.5, 2.0, 0.1);
                Box vortexBox = new Box(center.x - 7, center.y - 4, center.z - 7, center.x + 7, center.y + 5, center.z + 7);
                List<LivingEntity> pulled = world.getEntitiesByClass(LivingEntity.class, vortexBox,
                        e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !StoryManager.isBlacksmith(e));
                for (LivingEntity t : pulled) {
                    Vec3d pull = center.subtract(t.getPos()).multiply(0.45);
                    t.setVelocity(pull.x, 0.6, pull.z);
                    t.damage(t.getDamageSources().playerAttack(player), 31.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 35, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 8 -> {
                // Катаклизм Вечного Затмения
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.4f, 0.9f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 3, player.getY() + 1.5, player.getZ() + look.z * 3, 4, 1.5, 0.5, 1.5, 0.0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 110, 5.0, 1.5, 5.0, 0.15);
                world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.5, player.getZ(), 60, 4.5, 1.5, 4.5, 0.15);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 256.0) {
                        ally.heal(8.0f);
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 160, 1));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 1));
                    }
                }
                Box cataclysmBox = player.getBoundingBox().expand(8.5);
                List<LivingEntity> hitList = world.getEntitiesByClass(LivingEntity.class, cataclysmBox,
                        e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !StoryManager.isBlacksmith(e));
                for (LivingEntity t : hitList) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    t.damage(t.getDamageSources().playerAttack(player), 40.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 120, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Уровень клинка: §e" + this.tier + " / 8"));
        tooltip.add(Text.literal("§6Способность [ПКМ]: §f" + this.abilityName));
        tooltip.add(Text.literal("§7" + this.abilityDesc));
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§c⚔ Роль [Хранитель Ярости]: §7+35% урона навыка и Щит"));
        tooltip.add(Text.literal("§b✦ Роль [Ткач Стихий]: §7-35% перезарядки и лечение дуэта"));
        tooltip.add(Text.literal("§8Нерушимый сюжетный артефакт Димы и Ульяны"));
    }
}
