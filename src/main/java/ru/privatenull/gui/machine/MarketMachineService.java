package ru.privatenull.gui.machine;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.gui.MarketGuiLayout;
import ru.privatenull.pnlibrary.gui.*;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.*;
import java.util.function.Consumer;

public final class MarketMachineService implements Listener {
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_SIZE = 11;
    private static final int SLOT_ICONS = 13;
    private static final int SLOT_LAYOUT = 15;
    private static final int SLOT_PREVIEW = 31;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_ROLE_PREVIEW = 4;
    private static final int SLOT_REPLACE_ICON = 40;
    private static final int SLOT_BACK = 49;
    private static final int[] ROLE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 30, 31, 32, 33};
    private static final int[] ICON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 30, 32};
    private static final List<Role> ROLES = List.of(Role.values());
    private static final List<IconOption> ICONS = List.of(
            new IconOption("actions.back", "Кнопка возврата", Material.PLAYER_HEAD),
            new IconOption("actions.buy", "Кнопка покупки", Material.LIME_WOOL),
            new IconOption("actions.no-money", "Недостаточно денег", Material.BARRIER),
            new IconOption("search", "Возврат из поиска", Material.PLAYER_HEAD),
            new IconOption("auction-switch.regular", "Обычный аукцион", Material.NETHER_STAR),
            new IconOption("auction-switch.donate", "Донат-аукцион", Material.GOLD_INGOT),
            new IconOption("my-items.regular", "Мои товары", Material.BARREL),
            new IconOption("my-items.donate", "Мои донат-товары", Material.BARREL),
            new IconOption("favorites.menu", "Избранное", Material.ENDER_EYE),
            new IconOption("favorites.help", "Помощь избранного", Material.BOOK),
            new IconOption("pagination.previous", "Предыдущая страница", Material.PLAYER_HEAD),
            new IconOption("pagination.next", "Следующая страница", Material.PLAYER_HEAD),
            new IconOption("filter.category", "Категории", Material.COMPASS),
            new IconOption("filter.sort", "Сортировка", Material.HOPPER),
            new IconOption("auction.decor.black", "Чёрный декор", Material.BLACK_STAINED_GLASS_PANE),
            new IconOption("auction.decor.orange", "Оранжевый декор", Material.ORANGE_STAINED_GLASS_PANE));

    private final PnMarketPlugin plugin;
    private final GuiOpenAnimationService animations;
    private final GuiUpdateService updates;

    public MarketMachineService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.updates = plugin.guiUpdates();
        this.animations = new GuiOpenAnimationService(plugin, updates);
    }

    public void shutdown() {
        animations.shutdown();
    }

    public void open(Player player) {
        show(player, Screen.MAIN, -1, 54, "&0Machine • pnMarket", inventory -> {
            fill(inventory);
            int rows = MarketGuiLayout.load(gui()).size() / 9;
            inventory.setItem(SLOT_HEADER, button(Material.NETHER_STAR, "&#429F91Machine • Интерфейс", List.of(
                    "", "&#9EFC65 «Описание»", " &7- &fПолная настройка главного аукциона.",
                    " &7- &fРазмер, разметка, кнопки и декор", " &7- &fприменяются сразу после сохранения.", "")));
            inventory.setItem(SLOT_SIZE, button(Material.COMPARATOR, "&#429F91Размер интерфейса", List.of(
                    "", "&#65D1FC «Сейчас»", " &7- &fСтрок: &#FCA865" + rows,
                    " &7- &fСлотов: &#FCA865" + rows * 9, "", "&#FCA965 «Управление»",
                    " &7- &fЛКМ — добавить строку", " &7- &fПКМ — убрать строку",
                    " &7- &fShift — изменить на две строки", "")));
            inventory.setItem(SLOT_ICONS, button(Material.PAINTING, "&#429F91Иконки интерфейса", List.of(
                    "", "&#9EFC65 «Описание»", " &7- &fВсе изменяемые кнопки в одном месте.",
                    " &7- &fМатериал заменяется предметом с курсора.",
                    " &7- &fBase64 остаётся доступен через gui.yml.", "",
                    "&#FCA965 «Управление»", " &7- &fЛКМ — открыть редактор", "")));
            inventory.setItem(SLOT_LAYOUT, button(Material.ITEM_FRAME, "&#429F91Разметка интерфейса", List.of(
                    "", "&#9EFC65 «Описание»", " &7- &fОткройте визуальную сетку аукциона.",
                    " &7- &fКаждый слот получает отдельную роль.", " &7- &fПустая роль действительно оставляет AIR.",
                    "", "&#FCA965 «Управление»", " &7- &fЛКМ — открыть редактор", "")));
            inventory.setItem(SLOT_PREVIEW, button(Material.ENDER_EYE, "&#429F91Предпросмотр аукциона", List.of(
                    "", " &7- &fПоказывает интерфейс точно так же,", " &7- &fкак его увидит обычный игрок.",
                    "", "&#FCA965 «Управление»", " &7- &fЛКМ — посмотреть", "")));
            inventory.setItem(SLOT_CLOSE, gui().item("actions.back", Material.PLAYER_HEAD, Map.of()));
        });
    }

    private void openLayout(Player player) {
        MarketGuiLayout layout = MarketGuiLayout.load(gui());
        show(player, Screen.LAYOUT, -1, layout.size(), "&0Разметка • Q назад", inventory -> {
            for (int slot = 0; slot < layout.size(); slot++) {
                Role role = Role.byKey(layout.role(slot));
                inventory.setItem(slot, slotItem(layout, slot, role));
            }
        });
    }

    private void openRolePicker(Player player, int targetSlot) {
        MarketGuiLayout layout = MarketGuiLayout.load(gui());
        Role current = Role.byKey(layout.role(targetSlot));
        show(player, Screen.ROLE, targetSlot, 54,
                "&0Слот " + targetSlot + " • " + current.title, inventory -> {
                    fill(inventory);
                    inventory.setItem(SLOT_ROLE_PREVIEW, slotItem(layout, targetSlot, current));
                    for (int index = 0; index < ROLES.size(); index++) {
                        inventory.setItem(ROLE_SLOTS[index], roleButton(current, ROLES.get(index)));
                    }
                    inventory.setItem(SLOT_REPLACE_ICON, button(Material.ITEM_FRAME,
                            "&#65D1FCЗаменить иконку", List.of(
                                    "", " &7- &fПоложите предмет на курсор и нажмите.",
                                    " &7- &fМеняется материал текущей роли.",
                                    " &7- &fBase64 задаётся в gui.yml тем же блоком.", "")));
                    inventory.setItem(SLOT_BACK, gui().item("actions.back", Material.PLAYER_HEAD, Map.of()));
                });
    }

    private void openIcons(Player player) {
        show(player, Screen.ICONS, -1, 54, "&0Machine • Иконки", inventory -> {
            fill(inventory);
            for (int index = 0; index < ICONS.size(); index++) {
                IconOption option = ICONS.get(index);
                ItemStack item = gui().item(option.path, option.fallback, Map.of(
                        "root", "/ah", "page", 1, "sort", "Новые", "category", "Все"));
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(ColorUtil.component("&#429F91" + option.title)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.setLore(List.of("", ColorUtil.colorize(" &7- &fПуть: &#FCA865" + option.path),
                            "", ColorUtil.colorize("&#FCA965 «Управление»"),
                            ColorUtil.colorize(" &7- &fПредмет на курсоре — заменить material"),
                            ColorUtil.colorize(" &7- &fQ — вернуться назад"), ""));
                    hide(meta);
                    item.setItemMeta(meta);
                }
                inventory.setItem(ICON_SLOTS[index], item);
            }
            inventory.setItem(SLOT_BACK, gui().item("actions.back", Material.PLAYER_HEAD, Map.of()));
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineView view)
                || !(event.getWhoClicked() instanceof Player player)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= topSize) {
            if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY
                    || event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (animations.isAnimating(player)) return;
        if (!player.hasPermission("pnmarket.admin") || event.getRawSlot() < 0) return;
        plugin.withGuiTransition(player, event.getRawSlot(), () -> {
            switch (view.screen) {
                case MAIN -> handleMain(player, event, event.getRawSlot());
                case LAYOUT -> handleLayout(player, event, event.getRawSlot());
                case ROLE -> handleRole(player, event, view.selection, event.getRawSlot());
                case ICONS -> handleIcons(player, event, event.getRawSlot());
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineView)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) event.setCancelled(true);
    }

    private void handleMain(Player player, InventoryClickEvent event, int slot) {
        if (slot == SLOT_SIZE) {
            int step = event.isShiftClick() ? 2 : 1;
            int rows = MarketGuiLayout.load(gui()).size() / 9;
            int next = Math.max(1, Math.min(6, rows + (event.isRightClick() ? -step : step)));
            resizeLayout(rows, next);
            save();
            open(player);
        } else if (slot == SLOT_ICONS) {
            openIcons(player);
        } else if (slot == SLOT_LAYOUT) {
            openLayout(player);
        } else if (slot == SLOT_PREVIEW) {
            plugin.openAuction(player);
        } else if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleIcons(Player player, InventoryClickEvent event, int clickedSlot) {
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP
                || clickedSlot == SLOT_BACK) {
            open(player);
            return;
        }
        int index = -1;
        for (int candidate = 0; candidate < ICON_SLOTS.length; candidate++) {
            if (ICON_SLOTS[candidate] == clickedSlot) {
                index = candidate;
                break;
            }
        }
        if (index < 0) return;
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Положите нужный предмет на курсор."));
            plugin.playSound(player, "machine.error");
            return;
        }
        IconOption option = ICONS.get(index);
        gui().set(option.path + ".material", cursor.getType().name());
        gui().set(option.path + ".base64", null);
        save();
        openIcons(player);
    }

    private void handleLayout(Player player, InventoryClickEvent event, int slot) {
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            open(player);
            return;
        }
        if (slot >= 0 && slot < MarketGuiLayout.load(gui()).size()) openRolePicker(player, slot);
    }

    private void handleRole(Player player, InventoryClickEvent event, int targetSlot, int clickedSlot) {
        if (clickedSlot == SLOT_BACK) {
            openLayout(player);
            return;
        }
        MarketGuiLayout layout = MarketGuiLayout.load(gui());
        if (targetSlot < 0 || targetSlot >= layout.size()) {
            openLayout(player);
            return;
        }
        Role current = Role.byKey(layout.role(targetSlot));
        if (clickedSlot == SLOT_REPLACE_ICON) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir() || current.iconPath == null) {
                player.sendMessage(ColorUtil.colorize("&#FC7165Для этой роли нужна настраиваемая иконка на курсоре."));
                plugin.playSound(player, "machine.error");
                return;
            }
            replaceMaterial(current, cursor.getType());
            save();
            openRolePicker(player, targetSlot);
            return;
        }
        Role selected = roleAt(clickedSlot);
        if (selected == null || selected == current) return;
        assign(targetSlot, selected);
        save();
        openLayout(player);
        plugin.playSound(player, "machine.select");
    }

    private void assign(int slot, Role role) {
        for (Role candidate : ROLES) {
            if (candidate.listPath != null) {
                List<Integer> slots = new ArrayList<>(gui().configuration().getIntegerList(candidate.listPath));
                slots.removeIf(value -> value == slot);
                gui().set(candidate.listPath, slots);
            } else if (candidate.slotPath != null && gui().configuration().getInt(candidate.slotPath, -1) == slot) {
                gui().set(candidate.slotPath, -1);
            }
        }
        if (role.listPath != null) {
            List<Integer> slots = new ArrayList<>(gui().configuration().getIntegerList(role.listPath));
            slots.add(slot);
            gui().set(role.listPath, slots.stream().distinct().sorted().toList());
        } else if (role.slotPath != null) {
            gui().set(role.slotPath, slot);
        }
    }

    private void replaceMaterial(Role role, Material material) {
        for (String path : role.iconPath.split(",")) {
            gui().set(path + ".material", material.name());
            gui().set(path + ".base64", null);
        }
    }

    private void resizeLayout(int oldRows, int newRows) {
        if (oldRows == newRows) return;
        gui().set("auction.layout.rows", newRows);
    }

    private ItemStack slotItem(MarketGuiLayout layout, int slot, Role role) {
        ItemStack source = role.preview(gui());
        if (source == null || source.getType().isAir()) {
            source = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }
        ItemMeta meta = source.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.component(role.color + role.title)
                    .decoration(TextDecoration.ITALIC, false));
            meta.setLore(List.of("", ColorUtil.colorize(" &7- &fСлот: &#FCA865" + slot),
                    ColorUtil.colorize(" &7- &fНазначение: " + role.color + role.title), "",
                    ColorUtil.colorize("&#FCA965 «Управление»"),
                    ColorUtil.colorize(" &7- &fЛКМ — открыть настройку слота"),
                    ColorUtil.colorize(" &7- &fQ — вернуться назад"), ""));
            hide(meta);
            source.setItemMeta(meta);
        }
        return source;
    }

    private ItemStack roleButton(Role current, Role role) {
        boolean selected = current == role;
        Material material = role == Role.EMPTY ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : role.fallback;
        return button(material, (selected ? "&#9EFC65✓ " : role.color) + role.title, List.of(
                "", selected ? " &a✓ Сейчас назначено этому слоту" : " &7- &fЛКМ — назначить",
                role.unique() ? " &7- &fПереносит эту кнопку в выбранный слот." : "", ""));
    }

    private Role roleAt(int slot) {
        for (int index = 0; index < ROLE_SLOTS.length; index++) {
            if (ROLE_SLOTS[index] == slot) return ROLES.get(index);
        }
        return null;
    }

    private void show(Player player, Screen screen, int selection, int size,
                      String title, Consumer<Inventory> renderer) {
        Inventory current = player.getOpenInventory().getTopInventory();
        if (current.getHolder() instanceof MachineView open && open.screen == screen
                && open.selection == selection && current.getSize() == size) {
            animations.cancel(player);
            Inventory rendered = Bukkit.createInventory(new MachineView(screen, selection), size,
                    ColorUtil.colorize(title));
            renderer.accept(rendered);
            for (int slot = 0; slot < size; slot++) {
                if (!Objects.equals(current.getItem(slot), rendered.getItem(slot))) {
                    updates.setTopSlot(player, current, slot, rendered.getItem(slot));
                }
            }
            return;
        }
        MachineView view = new MachineView(screen, selection);
        Inventory inventory = Bukkit.createInventory(view, size, ColorUtil.colorize(title));
        view.inventory = inventory;
        renderer.accept(inventory);
        animations.open(player, inventory, false, plugin.guiAnimationProfile(),
                plugin.guiTransitionOrigin(player));
        plugin.playSound(player, "machine.open");
    }

    private void fill(Inventory inventory) {
        ItemStack pane = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane.clone());
    }

    private void save() {
        if (gui().save()) plugin.reloadGuiRuntime();
    }

    private GuiConfig gui() {
        return plugin.guiConfig();
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.component(name).decoration(TextDecoration.ITALIC, false));
            meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            hide(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void hide(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    }

    private enum Role {
        EMPTY("empty", "Пусто (AIR)", "&8", null, null, null, Material.AIR),
        LISTINGS("listings", "Товары", "&#65D1FC", "auction.layout.listings", null, null, Material.CHEST),
        BLACK("decor.black", "Чёрный декор", "&7", "auction.layout.decor.black", null,
                "auction.decor.black", Material.BLACK_STAINED_GLASS_PANE),
        ORANGE("decor.orange", "Оранжевый декор", "&#FCA865", "auction.layout.decor.orange", null,
                "auction.decor.orange", Material.ORANGE_STAINED_GLASS_PANE),
        SWITCH("switch", "Смена аукциона", "&#9EFC65", null, "auction.layout.switch",
                "auction-switch.regular,auction-switch.donate", Material.NETHER_STAR),
        MY_ITEMS("my-items", "Мои товары", "&#9EFC65", null, "auction.layout.my-items",
                "my-items.regular,my-items.donate", Material.BARREL),
        PREVIOUS("previous", "Предыдущая страница", "&#9EFC65", null, "auction.layout.previous",
                "pagination.previous", Material.ARROW),
        FAVORITES("favorites", "Избранное", "&#9EFC65", null, "auction.layout.favorites",
                "favorites.menu", Material.ENDER_EYE),
        NEXT("next", "Следующая страница", "&#9EFC65", null, "auction.layout.next",
                "pagination.next", Material.ARROW),
        SORT("sort", "Сортировка", "&#9EFC65", null, "auction.layout.sort",
                "filter.sort", Material.HOPPER),
        CATEGORY("category", "Категории", "&#9EFC65", null, "auction.layout.category",
                "filter.category", Material.COMPASS);

        private final String key, title, color, listPath, slotPath, iconPath;
        private final Material fallback;

        Role(String key, String title, String color, String listPath, String slotPath,
             String iconPath, Material fallback) {
            this.key = key;
            this.title = title;
            this.color = color;
            this.listPath = listPath;
            this.slotPath = slotPath;
            this.iconPath = iconPath;
            this.fallback = fallback;
        }

        private boolean unique() {
            return slotPath != null;
        }

        private static Role byKey(String key) {
            return ROLES.stream().filter(role -> role.key.equals(key)).findFirst().orElse(EMPTY);
        }

        private ItemStack preview(GuiConfig gui) {
            if (this == EMPTY) return null;
            if (iconPath == null) return new ItemStack(fallback);
            return gui.item(iconPath.split(",")[0], fallback, Map.of(
                    "root", "/ah", "page", 1, "sort", "Новые", "category", "Все"));
        }
    }

    private record IconOption(String path, String title, Material fallback) {
    }

    private enum Screen { MAIN, LAYOUT, ROLE, ICONS }

    private static final class MachineView implements InventoryHolder {
        private final Screen screen;
        private final int selection;
        private Inventory inventory;

        private MachineView(Screen screen, int selection) {
            this.screen = screen;
            this.selection = selection;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
