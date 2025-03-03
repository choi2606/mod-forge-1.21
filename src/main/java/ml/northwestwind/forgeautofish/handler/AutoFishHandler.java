package ml.northwestwind.forgeautofish.handler;

import com.google.common.collect.Lists;
import ml.northwestwind.forgeautofish.AutoFish;
import ml.northwestwind.forgeautofish.config.Config;
import ml.northwestwind.forgeautofish.config.gui.SettingsScreen;
import ml.northwestwind.forgeautofish.keybind.KeyBinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = AutoFish.MODID, value = Dist.CLIENT)
public class AutoFishHandler {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static boolean autofish = Config.AUTO_FISH.get(), rodprotect = Config.ROD_PROTECT.get(), autoreplace = Config.AUTO_REPLACE.get(), itemfilter = Config.ALL_FILTERS.get();
    public static long recastDelay = Config.RECAST_DELAY.get(), reelInDelay = Config.REEL_IN_DELAY.get(), throwDelay = Config.THROW_DELAY.get(), checkInterval = Config.CHECK_INTERVAL.get();
    private static final List<Item> shouldDrop = Lists.newArrayList();
    private static boolean processingDrop, pendingReelIn, pendingRecast, lastTickFishing, afterDrop;
    private static int dropCd;
    private static long tick, checkTick;
    private static List<ItemStack> itemsBeforeFished;
    private static ItemStack rodStack;

    private static boolean toggleSkillAgility = false;

    private static boolean autoSetItemOffHand = false;

    private static boolean autoRun = false;

    private static boolean jumps = false;

    public static boolean isMainAcc = false;

    public static boolean getTypeItem = false;

    private static long lastFixTime = 0; // Thời gian gọi lần cuối


