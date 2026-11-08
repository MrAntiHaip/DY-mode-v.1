package com.dimaulyana.eightblades;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class StoryManager extends PersistentState {
    public static final String BLACKSMITH_TAG = "eightblades_blacksmith";
    public static final String BOSS_TAG = "eightblades_boss";
    public static final String MINION_TAG = "eightblades_minion";

    private boolean storyStarted = false;
    private int currentStage = 1; // 1..8, 9 = Completed
    private boolean swordCraftedForStage = false;
    private int bossSpawnCountdown = -1;
    private int bossAbilityCooldown = 80;
    private int bossPhase = 1;

    private final Map<String, Integer> deliveredItems = new HashMap<>();
    private final Map<UUID, String> playerRoles = new HashMap<>();
    private UUID blacksmithUuid = null;
    private UUID activeBossUuid = null;

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("§cБосс"),
            BossBar.Color.RED,
            BossBar.Style.NOTCHED_10
    );

    public record Requirement(Item item, String key, String displayName, int requiredCount) {}

    public record StageData(
            int stage,
            String title,
            List<Requirement> requirements,
            Supplier<Item> swordSupplier,
            String swordName,
            String npcStartQuote,
            String npcForgedQuote,
            String bossName,
            double bossMaxHp,
            BossBar.Color bossColor
    ) {}

    private static final List<StageData> STAGES = new ArrayList<>();

    static {
        STAGES.add(new StageData(
                1,
                "Глава 1: Грязевое Начало",
                List.of(
                        new Requirement(Items.STICK, "stick", "Палка", 2),
                        new Requirement(Items.DIRT, "dirt", "Земля", 8)
                ),
                () -> EightBladesMod.MUD_SWORD,
                "§6[I] Грязевой Меч Нуба",
                "§e[Кузнец Аркадий]: §fДима! Ульяна! Слава богам, вы пришли! Древняя Печать рухнула — все обычные мечи мира рассыпаются в прах! Нам придется ковать Необычные Магические Клинки с самого нуля. Для начала принесите мне §e2 Палки §fи §e8 Земли §f— просто бросьте их (Q) рядом со мной!",
                "§e[Кузнец Аркадий]: §aХа-ха! Выглядит нелепо, но в этой грязи течет древняя магия! Держите оба §6Грязевой Меч Нуба§a! Берегитесь — запах тины уже привлек первого стража: §2Болотного Грязежера§a!",
                "Болотный Грязежер Чвак",
                60.0,
                BossBar.Color.GREEN
        ));

        STAGES.add(new StageData(
                2,
                "Глава 2: Колючий Мираж",
                List.of(
                        new Requirement(Items.CACTUS, "cactus", "Кактус", 6),
                        new Requirement(Items.SAND, "sand", "Песок", 8),
                        new Requirement(Items.ROTTEN_FLESH, "rotten_flesh", "Гнилая плоть", 4)
                ),
                () -> EightBladesMod.CACTUS_SWORD,
                "§a[II] Кактусовый Шиполом",
                "§e[Кузнец Аркадий]: §fОтличная работа, Дима и Ульяна! Но грязевым мечом следующих чудовищ не пробить. Принесите мне §a6 Кактусов§f, §e8 Песка §fи §c4 Гнилой плоти§f. Скуем клинок, который пускает веер острых шипов!",
                "§e[Кузнец Аркадий]: §aГотово! Вручаю обоим §2Кактусовый Шиполом§a! Нажмите ПКМ, чтобы выпускать кольцо игл и лечиться. Приготовьтесь: из песков идет §6Пустынный Иглострел Хасан§a!",
                "Пустынный Иглострел Хасан",
                110.0,
                BossBar.Color.YELLOW
        ));

        STAGES.add(new StageData(
                3,
                "Глава 3: Шепот Паутины",
                List.of(
                        new Requirement(Items.STRING, "string", "Нить", 6),
                        new Requirement(Items.SPIDER_EYE, "spider_eye", "Паучий глаз", 3),
                        new Requirement(Items.COBBLESTONE, "cobblestone", "Булыжник", 10)
                ),
                () -> EightBladesMod.VENOM_SWORD,
                "§2[III] Паучий Клинок Яда",
                "§e[Кузнец Аркадий]: §fВы сражаетесь как настоящие легенды! Теперь нам нужен нейротоксин глубин. Добудьте §f6 Нитей§f, §23 Паучьих глаза §fи §710 Булыжника§f. Бросайте их ко мне!",
                "§e[Кузнец Аркадий]: §aЯд впитался в лезвие! Получите §2Паучий Клинок Яда§a! На ПКМ он совершает стремительный ядовитый рывок. Осторожно, из теней спускается §5Матриарх Черной Паутины§a!",
                "Матриарх Черной Паутины",
                170.0,
                BossBar.Color.PURPLE
        ));

        STAGES.add(new StageData(
                4,
                "Глава 4: Грозовой Резонанс",
                List.of(
                        new Requirement(Items.COPPER_INGOT, "copper_ingot", "Медный слиток", 8),
                        new Requirement(Items.IRON_INGOT, "iron_ingot", "Железный слиток", 4),
                        new Requirement(Items.REDSTONE, "redstone", "Редстоун", 8)
                ),
                () -> EightBladesMod.THUNDER_SWORD,
                "§b[IV] Грозовой Клинок Цепи",
                "§e[Кузнец Аркадий]: §fПоловина пути позади! Следующий страж закован в титановую броню — его возьмет только живая молния. Принесите §68 Медных слитков§f, §f4 Железных слитка §fи §c8 Редстоуна§f!",
                "§e[Кузнец Аркадий]: §bСлышите треск озона?! §fВот ваш §bГрозовой Клинок Цепи§f! На ПКМ он поражает врагов настоящей цепной молнией! А вот и его цель — §3Перегруженный Автоматон Титан§f!",
                "Перегруженный Автоматон Титан",
                240.0,
                BossBar.Color.BLUE
        ));

        STAGES.add(new StageData(
                5,
                "Глава 5: Дыхание Вечной Зимы",
                List.of(
                        new Requirement(Items.SNOWBALL, "snowball", "Снежок", 12),
                        new Requirement(Items.LAPIS_LAZULI, "lapis_lazuli", "Лазурит", 6),
                        new Requirement(Items.BONE, "bone", "Кость", 5)
                ),
                () -> EightBladesMod.FROST_SWORD,
                "§3[V] Клинок Ледяной Вьюги",
                "§e[Кузнец Аркадий]: §fДима, Ульяна, воздух вокруг леденеет... Чтобы выжить в битве с Королем Мерзлоты, принесите мне §b12 Снежков§f, §96 Лазурита §fи §f5 Костей§f!",
                "§e[Кузнец Аркадий]: §bХолод запечатан в стали! §fДержите §3Клинок Ледяной Вьюги§f! Он замораживает врагов в глыбу льда. Встречайте §bКороля Вечной Мерзлоты Борея§f!",
                "Король Вечной Мерзлоты Борей",
                320.0,
                BossBar.Color.WHITE
        ));

        STAGES.add(new StageData(
                6,
                "Глава 6: Сердце Пепельного Феникса",
                List.of(
                        new Requirement(Items.GOLD_INGOT, "gold_ingot", "Золотой слиток", 6),
                        new Requirement(Items.GUNPOWDER, "gunpowder", "Порох", 5),
                        new Requirement(Items.COAL, "coal", "Уголь", 10),
                        new Requirement(Items.FLINT, "flint", "Кремень", 4)
                ),
                () -> EightBladesMod.PHOENIX_SWORD,
                "§c[VI] Клинок Пепельного Феникса",
                "§e[Кузнец Аркадий]: §fЛед повержен! Теперь нам нужно первородное пламя возрождения. Соберите §66 Золотых слитков§f, §75 Пороха§f, §810 Угля §fи §f4 Кремня§f!",
                "§e[Кузнец Аркадий]: §6Пламя Феникса поет в ваших руках! §fОба получаете §cКлинок Пепельного Феникса§f — он дарует Огнестойкость, лечит и выжигает врагов! Но берегитесь: явился §4Инфернальный Вестник Пепла Игнис§f!",
                "Инфернальный Вестник Пепла Игнис",
                420.0,
                BossBar.Color.RED
        ));

        STAGES.add(new StageData(
                7,
                "Глава 7: Разлом Астрала",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "Алмаз", 3),
                        new Requirement(Items.ENDER_PEARL, "ender_pearl", "Жемчуг Эндера", 2),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 4)
                ),
                () -> EightBladesMod.ASTRAL_SWORD,
                "§d[VII] Клинок Астрального Разлома",
                "§e[Кузнец Аркадий]: §fОстался предпоследний шаг перед финалом! Нам нужно подчинить себе саму гравитацию. Принесите §b3 Алмаза§f, §32 Жемчуга Эндера §fи §54 Обсидиана§f!",
                "§e[Кузнец Аркадий]: §dПространство склоняется перед вами! §fВручаю §dКлинок Астрального Разлома§f! Он создает черную дыру, стягивающую врагов. Приготовьтесь к бою с §5Пожирателем Пространства Эоном§f!",
                "Пожиратель Пространства Эон",
                550.0,
                BossBar.Color.PURPLE
        ));

        STAGES.add(new StageData(
                8,
                "ФИНАЛ: Вечное Затмение",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "Алмаз", 4),
                        new Requirement(Items.GOLD_INGOT, "gold_ingot", "Золотой слиток", 8),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 4),
                        new Requirement(Items.GOLDEN_APPLE, "golden_apple", "Золотое яблоко", 1)
                ),
                () -> EightBladesMod.ECLIPSE_SWORD,
                "§5§l[VIII] Клинок Вечного Затмения",
                "§e[Кузнец Аркадий]: §c§lДИМА! УЛЬЯНА! ЭТО ФИНАЛ! §fСам §4Архонт Пустоты Малкор §fпробуждается, чтобы стереть наш мир! Принесите §b4 Алмаза§f, §68 Золотых слитков§f, §54 Обсидиана §fи §e1 Золотое яблоко§f, чтобы выковать абсолютное оружие — Клинок Вечного Затмения!",
                "§e[Кузнец Аркадий]: §5§lШЕДЕВР ЗАВЕРШЕН! §fДима и Ульяна, примите §5§lКлинок Вечного Затмения§f! У Малкора §c850 HP §fи §e3 смертоносные фазы§f! Держитесь рядом ради Резонанса Дуэта и спасите этот мир!",
                "АРХОНТ ПУСТОТЫ МАЛКОР",
                850.0,
                BossBar.Color.PINK
        ));
    }

    public static StoryManager get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                StoryManager::fromNbt,
                StoryManager::new,
                "eightblades_story"
        );
    }

    public static StageData getStageData(int stage) {
        int idx = Math.max(0, Math.min(STAGES.size() - 1, stage - 1));
        return STAGES.get(idx);
    }

    public boolean isStoryStarted() {
        return storyStarted;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public boolean isSwordCraftedForStage() {
        return swordCraftedForStage;
    }

    public String getPlayerRole(UUID uuid) {
        return playerRoles.getOrDefault(uuid, "NONE");
    }

    public String getRoleDisplayName(String roleCode) {
        return switch (roleCode) {
            case "WARRIOR" -> "§c⚔ Хранитель Ярости";
            case "MAGE" -> "§b✦ Ткач Стихий";
            default -> "§7Не выбрана (ПКМ Компасом)";
        };
    }

    public void setPlayerRole(ServerPlayerEntity player, String roleCode) {
        playerRoles.put(player.getUuid(), roleCode);
        markDirty();
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        if ("WARRIOR".equals(roleCode)) {
            broadcast(player.getServer(), "§6[Судьба] §e" + player.getName().getString() + " §fвыбрал роль: §c⚔ Хранитель Ярости §7(+4 HP, Сопротивление, +35% урона навыков мечей)!");
        } else if ("MAGE".equals(roleCode)) {
            broadcast(player.getServer(), "§6[Судьба] §e" + player.getName().getString() + " §fвыбрал роль: §b✦ Ткач Стихий §7(Скорость, -35% перезарядки мечей, Исцеление дуэта)!");
        }
        syncHudToAll(player.getServer());
    }

    public static boolean isBlacksmith(Entity entity) {
        return entity != null && entity.getCommandTags().contains(BLACKSMITH_TAG);
    }

    public static boolean isBoss(Entity entity) {
        return entity != null && entity.getCommandTags().contains(BOSS_TAG);
    }

    public void giveCompassIfNeeded(ServerPlayerEntity player) {
        boolean hasCompass = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(Items.COMPASS)) {
                hasCompass = true;
                break;
            }
        }
        if (!hasCompass) {
            ItemStack compass = new ItemStack(Items.COMPASS);
            compass.setCustomName(Text.literal("§6✦ Компас Судьбы [Дима и Ульяна] §7(ПКМ)"));
            player.getInventory().insertStack(compass);
        }
    }

    public void startStory(ServerPlayerEntity starter) {
        MinecraftServer server = starter.getServer();
        if (server == null) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            giveCompassIfNeeded(p);
        }

        if (!storyStarted) {
            storyStarted = true;
            currentStage = 1;
            swordCraftedForStage = false;
            deliveredItems.clear();
            markDirty();

            broadcast(server, "§8==================================================");
            broadcast(server, "§6§l   ✦ ЛЕГЕНДА ВОСЬМИ КЛИНКОВ: ДИМА И УЛЬЯНА ✦");
            broadcast(server, "§7   Обычные мечи прокляты и рассыпаются в прах!");
            broadcast(server, "§7   Откройте §eКомпас (ПКМ)§7, чтобы выбрать свою Роль.");
            broadcast(server, "§8==================================================");
        }

        spawnOrTeleportBlacksmith(starter);
        StageData stage = getStageData(currentStage);
        if (currentStage <= 8) {
            broadcast(server, stage.npcStartQuote());
        }
        syncHudToAll(server);
    }

    public void resetStory(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        clearExistingBoss(server.getOverworld());
        storyStarted = false;
        currentStage = 1;
        swordCraftedForStage = false;
        bossSpawnCountdown = -1;
        deliveredItems.clear();
        bossBar.clearPlayers();
        markDirty();
        broadcast(server, "§e[Сюжет] §fПрогресс сюжета сброшен! Введите §a/st §fдля нового старта.");
        syncHudToAll(server);
    }

    public void spawnOrTeleportBlacksmith(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        VillagerEntity existing = findBlacksmith(world);
        Vec3d look = player.getRotationVec(1.0f);
        double tx = player.getX() + look.x * 2.5;
        double tz = player.getZ() + look.z * 2.5;
        double ty = player.getY();

        if (existing != null && existing.isAlive()) {
            existing.refreshPositionAndAngles(tx, ty, tz, player.getYaw() + 180.0f, 0.0f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, existing.getX(), existing.getY() + 1.0, existing.getZ(), 15, 0.4, 0.5, 0.4, 0.05);
            world.playSound(null, existing.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            return;
        }

        VillagerEntity villager = EntityType.VILLAGER.create(world);
        if (villager != null) {
            villager.refreshPositionAndAngles(tx, ty, tz, player.getYaw() + 180.0f, 0.0f);
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.WEAPONSMITH).withLevel(5));
            villager.setCustomName(Text.literal("§6§lКузнец Аркадий §e[Бросьте ресурсы сюда]"));
            villager.setCustomNameVisible(true);
            villager.setInvulnerable(true);
            villager.setAiDisabled(true);
            villager.setPersistent();
            villager.addCommandTag(BLACKSMITH_TAG);
            world.spawnEntity(villager);
            this.blacksmithUuid = villager.getUuid();
            markDirty();
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.0, villager.getZ(), 35, 0.5, 0.8, 0.5, 0.1);
            world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.2f, 1.0f);
        }
    }

    @org.jetbrains.annotations.Nullable
    public VillagerEntity findBlacksmith(ServerWorld world) {
        if (blacksmithUuid != null) {
            Entity e = world.getEntity(blacksmithUuid);
            if (e instanceof VillagerEntity v && v.isAlive()) {
                return v;
            }
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<VillagerEntity> list = world.getEntitiesByClass(VillagerEntity.class, p.getBoundingBox().expand(64.0), StoryManager::isBlacksmith);
            if (!list.isEmpty()) {
                blacksmithUuid = list.get(0).getUuid();
                return list.get(0);
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    public LivingEntity findActiveBoss(ServerWorld world) {
        if (activeBossUuid != null) {
            Entity e = world.getEntity(activeBossUuid);
            if (e instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(96.0), StoryManager::isBoss);
            if (!list.isEmpty()) {
                activeBossUuid = list.get(0).getUuid();
                return list.get(0);
            }
        }
        return null;
    }

    private void clearExistingBoss(ServerWorld world) {
        LivingEntity boss = findActiveBoss(world);
        if (boss != null) {
            boss.discard();
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<MobEntity> minions = world.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(80.0),
                    m -> m.getCommandTags().contains(MINION_TAG) || m.getCommandTags().contains(BOSS_TAG));
            for (MobEntity m : minions) {
                m.discard();
            }
        }
        activeBossUuid = null;
        bossBar.clearPlayers();
    }

    public boolean trySubmitStack(MinecraftServer server, ItemStack stack) {
        if (!storyStarted || currentStage > 8 || swordCraftedForStage || stack.isEmpty()) {
            return false;
        }
        StageData stage = getStageData(currentStage);
        for (Requirement req : stage.requirements()) {
            if (stack.isOf(req.item())) {
                int current = deliveredItems.getOrDefault(req.key(), 0);
                int needed = req.requiredCount() - current;
                if (needed > 0) {
                    int take = Math.min(needed, stack.getCount());
                    stack.decrement(take);
                    deliveredItems.put(req.key(), current + take);
                    markDirty();
                    broadcast(server, "§a[Кузнец Аркадий] §fПринято: §e" + req.displayName() + " +" + take
                            + " §7(" + (current + take) + "/" + req.requiredCount() + ")");
                    checkStageRequirementsComplete(server);
                    syncHudToAll(server);
                    return true;
                }
            }
        }
        return false;
    }

    private void checkStageRequirementsComplete(MinecraftServer server) {
        StageData stage = getStageData(currentStage);
        for (Requirement req : stage.requirements()) {
            if (deliveredItems.getOrDefault(req.key(), 0) < req.requiredCount()) {
                return;
            }
        }

        // All items collected!
        swordCraftedForStage = true;
        bossSpawnCountdown = 100; // 5 seconds until boss spawns
        markDirty();

        broadcast(server, "§8--------------------------------------------------");
        broadcast(server, stage.npcForgedQuote());
        broadcast(server, "§6§l✦ ОБА ИГРОКА ПОЛУЧАЮТ: " + stage.swordName() + " §6§l✦");
        broadcast(server, "§c⚠ Приготовьтесь! Босс §e" + stage.bossName() + " §cпоявится через 5 секунд!");
        broadcast(server, "§8--------------------------------------------------");

        Item rewardItem = stage.swordSupplier().get();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack reward = new ItemStack(rewardItem);
            if (!player.getInventory().insertStack(reward)) {
                player.dropItem(reward, false);
            }
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.0f, 1.1f);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
        }
    }

    public void spawnCurrentStageBoss(ServerWorld world, BlockPos centerPos) {
        if (currentStage > 8) return;
        clearExistingBoss(world);

        StageData stage = getStageData(currentStage);
        MobEntity boss = createBossEntityForStage(world, currentStage);
        if (boss == null) return;

        double spawnX = centerPos.getX() + 6.0;
        double spawnZ = centerPos.getZ() + 6.0;
        double spawnY = centerPos.getY() + 0.5;

        boss.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0.0f, 0.0f);
        boss.addCommandTag(BOSS_TAG);
        boss.setPersistent();
        boss.setCustomNameVisible(true);

        EntityAttributeInstance maxHpAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(stage.bossMaxHp());
        }
        EntityAttributeInstance kbAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.setBaseValue(0.55);
        }
        EntityAttributeInstance speedAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && currentStage >= 3) {
            speedAttr.setBaseValue(Math.max(speedAttr.getBaseValue(), 0.28));
        }

        boss.setHealth((float) stage.bossMaxHp());
        bossPhase = 1;
        bossAbilityCooldown = 80;

        String hpTitle = "§6[Босс " + currentStage + "/8] §e" + stage.bossName() + " §c❤ " + (int) stage.bossMaxHp() + " / " + (int) stage.bossMaxHp() + " HP";
        boss.setCustomName(Text.literal(hpTitle));

        world.spawnEntity(boss);
        activeBossUuid = boss.getUuid();
        markDirty();

        bossBar.setColor(stage.bossColor());
        bossBar.setName(Text.literal(hpTitle));
        bossBar.setPercent(1.0f);
        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
            bossBar.addPlayer(p);
        }

        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
    }

    private MobEntity createBossEntityForStage(ServerWorld world, int stage) {
        switch (stage) {
            case 1 -> {
                ZombieEntity z = EntityType.ZOMBIE.create(world);
                if (z != null) {
                    z.setBaby(false);
                    z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.SLIME_BLOCK));
                    z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.MUD));
                }
                return z;
            }
            case 2 -> {
                HuskEntity h = EntityType.HUSK.create(world);
                if (h != null) {
                    h.setBaby(false);
                    h.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CACTUS));
                    h.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                    h.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                }
                return h;
            }
            case 3 -> {
                CaveSpiderEntity s = EntityType.CAVE_SPIDER.create(world);
                if (s != null) {
                    s.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, false, false));
                }
                return s;
            }
            case 4 -> {
                IronGolemEntity g = EntityType.IRON_GOLEM.create(world);
                if (g != null) {
                    g.setPlayerCreated(false);
                }
                return g;
            }
            case 5 -> {
                StrayEntity st = EntityType.STRAY.create(world);
                if (st != null) {
                    st.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    st.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    st.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
                }
                return st;
            }
            case 6 -> {
                WitherSkeletonEntity ws = EntityType.WITHER_SKELETON.create(world);
                if (ws != null) {
                    ws.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                    ws.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    ws.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
                    ws.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 999999, 0, false, false));
                }
                return ws;
            }
            case 7 -> {
                VindicatorEntity v = EntityType.VINDICATOR.create(world);
                if (v != null) {
                    v.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DRAGON_HEAD));
                    v.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                }
                return v;
            }
            default -> {
                WitherSkeletonEntity archon = EntityType.WITHER_SKELETON.create(world);
                if (archon != null) {
                    archon.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                    archon.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    archon.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                    archon.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
                    archon.equipStack(EquipmentSlot.MAINHAND, new ItemStack(EightBladesMod.ECLIPSE_SWORD));
                    archon.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 999999, 0, false, false));
                }
                return archon;
            }
        }
    }

    public void tick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        long time = overworld.getTime();

        // 1. Destroy banned vanilla swords & apply Role passives + Duet Synergy
        tickPlayersAndSynergy(server, time);

        if (!storyStarted) {
            if (time % 20 == 0) syncHudToAll(server);
            return;
        }

        // 2. Check dropped items near Blacksmith every 10 ticks
        if (time % 10 == 0 && currentStage <= 8 && !swordCraftedForStage) {
            VillagerEntity smith = findBlacksmith(overworld);
            if (smith != null) {
                Box pickupBox = smith.getBoundingBox().expand(3.5);
                List<ItemEntity> drops = overworld.getEntitiesByClass(ItemEntity.class, pickupBox, ItemEntity::isAlive);
                for (ItemEntity itemEntity : drops) {
                    ItemStack stack = itemEntity.getStack();
                    if (trySubmitStack(server, stack)) {
                        overworld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, smith.getX(), smith.getY() + 1.2, smith.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                        overworld.spawnParticles(ParticleTypes.ENCHANT, smith.getX(), smith.getY() + 1.5, smith.getZ(), 20, 0.5, 0.5, 0.5, 0.2);
                        overworld.playSound(null, smith.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                        if (stack.isEmpty()) {
                            itemEntity.discard();
                        }
                    }
                }
            }
        }

        // 3. Countdown to Boss Spawn
        if (bossSpawnCountdown > 0) {
            bossSpawnCountdown--;
            if (bossSpawnCountdown == 0) {
                VillagerEntity smith = findBlacksmith(overworld);
                BlockPos center = smith != null ? smith.getBlockPos()
                        : (!overworld.getPlayers().isEmpty() ? overworld.getPlayers().get(0).getBlockPos() : BlockPos.ORIGIN);
                spawnCurrentStageBoss(overworld, center);
            }
        }

        // 4. Active Boss AI, BossBar HP update & Death check
        if (currentStage <= 8 && swordCraftedForStage && bossSpawnCountdown <= 0) {
            LivingEntity boss = findActiveBoss(overworld);
            if (boss != null && boss.isAlive()) {
                tickActiveBoss(server, overworld, boss);
            } else if (activeBossUuid != null) {
                onBossDefeated(server, overworld);
            }
        }

        if (time % 15 == 0) {
            syncHudToAll(server);
        }
    }

    private void tickPlayersAndSynergy(MinecraftServer server, long time) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        boolean hasWarrior = false;
        boolean hasMage = false;
        ServerPlayerEntity warriorPlayer = null;
        ServerPlayerEntity magePlayer = null;

        for (ServerPlayerEntity p : players) {
            // Check banned vanilla swords
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (isBannedVanillaSword(stack.getItem())) {
                    inv.setStack(i, ItemStack.EMPTY);
                    p.playSound(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    p.sendMessage(Text.literal("§c[Проклятие Печати] §7Обычный меч рассыпался в прах! Куйте Магические Клинки у Кузнеца Аркадия."), true);
                }
            }

            String role = getPlayerRole(p.getUuid());
            if ("WARRIOR".equals(role)) {
                hasWarrior = true;
                warriorPlayer = p;
                if (time % 40 == 0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 100, 0, true, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, false, true));
                }
            } else if ("MAGE".equals(role)) {
                hasMage = true;
                magePlayer = p;
                if (time % 40 == 0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, false, true));
                }
            }
        }

        // Duet Synergy when Dima & Ulyana have complementary roles within 16 blocks
        if (time % 40 == 0 && hasWarrior && hasMage && warriorPlayer != null && magePlayer != null) {
            if (warriorPlayer.getWorld() == magePlayer.getWorld() && warriorPlayer.squaredDistanceTo(magePlayer) <= 256.0) {
                warriorPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, false, true));
                magePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, false, true));
            }
        }
    }

    public static boolean isBannedVanillaSword(Item item) {
        return item == Items.WOODEN_SWORD
                || item == Items.STONE_SWORD
                || item == Items.IRON_SWORD
                || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD
                || item == Items.NETHERITE_SWORD;
    }

    private void tickActiveBoss(MinecraftServer server, ServerWorld world, LivingEntity boss) {
        StageData stage = getStageData(currentStage);
        float hp = Math.max(0.0f, boss.getHealth());
        float maxHp = boss.getMaxHealth();
        float ratio = Math.max(0.0f, Math.min(1.0f, hp / maxHp));

        String phaseSuffix = "";
        if (currentStage == 8) {
            if (hp <= 250.0f && bossPhase < 3) {
                bossPhase = 3;
                broadcast(server, "§4§l[АРХОНТ МАЛКОР - ФАЗА 3/3]: §c«ПОСЛЕДНЕЕ ЗАТМЕНИЕ! ПУСТОТА ПОГЛОТИТ ВАС, ДИМА И УЛЬЯНА!»");
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.5f, 0.7f);
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1));
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 999999, 1));
            } else if (hp <= 550.0f && bossPhase < 2) {
                bossPhase = 2;
                broadcast(server, "§5§l[АРХОНТ МАЛКОР - ФАЗА 2/3]: §d«Призываю Стражей Затмения! Мой щит нерушим, пока они живы!»");
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 1.2f);
                spawnBossMinions(world, boss, EntityType.VEX, 3, "§dТень Затмения");
            }
            phaseSuffix = " §d[Фаза " + bossPhase + "/3]";
        }

        String barTitle = "§6[Босс " + currentStage + "/8] §e" + stage.bossName() + phaseSuffix
                + " §c❤ " + (int) Math.ceil(hp) + " / " + (int) maxHp + " HP";
        bossBar.setName(Text.literal(barTitle));
        bossBar.setPercent(ratio);
        boss.setCustomName(Text.literal(barTitle));

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }

        // Ensure Iron Golem or Mob targets nearest player
        ServerPlayerEntity nearest = findNearestPlayer(world, boss);
        if (nearest != null && boss instanceof MobEntity mob && mob.getTarget() == null) {
            mob.setTarget(nearest);
        }

        if (bossAbilityCooldown > 0) {
            bossAbilityCooldown--;
            return;
        }

        if (nearest == null) return;
        bossAbilityCooldown = currentStage == 8 ? (bossPhase == 3 ? 55 : 75) : 95;
        executeBossSpecialAbility(server, world, boss, nearest, currentStage);
    }

    private void executeBossSpecialAbility(MinecraftServer server, ServerWorld world, LivingEntity boss, ServerPlayerEntity target, int stage) {
        Box area = boss.getBoundingBox().expand(10.0);
        List<ServerPlayerEntity> nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity.class, area, ServerPlayerEntity::isAlive);

        switch (stage) {
            case 1 -> {
                // Болотный прыжок + слизни
                Vec3d dir = target.getPos().subtract(boss.getPos()).normalize();
                boss.setVelocity(dir.x * 0.9, 0.5, dir.z * 0.9);
                world.spawnParticles(ParticleTypes.ITEM_SLIME, boss.getX(), boss.getY() + 1.0, boss.getZ(), 35, 1.5, 0.5, 1.5, 0.15);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.2f, 0.6f);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 1));
                }
                spawnBossMinions(world, boss, EntityType.SLIME, 2, "§2Болотный комок");
            }
            case 2 -> {
                // Песчаная буря + шипы
                world.spawnParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 1.0, boss.getZ(), 50, 3.0, 1.0, 3.0, 0.1);
                world.spawnParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 3.0, 1.0, 3.0, 0.2);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_HUSK_CONVERTED_TO_ZOMBIE, SoundCategory.HOSTILE, 1.2f, 0.9f);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.damage(p.getDamageSources().mobAttack(boss), 5.0f);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0));
                }
            }
            case 3 -> {
                // Прыжок паучихи за спину + кокон
                Vec3d behind = target.getPos().subtract(target.getRotationVec(1.0f).multiply(1.5));
                boss.refreshPositionAfterTeleport(behind.x, target.getY(), behind.z);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, boss.getX(), boss.getY() + 0.8, boss.getZ(), 30, 1.5, 0.5, 1.5, 0.1);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.HOSTILE, 1.4f, 0.7f);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 2));
                }
            }
            case 4 -> {
                // Магнитное притяжение + разряд молнии
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.4f, 0.6f);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.2, boss.getZ(), 60, 3.0, 1.2, 3.0, 0.2);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    Vec3d pull = boss.getPos().subtract(p.getPos()).normalize().multiply(0.8);
                    p.setVelocity(pull.x, 0.4, pull.z);
                    p.velocityModified = true;
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(p.getX(), p.getY(), p.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    p.damage(p.getDamageSources().mobAttack(boss), 7.0f);
                }
            }
            case 5 -> {
                // Вьюга Вечной Мерзлоты
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.4f, 0.6f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 1.2, boss.getZ(), 85, 4.0, 1.5, 4.0, 0.08);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.setFrozenTicks(240);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 90, 2));
                    p.damage(p.getDamageSources().mobAttack(boss), 8.0f);
                }
            }
            case 6 -> {
                // Кольцо Пепельного Феникса
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.4f, 0.7f);
                world.spawnParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1.0, boss.getZ(), 90, 4.0, 1.2, 4.0, 0.12);
                world.spawnParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + 1.0, boss.getZ(), 25, 3.0, 1.0, 3.0, 0.1);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.setOnFireFor(5);
                    p.damage(p.getDamageSources().mobAttack(boss), 9.0f);
                    p.addVelocity(0, 0.5, 0);
                    p.velocityModified = true;
                }
            }
            case 7 -> {
                // Астральный сдвиг
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 80, 3.5, 1.5, 3.5, 0.2);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 35, 1));
                    p.damage(p.getDamageSources().mobAttack(boss), 10.0f);
                }
                spawnBossMinions(world, boss, EntityType.VEX, 2, "§5Фантом Эона");
            }
            case 8 -> {
                // Финальный Босс Малкор (3 фазы)
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.4f, 0.8f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 1.2, boss.getZ(), 95, 4.5, 1.5, 4.5, 0.15);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    if (bossPhase >= 2) {
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                        if (bolt != null) {
                            bolt.refreshPositionAfterTeleport(p.getX(), p.getY(), p.getZ());
                            bolt.setCosmetic(true);
                            world.spawnEntity(bolt);
                        }
                    }
                    if (bossPhase == 3) {
                        Vec3d pull = boss.getPos().subtract(p.getPos()).normalize().multiply(0.75);
                        p.setVelocity(pull.x, 0.45, pull.z);
                        p.velocityModified = true;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
                    }
                    p.damage(p.getDamageSources().mobAttack(boss), bossPhase == 3 ? 13.0f : 10.0f);
                }
            }
        }
    }

    private <T extends MobEntity> void spawnBossMinions(ServerWorld world, LivingEntity boss, EntityType<T> type, int count, String name) {
        List<MobEntity> existing = world.getEntitiesByClass(MobEntity.class, boss.getBoundingBox().expand(24.0),
                e -> e.getCommandTags().contains(MINION_TAG));
        if (existing.size() >= 5) return;

        for (int i = 0; i < count; i++) {
            T m = type.create(world);
            if (m != null) {
                double ox = (world.random.nextDouble() - 0.5) * 4.0;
                double oz = (world.random.nextDouble() - 0.5) * 4.0;
                m.refreshPositionAndAngles(boss.getX() + ox, boss.getY() + 0.5, boss.getZ() + oz, 0.0f, 0.0f);
                if (m instanceof SlimeEntity slime) {
                    slime.setSize(1, true);
                }
                m.setCustomName(Text.literal(name));
                m.setCustomNameVisible(true);
                m.addCommandTag(MINION_TAG);
                world.spawnEntity(m);
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private ServerPlayerEntity findNearestPlayer(ServerWorld world, Entity center) {
        ServerPlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isAlive() && !p.isSpectator()) {
                double d = p.squaredDistanceTo(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private void onBossDefeated(MinecraftServer server, ServerWorld world) {
        StageData defeatedStage = getStageData(currentStage);
        activeBossUuid = null;
        bossBar.clearPlayers();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.heal(p.getMaxHealth());
            p.giveItemStack(new ItemStack(Items.GOLDEN_APPLE, 2));
            world.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 50, 0.6, 0.8, 0.6, 0.15);
        }

        if (currentStage < 8) {
            broadcast(server, "§a§l★ БОСС ПОВЕРЖЕН: §e" + defeatedStage.bossName() + "§a§l! ★");
            currentStage++;
            swordCraftedForStage = false;
            deliveredItems.clear();
            markDirty();

            StageData nextStage = getStageData(currentStage);
            broadcast(server, nextStage.npcStartQuote());
        } else {
            currentStage = 9;
            swordCraftedForStage = true;
            markDirty();
            broadcast(server, "§6§l==================================================");
            broadcast(server, "§e§l   ★ ПОБЕДА! АРХОНТ ПУСТОТЫ МАЛКОР ПОВЕРЖЕН! ★");
            broadcast(server, "§f   Дима и Ульяна прошли путь всех Восьми Клинков");
            broadcast(server, "§f   и сняли Древнее Проклятие с этого мира!");
            broadcast(server, "§e   [Кузнец Аркадий]: §a«Вы — величайший дуэт героев!»");
            broadcast(server, "§6§l==================================================");
        }
        syncHudToAll(server);
    }

    public void openCompassMenu(ServerPlayerEntity player) {
        SimpleInventory menuInv = new SimpleInventory(27);
        populateMenuInventory(player, menuInv);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, menuInv, 3) {
                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
                        if (!(clickingPlayer instanceof ServerPlayerEntity sp)) return;
                        MinecraftServer srv = sp.getServer();
                        if (srv == null) return;

                        if (slotIndex == 11) {
                            setPlayerRole(sp, "WARRIOR");
                            populateMenuInventory(sp, menuInv);
                            sendContentUpdates();
                        } else if (slotIndex == 15) {
                            setPlayerRole(sp, "MAGE");
                            populateMenuInventory(sp, menuInv);
                            sendContentUpdates();
                        } else if (slotIndex == 13) {
                            sp.closeHandledScreen();
                            startStory(sp);
                        } else if (slotIndex == 21) {
                            sp.closeHandledScreen();
                            spawnOrTeleportBlacksmith(sp);
                        } else if (slotIndex == 23) {
                            sp.closeHandledScreen();
                            if (storyStarted && currentStage <= 8 && swordCraftedForStage) {
                                spawnCurrentStageBoss(sp.getServerWorld(), sp.getBlockPos());
                                broadcast(srv, "§6[Сюжет] §e" + sp.getName().getString() + " §fпризвал босса текущей главы!");
                            } else {
                                sp.sendMessage(Text.literal("§cСначала сдайте все ресурсы Кузнецу Аркадию и получите меч этой главы!"), false);
                            }
                        }
                    }

                    @Override
                    public ItemStack quickMove(PlayerEntity player, int slot) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }
                },
                Text.literal("§8✦ §6Меню Судьбы: Дима и Ульяна §8✦")
        ));
    }

    private void populateMenuInventory(ServerPlayerEntity player, SimpleInventory inv) {
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.setCustomName(Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glass.copy());
        }

        String currentRole = getPlayerRole(player.getUuid());

        // Slot 11: Warrior Role
        ItemStack warriorIcon = new ItemStack(Items.NETHERITE_CHESTPLATE);
        warriorIcon.setCustomName(Text.literal("§c§l⚔ Роль: Хранитель Ярости " + ("WARRIOR".equals(currentRole) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(warriorIcon, List.of(
                "§7Идеально для авангарда дуэта!",
                "§f• Пассивно: §c+2 Сердца (Health Boost) §fи §bСопротивление I",
                "§f• Бонус к мечам: §6+35% урона §fот способностей [ПКМ]",
                "§f• При активации меча дает §eЩит Поглощения",
                "§aНажмите, чтобы выбрать эту роль!"
        ));
        inv.setStack(11, warriorIcon);

        // Slot 15: Mage Role
        ItemStack mageIcon = new ItemStack(Items.ENCHANTED_BOOK);
        mageIcon.setCustomName(Text.literal("§b§l✦ Роль: Ткач Стихий " + ("MAGE".equals(currentRole) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(mageIcon, List.of(
                "§7Идеально для поддержки и контроля стихий!",
                "§f• Пассивно: §bСкорость I §fв походе и бою",
                "§f• Бонус к мечам: §a-35% перезарядки §fспособностей [ПКМ]",
                "§f• Способность меча §dлечит обоих игроков на 2 сердца",
                "§aНажмите, чтобы выбрать эту роль!"
        ));
        inv.setStack(15, mageIcon);

        // Slot 13: Story Status / Start
        ItemStack storyIcon = new ItemStack(Items.NETHER_STAR);
        if (!storyStarted) {
            storyIcon.setCustomName(Text.literal("§a§l▶ Запустить Сюжет (/st)"));
            setItemLore(storyIcon, List.of(
                    "§7Нажмите здесь или введите §e/st§7,",
                    "§7чтобы призвать §6Кузнеца Аркадия §7и начать",
                    "§7приключение Димы и Ульяны!"
            ));
        } else if (currentStage <= 8) {
            StageData sd = getStageData(currentStage);
            storyIcon.setCustomName(Text.literal("§6§l✦ " + sd.title() + " §e[" + currentStage + "/8]"));
            List<String> lines = new ArrayList<>();
            lines.add("§7Награда главы: " + sd.swordName());
            lines.add("§7Босс главы: §c" + sd.bossName() + " (" + (int) sd.bossMaxHp() + " HP)");
            lines.add("");
            if (!swordCraftedForStage) {
                lines.add("§eНужно сбросить Кузнецу Аркадию:");
                for (Requirement r : sd.requirements()) {
                    int got = deliveredItems.getOrDefault(r.key(), 0);
                    lines.add(" §f- " + r.displayName() + ": §a" + got + " / " + r.requiredCount());
                }
            } else {
                lines.add("§a✔ Меч выкован! Победите босса: §c" + sd.bossName());
            }
            setItemLore(storyIcon, lines);
        } else {
            storyIcon.setCustomName(Text.literal("§6§l★ СЮЖЕТ ПРОЙДЕН НА 100%! ★"));
            setItemLore(storyIcon, List.of("§aДима и Ульяна спасли мир от Архонта Малкора!"));
        }
        inv.setStack(13, storyIcon);

        // Slot 21: Teleport Blacksmith
        ItemStack smithIcon = new ItemStack(Items.ANVIL);
        smithIcon.setCustomName(Text.literal("§e§l⚒ Позвать Кузнеца Аркадия к себе"));
        setItemLore(smithIcon, List.of(
                "§7Если вы убежали далеко от кузнеца,",
                "§7нажмите сюда, чтобы телепортировать его к вам!"
        ));
        inv.setStack(21, smithIcon);

        // Slot 23: Re-summon Boss
        ItemStack bossIcon = new ItemStack(Items.DRAGON_HEAD);
        bossIcon.setCustomName(Text.literal("§c§l☠ Призвать / Перепризвать Босса главы"));
        setItemLore(bossIcon, List.of(
                "§7Доступно после получения меча текущей главы.",
                "§7Используйте, если босс пропал или вы готовы к реваншу!"
        ));
        inv.setStack(23, bossIcon);
    }

    private static void setItemLore(ItemStack stack, List<String> lines) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        for (String line : lines) {
            loreList.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        }
        display.put("Lore", loreList);
    }

    public void syncHudToAll(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        boolean synergyActive = false;
        if (players.size() >= 2) {
            boolean w = false, m = false;
            for (ServerPlayerEntity p : players) {
                String r = getPlayerRole(p.getUuid());
                if ("WARRIOR".equals(r)) w = true;
                if ("MAGE".equals(r)) m = true;
            }
            synergyActive = w && m;
        }

        for (ServerPlayerEntity player : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(storyStarted);
            buf.writeInt(currentStage);
            buf.writeBoolean(swordCraftedForStage);
            buf.writeString(getRoleDisplayName(getPlayerRole(player.getUuid())));
            buf.writeBoolean(synergyActive);

            List<String> lines = buildHudLines();
            buf.writeInt(lines.size());
            for (String line : lines) {
                buf.writeString(line);
            }
            ServerPlayNetworking.send(player, EightBladesMod.HUD_SYNC_ID, buf);
        }
    }

    private List<String> buildHudLines() {
        List<String> lines = new ArrayList<>();
        if (!storyStarted) {
            lines.add("§e1. Откройте Компас (ПКМ) и выберите Роль");
            lines.add("§a2. Введите команду §f/st §aдля старта сюжета");
            return lines;
        }
        if (currentStage > 8) {
            lines.add("§6★ ФИНАЛ ПРОЙДЕН! ★");
            lines.add("§aДима и Ульяна победили всех 8 боссов!");
            return lines;
        }

        StageData stage = getStageData(currentStage);
        lines.add("§6" + stage.title());
        lines.add("§7Клинок: " + stage.swordName());
        if (!swordCraftedForStage) {
            lines.add("§eСбросьте (Q) Кузнецу Аркадию:");
            for (Requirement req : stage.requirements()) {
                int got = deliveredItems.getOrDefault(req.key(), 0);
                boolean done = got >= req.requiredCount();
                String prefix = done ? "§a ✔ " : "§f • ";
                String countColor = done ? "§a" : "§e";
                lines.add(prefix + req.displayName() + ": " + countColor + got + "/" + req.requiredCount());
            }
        } else {
            lines.add("§a✔ Меч получен обоими игроками!");
            if (bossSpawnCountdown > 0) {
                lines.add("§c⚔ Босс появится через: §e" + (bossSpawnCountdown / 20 + 1) + " сек.");
            } else {
                lines.add("§c⚔ Победите босса: §f" + stage.bossName());
                lines.add("§7(ПКМ Компасом, если нужно перепризвать)");
            }
        }
        return lines;
    }

    public static void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("storyStarted", storyStarted);
        nbt.putInt("currentStage", currentStage);
        nbt.putBoolean("swordCraftedForStage", swordCraftedForStage);
        if (blacksmithUuid != null) nbt.putUuid("blacksmithUuid", blacksmithUuid);
        if (activeBossUuid != null) nbt.putUuid("activeBossUuid", activeBossUuid);

        NbtCompound deliveredNbt = new NbtCompound();
        for (Map.Entry<String, Integer> e : deliveredItems.entrySet()) {
            deliveredNbt.putInt(e.getKey(), e.getValue());
        }
        nbt.put("deliveredItems", deliveredNbt);

        NbtList rolesList = new NbtList();
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) {
            NbtCompound r = new NbtCompound();
            r.putUuid("uuid", e.getKey());
            r.putString("role", e.getValue());
            rolesList.add(r);
        }
        nbt.put("playerRoles", rolesList);
        return nbt;
    }

    public static StoryManager fromNbt(NbtCompound nbt) {
        StoryManager m = new StoryManager();
        m.storyStarted = nbt.getBoolean("storyStarted");
        m.currentStage = Math.max(1, nbt.getInt("currentStage"));
        m.swordCraftedForStage = nbt.getBoolean("swordCraftedForStage");
        if (nbt.containsUuid("blacksmithUuid")) m.blacksmithUuid = nbt.getUuid("blacksmithUuid");
        if (nbt.containsUuid("activeBossUuid")) m.activeBossUuid = nbt.getUuid("activeBossUuid");

        NbtCompound deliveredNbt = nbt.getCompound("deliveredItems");
        for (String key : deliveredNbt.getKeys()) {
            m.deliveredItems.put(key, deliveredNbt.getInt(key));
        }

        NbtList rolesList = nbt.getList("playerRoles", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rolesList.size(); i++) {
            NbtCompound r = rolesList.getCompound(i);
            m.playerRoles.put(r.getUuid("uuid"), r.getString("role"));
        }
        return m;
    }
}
