package com.dimaulyana.eightblades;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

public class EightBladesMod implements ModInitializer {
    public static final String MOD_ID = "eightblades";
    public static final Identifier HUD_SYNC_ID = new Identifier(MOD_ID, "hud_sync");

    public static final Item MUD_SWORD = registerSword("mud_sword", 1, 0, -2.4f,
            "Грязевой Взрыв", "Отбрасывает врагов комьями болотной грязи и замедляет их.", 90);

    public static final Item CACTUS_SWORD = registerSword("cactus_sword", 2, 2, -2.3f,
            "Веер Игл", "Выпускает кольцо острых шипов вокруг героя и включает Регенерацию.", 100);

    public static final Item VENOM_SWORD = registerSword("venom_sword", 3, 3, -2.2f,
            "Ядовитый Рывок", "Стремительный бросок вперед с отравлением и паутинным захватом.", 100);

    public static final Item THUNDER_SWORD = registerSword("thunder_sword", 4, 5, -2.2f,
            "Цепная Молния", "Поражает до 5 врагов рядом настоящими разрядами молнии.", 110);

    public static final Item FROST_SWORD = registerSword("frost_sword", 5, 6, -2.1f,
            "Абсолютный Ноль", "Замораживает врагов вокруг и укрывает героя Ледяным Щитом.", 120);

    public static final Item PHOENIX_SWORD = registerSword("phoenix_sword", 6, 8, -2.1f,
            "Взрыв Сверхновой", "Поджигает врагов волной огня, лечит дуэт и дает Огнестойкость.", 120);

    public static final Item ASTRAL_SWORD = registerSword("astral_sword", 7, 11, -2.0f,
            "Астральная Сингулярность", "Стягивает всех врагов в гравитационную воронку и подбрасывает в воздух.", 130);

    public static final Item ECLIPSE_SWORD = registerSword("eclipse_sword", 8, 15, -1.9f,
            "Катаклизм Затмения", "Обрушивает звуковой удар, молнии Затмения и усиливает обоих игроков!", 140);

    public static final ItemGroup EIGHT_BLADES_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(MOD_ID, "main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ECLIPSE_SWORD))
                    .displayName(Text.translatable("itemGroup.eightblades.main"))
                    .entries((context, entries) -> {
                        entries.add(MUD_SWORD);
                        entries.add(CACTUS_SWORD);
                        entries.add(VENOM_SWORD);
                        entries.add(THUNDER_SWORD);
                        entries.add(FROST_SWORD);
                        entries.add(PHOENIX_SWORD);
                        entries.add(ASTRAL_SWORD);
                        entries.add(ECLIPSE_SWORD);
                    })
                    .build()
    );

    private static Item registerSword(String name, int tier, int bonusDamage, float speed, String abilityName, String abilityDesc, int cooldown) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(MOD_ID, name),
                new MagicSwordItem(tier, bonusDamage, speed, abilityName, abilityDesc, cooldown, new Item.Settings().maxCount(1).fireproof())
        );
    }

    @Override
    public void onInitialize() {
        // 1. Register /st commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("st")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).startStory(player);
                        return 1;
                    })
                    .then(CommandManager.literal("menu").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).openCompassMenu(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("npc").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).spawnOrTeleportBlacksmith(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("boss").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager sm = StoryManager.get(player.getServer());
                        if (sm.isStoryStarted() && sm.isSwordCraftedForStage() && sm.getCurrentStage() <= 8) {
                            sm.spawnCurrentStageBoss(player.getServerWorld(), player.getBlockPos());
                        } else {
                            player.sendMessage(Text.literal("§cСначала сдайте ресурсы Кузнецу Аркадию для текущего меча!"), false);
                        }
                        return 1;
                    }))
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).resetStory(player);
                        return 1;
                    }))
            );
        });

        // 2. Player Join Event: Give Compass & sync HUD
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            StoryManager sm = StoryManager.get(server);
            sm.giveCompassIfNeeded(player);
            player.sendMessage(Text.literal("§6✦ [Легенда Восьми Клинков: Дима и Ульяна] §fНажмите §eПКМ Компасом §fдля выбора Роли и введите §a/st §fдля старта сюжета!"), false);
            sm.syncHudToAll(server);
        });

        // 3. Right-click Compass -> Open Role & Story Menu
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.COMPASS)) {
                if (!world.isClient() && player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
                    StoryManager.get(sp.getServer()).openCompassMenu(sp);
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // 4. Right-click Blacksmith -> Submit held item or Open Menu
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand == Hand.MAIN_HAND && StoryManager.isBlacksmith(entity)) {
                if (!world.isClient() && player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
                    StoryManager sm = StoryManager.get(sp.getServer());
                    ItemStack held = sp.getMainHandStack();
                    if (sm.trySubmitStack(sp.getServer(), held)) {
                        ServerWorld sw = sp.getServerWorld();
                        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.2, entity.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                        sw.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                    } else {
                        sm.openCompassMenu(sp);
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // 5. Block vanilla swords & protect Blacksmith
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (StoryManager.isBlacksmith(entity)) {
                return ActionResult.FAIL;
            }
            ItemStack weapon = player.getMainHandStack();
            if (StoryManager.isBannedVanillaSword(weapon.getItem())) {
                if (!world.isClient()) {
                    weapon.setCount(0);
                    player.playSound(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    player.sendMessage(Text.literal("§c[Проклятие Печати] §7Обычные мечи запрещены! Используйте только Магические Клинки Аркадия."), true);
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 6. Server Tick Event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StoryManager.get(server).tick(server);
        });
    }
}