    private static double xOriginal;
    private static double yOriginal;
    private static double zOriginal;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key e) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (KeyBinds.autofish.consumeClick()) {
            Config.setAutoFish(!autofish);
            if (player != null) player.displayClientMessage(getText("forgeautofish", autofish), true);
        } else if (KeyBinds.rodprotect.consumeClick()) {
            Config.setRodProtect(!rodprotect);
            jumps = !jumps;
            if (player != null) player.displayClientMessage(getText("rodprotect", rodprotect), true);
        } else if (KeyBinds.autoreplace.consumeClick()) {
            Config.setAutoReplace(!autoreplace);
            toggleSkillAgility = !toggleSkillAgility;
            if (toggleSkillAgility) {
                autoRun = true;
            }
            if (player != null) {
                player.displayClientMessage(getText("autoreplace", autoreplace), true);
                player.sendSystemMessage(Component.literal("Đang " + autoRun + " auto skill nhanh nhẹn"));
            }

        } else if (KeyBinds.itemfilter.consumeClick()) {
            Config.enableFilter(!itemfilter);
            autoSetItemOffHand = !autoSetItemOffHand;
            getTypeItem = !getTypeItem;
            if (player != null) {
                player.displayClientMessage(getText("itemfilter", itemfilter), true);
                player.sendSystemMessage(Component.literal("Đang " + autoSetItemOffHand + " auto set thức ăn tay trái"));
            }
        } else if (KeyBinds.settings.consumeClick())
            minecraft.setScreen(new SettingsScreen());
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent e) {
        if (e.side != LogicalSide.CLIENT || !e.phase.equals(TickEvent.Phase.START)) return;
        Player player = e.player;
        try {
            autoSetItem(player);
            if (toggleSkillAgility) {
                autoDrop(player);
            }

            if (jumps) {
                player.moveTo(player.getX(), player.getY() + 20f, player.getZ() + 1f);
                jumps = false;
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
        autoFix(player);
        if (!player.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (checkTick > 0) checkTick--;
        else {
            checkTick = checkInterval;
            if (!pendingRecast) {
                if (player.fishing == null) recast(player);
                else if (player.fishing.getDeltaMovement().lengthSqr() == 0) pendingReelIn = true;
            }
        }
        if (lastTickFishing && player.fishing == null)
            itemsBeforeFished = Lists.newArrayList(player.getInventory().items);
        lastTickFishing = player.fishing != null;
        if (afterDrop) {
            if (tick == 0 && rodStack != null) {
                player.getInventory().setPickedItem(rodStack);
                rodStack = null;
            }
            tick++;
            if (tick > 2) {
                afterDrop = false;
                tick = 0;
            }
            return;
        }
        if (pendingReelIn) {
            tick++;
            if (tick >= reelInDelay) {
                reelIn(player);
                tick = 0;
                pendingReelIn = false;
            }
            return;
        }
        if (processingDrop) {
            if (dropCd > 0) dropCd--;
            dropItem(player);
            if (shouldDrop.size() <= 0) {
                processingDrop = false;
                afterDrop = true;
            }
            return;
        }
        if (pendingRecast) {
            tick++;
            if (tick >= recastDelay) {
                checkItem(player);
                if (processingDrop) {
                    tick = 0;
                    return;
                }
                if (player.fishing == null) {
                    recast(player);
                    tick = 0;
                }
                pendingRecast = false;
            }
            return;
        }
        if (!autofish || player.fishing == null) return;
        Vec3 vector = player.fishing.getDeltaMovement();
        double x = vector.x();
        double y = vector.y();
        double z = vector.z();
        if (y < -0.075 && !player.level().getFluidState(player.fishing.blockPosition()).isEmpty() && x == 0 && z == 0)
            pendingReelIn = true;
    }

    private static void reelIn(Player player) {
        if (!autofish) return;
        InteractionHand hand = findHandOfRod(player);
        if (hand == null) return;
        click(player, hand, Minecraft.getInstance().gameMode);
        ItemStack fishingRod = player.getItemInHand(hand);
        int breakValue = fishingRod.getDamageValue();
        if (isMainAcc) {
            autoFix(fishingRod, breakValue);
        }

        // case can't fix item
        if (breakValue >= 62) {
            // stop autofish
            autofish = false;
        }

        boolean needReplace = false;
        if (fishingRod.getMaxDamage() - fishingRod.getDamageValue() < 2)
            if (autoreplace) needReplace = true;
            else return;
        else if (fishingRod.getMaxDamage() - fishingRod.getDamageValue() < 3 && !player.isCreative() && rodprotect)
            if (autoreplace) needReplace = true;
            else {
                autofish = false;
                player.displayClientMessage(getText("forgeautofish", autofish), true);
                return;
            }
        if (needReplace) {
            AutoFish.LOGGER.info("Fishing rod broke. Finding replacement...");
            boolean found = false;
            for (int i = 0; i < 9; i++) {
                if (i == player.getInventory().selected) continue;
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof FishingRodItem) {
                    if (rodprotect && stack.getMaxDamage() - stack.getDamageValue() < 2) continue;
                    AutoFish.LOGGER.info("Found fishing rod for replacement");
                    player.getInventory().selected = i;
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }
        pendingRecast = true;
    }

    public static void recast(Player player) {
        if (!autofish) return;
        InteractionHand hand = findHandOfRod(player);
        if (hand == null) return;
        ItemStack fishingRod = player.getItemInHand(hand);
        if (fishingRod.isEmpty()) return;
        click(player, hand, Minecraft.getInstance().gameMode);
    }

    private static void checkItem(Player player) {
        if (itemsBeforeFished != null) {
            List<ItemStack> items = player.getInventory().items;
            for (String name : Config.FILTER.get()) {
                ResourceLocation rl = ResourceLocation.parse(name);
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) continue;
                int newCount = items.stream().filter(stack -> stack.getItem().equals(item)).mapToInt(ItemStack::getCount).reduce(Integer::sum).orElse(0);
                int oldCount = itemsBeforeFished.stream().filter(stack -> stack.getItem().equals(item)).mapToInt(ItemStack::getCount).reduce(Integer::sum).orElse(0);
                int diff = newCount - oldCount;
                for (int ii = 0; ii < diff; ii++) shouldDrop.add(item);
            }
            itemsBeforeFished = null;
            if (shouldDrop.size() > 0) {
                processingDrop = true;
                rodStack = player.getMainHandItem();
            }
        }
    }

    private static void dropItem(Player player) {
        if (dropCd == 4 || dropCd == 2 || dropCd == 1) return;
        Item item = shouldDrop.get(0);
        if (dropCd == 3) {
            ((LocalPlayer) player).drop(false);
            shouldDrop.remove(item);
            return;
        }
        for (int ii = 0; ii < 9; ii++) {
            final ItemStack stack = player.getInventory().items.get(ii);
            if (!stack.getItem().equals(item)) continue;
            player.getInventory().setPickedItem(stack);
            dropCd = 5;
            return;
        }
        // if item cannot be found in hotbar, just ignore it
        shouldDrop.remove(item);
    }

    private static void click(Player player, InteractionHand hand, @Nullable MultiPlayerGameMode controller) {
        if (controller == null) return;
        controller.useItem(player, hand);
    }

    @Nullable
    private static InteractionHand findHandOfRod(Player player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) return InteractionHand.MAIN_HAND;
        else if (player.getOffhandItem().getItem() instanceof FishingRodItem) return InteractionHand.OFF_HAND;
        else return null;
    }

    private static Component getText(String key, boolean bool) {
        return AutoFish.getTranslatableComponent("toggle." + key, AutoFish.getTranslatableComponent("toggle.enable." + bool).withStyle(bool ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    // handle auto fix item
    public static void autoFix(ItemStack itemStack, int breakValue) {
        // check breakValue
        if (breakValue >= 60)
            // Send command to server
            Minecraft.getInstance().player.connection.sendCommand("fix");
    }

    public static void autoSetItem(Player player) {
        if (autoSetItemOffHand) {
            if (player.getOffhandItem().isEmpty()) {
                int indexItemFood = getIndexItem(player);
                if (indexItemFood != -1) {
                    Minecraft mc = Minecraft.getInstance();
                    InventoryMenu inventoryMenu = player.inventoryMenu;
                    mc.gameMode.handleInventoryMouseClick(inventoryMenu.containerId, indexItemFood, 40, ClickType.SWAP, player);
                }
            }
        }
    }

    public static int getIndexItem(Player player) {
        Inventory inventory = player.getInventory();
        NonNullList<ItemStack> itemsInInv = inventory.items;
        for (int i = 0; i < inventory.items.size(); i++) {
            if (!itemsInInv.get(i).isEmpty()) {
                ItemStack itemStack = itemsInInv.get(i);
                FoodProperties food = itemStack.get(DataComponents.FOOD);
                if (food != null && !itemStack.getDescriptionId().contains("item.minecraft.rotten_flesh")) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void autoDrop(Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double elevation = 20.0d;
        final int[] truongHop = {0};

        if (autoRun && player.getHealth() >= 18.0f) {
            //player.sendSystemMessage(Component.literal("Co chay vao truong hop" + truongHop[0] + " " + x + y + z));
            autoRun = false;
            player.moveTo(x, y + elevation, z - 1.0d);

            scheduler.schedule(() -> {
                Minecraft.getInstance().execute(() -> {
                    double oldX = player.getX();
                    double oldY = player.getX();
                    double oldZ = player.getX();

                    Minecraft.getInstance().player.connection.sendCommand("home nn");
//                    scheduler.schedule(() -> {
//                        Minecraft.getInstance().execute(() -> {
//                            if (((int) player.getX()) != ((int) oldX) || ((int) player.getY()) != ((int) oldY) || ((int) player.getZ()) != ((int) oldZ)) {
//                                player.sendSystemMessage(Component.literal("Lệnh home nn không thành công, đang thử lại..."));
//                                Minecraft.getInstance().player.connection.sendCommand("home nn");
//                                return;
//                            }
//                            player.sendSystemMessage(Component.literal("Trở về vị trí thành công!"));
//
//                        });
//                    }, 17, TimeUnit.SECONDS);
                });
            }, 8, TimeUnit.SECONDS);

            truongHop[0] += 1;
        }

        scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                if (truongHop[0] == 1 && (int) player.getX() == (int) x && (int) player.getY() == (int) y && (int) player.getZ() == (int) z) {
                    //player.sendSystemMessage(Component.literal("Co chay vao truong hop " + truongHop[0] + " vi tri ban dau"));
                    autoRun = true;
                    truongHop[0] = 0;
                }
            });
        }, 10, TimeUnit.SECONDS);

        scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                if (truongHop[0] != 0 && !autoRun && (int) player.getX() == (int) x && (int) player.getY() == (int) y && (int) player.getZ() == (int) z) {
                    player.sendSystemMessage(Component.literal("Đã set true autoRun"));
                    autoRun = true;
                    truongHop[0] = 0;
                }
            });
        }, 20, TimeUnit.SECONDS);
    }

    public static void autoFix(Player player) {
        long delay = 10000;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastFixTime < delay) return;

        lastFixTime = currentTime;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack fishingRod = player.getItemInHand(hand);
        int breakValue = fishingRod.getDamageValue();
        if (breakValue >= 50) {
            Minecraft.getInstance().player.connection.sendCommand("fix");
        }
    }
}