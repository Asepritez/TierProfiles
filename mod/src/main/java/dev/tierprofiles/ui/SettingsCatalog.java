package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.Kits;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.Palette;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import dev.tierprofiles.input.Keys;
import dev.tierprofiles.surface.Icons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;

/**
 * Every setting in the mod, declared in one place. Adding a line here puts a setting on
 * its tab and in search, with nothing else to wire up.
 */
final class SettingsCatalog {
    private static final int SLIDER_WIDTH = 200;

    private final Supplier<ConfigFile> staged;
    private final SettingsScreen screen;
    private final List<Setting> all = new ArrayList<>();
    private Surface currentSurface;

    SettingsCatalog(Supplier<ConfigFile> staged, SettingsScreen screen) {
        this.staged = staged;
        this.screen = screen;

        for (Surface surface : Surface.values()) {
            addSurface(surface);
        }
        currentSurface = null;
        addLists();
        addTagStyle();
        addColours();
        addRules();
        addCards();
        addProfile();
        addPosition();
        addKeys();
        addMore();
    }

    List<Setting> all() {
        return all;
    }

    /** One tab's settings, with the Tags tab narrowed to the surface being edited. */
    List<Setting> forTab(SettingsTab tab, Surface surface, boolean advanced) {
        List<Setting> out = new ArrayList<>();
        for (Setting setting : all) {
            boolean otherSurface = setting.surface() != null && setting.surface() != surface;
            if (setting.tab() == tab && setting.advanced() == advanced && !otherSurface) {
                out.add(setting);
            }
        }
        return out;
    }

    private Settings active() {
        return staged.get().active();
    }

    private Global global() {
        return staged.get().global;
    }

    private Cosmetics cosmetics() {
        return global().cosmetics;
    }

    private void add(SettingsTab tab, String path, String label, String hint, boolean advanced, Setting.Control control) {
        all.add(new Setting(tab, path, currentSurface, label, hint, advanced, 1, control));
    }

    private void addTall(SettingsTab tab, String path, String label, String hint, boolean advanced, int rows, Setting.Control control) {
        all.add(new Setting(tab, path, currentSurface, label, hint, advanced, rows, control));
    }

    private <O> void toggle(SettingsTab tab, String path, String label, String hint, boolean advanced,
            Supplier<O> owner, Function<O, Boolean> get, BiConsumer<O, Boolean> set) {
        add(tab, path, label, hint, advanced, (ui, x, y, width) -> {
            O target = owner.get();
            set.accept(target, ui.toggle(x, y, get.apply(target)));
        });
    }

    private <O, E> void choice(SettingsTab tab, String path, String label, String hint, boolean advanced,
            List<E> values, Function<E, String> name, Supplier<O> owner, Function<O, E> get, BiConsumer<O, E> set) {
        add(tab, path, label, hint, advanced, (ui, x, y, width) -> {
            O target = owner.get();
            set.accept(target, ui.segments(x, y, values, get.apply(target), name));
        });
    }

    private <O> void percent(SettingsTab tab, String path, String label, String hint, boolean advanced,
            float min, float max, Supplier<O> owner, Function<O, Float> get, BiConsumer<O, Float> set) {
        Object id = new Object();
        add(tab, path, label, hint, advanced, (ui, x, y, width) -> {
            O target = owner.get();
            float value = get.apply(target);
            set.accept(target, ui.slider(id, x, y, SLIDER_WIDTH, value, min, max, Math.round(value * 100) + "%"));
        });
    }

    private <O> void number(SettingsTab tab, String path, String label, String hint, boolean advanced,
            int min, int max, String unit, Supplier<O> owner, Function<O, Integer> get, BiConsumer<O, Integer> set) {
        Object id = new Object();
        add(tab, path, label, hint, advanced, (ui, x, y, width) -> {
            O target = owner.get();
            int value = get.apply(target);
            set.accept(target, Math.round(ui.slider(id, x, y, SLIDER_WIDTH, value, min, max, value + unit)));
        });
    }

    private static String words(Enum<?> value) {
        return words(value.name());
    }

    private static String words(String id) {
        String lower = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int colorOf(Tier tier) {
        return active().style.color(TierList.MCTIERS, tier);
    }

    private void addSurface(Surface surface) {
        currentSurface = surface;
        String name = surface.displayName();
        String path = "Tags, " + name;
        Supplier<SurfaceSettings> rules = () -> active().surface(surface);
        SettingsTab tab = SettingsTab.TAGS;

        toggle(tab, path, "Show tag", "Turn tags on or off here.", false, rules, s -> s.enabled, (s, v) -> s.enabled = v);
        add(tab, path, "Minimum tier", "Hides tags below this tier. LT3 is recommended for busy lobbies. Everyone showing HT4 tells you nothing.", false, (ui, x, y, width) -> {
            SurfaceSettings target = rules.get();
            Tier picked = ui.tierPicker(x, y, target.minimum(), this::colorOf);
            target.minimumTier = picked == null ? null : picked.label();
        });
        choice(tab, path, "Gamemode", "Highest uses their best kit. Selected uses your kit list. Auto reads your inventory.", false,
                List.of(Filters.KitMode.values()), SettingsCatalog::words, rules, s -> s.kitMode, (s, v) -> s.kitMode = v);
        choice(tab, path, "Tier", "Their rank today, their best ever, or both.", false,
                List.of(SurfaceSettings.TierMode.values()), SettingsCatalog::words, rules, s -> s.tierMode, (s, v) -> s.tierMode = v);

        String advanced = path + ", Advanced";
        toggle(tab, advanced, "Hide retired", "Ignore tiers the player has retired from.", true, rules, s -> s.hideRetired, (s, v) -> s.hideRetired = v);
        toggle(tab, advanced, "Hide unranked", "Show nothing for players with no tiers.", true, rules, s -> s.hideUnranked, (s, v) -> s.hideUnranked = v);
        toggle(tab, advanced, "Hide my own tag", "Leave your own name untagged.", true, rules, s -> s.hideOwnTag, (s, v) -> s.hideOwnTag = v);
        toggle(tab, advanced, "Apply name rules", "Let the Rules tab recolour names here.", true, rules, s -> s.highlightName, (s, v) -> s.highlightName = v);
        toggle(tab, advanced, "Gamemode icon", "Show the kit icon in the tag.", true, rules, s -> s.kitIcon, (s, v) -> s.kitIcon = v);
        toggle(tab, advanced, "List logo", "Show which list the tier came from.", true, rules, s -> s.listLogo, (s, v) -> s.listLogo = v);
        toggle(tab, advanced, "Region", "Show the player's region.", true, rules, s -> s.region, (s, v) -> s.region = v);
        number(tab, advanced, "Tags at once", "How many lists may show a tag together.", true, 1, SurfaceSettings.MAX_TAGS, "", rules, s -> s.maxTags, (s, v) -> s.maxTags = v);
    }

    /** How many slots fit across a control, for rows that wrap onto a second line. */
    private static int perRow(int width) {
        return Math.max(1, width / (Ui.SLOT + 1));
    }

    private static int slotX(int x, int index, int perRow) {
        return x + index % perRow * (Ui.SLOT + 1);
    }

    private static int slotY(int y, int index, int perRow) {
        return y + index / perRow * (Ui.SLOT + 1);
    }

    /** A row of list logos you click to switch off. A list that's off is faded and framed red. */
    private void listSwitches(SettingsTab tab, String path, String label, String hint, boolean advanced,
            Function<TierList, Boolean> isHidden, BiConsumer<TierList, Boolean> setHidden) {
        add(tab, path, label, hint, advanced, (ui, x, y, width) -> {
            for (TierList list : TierList.values()) {
                boolean hidden = isHidden.apply(list);
                if (ui.slot(x, y, false)) {
                    setHidden.accept(list, !hidden);
                }
                dev.tierprofiles.hud.Logos.draw(ui.graphics, list, x + 2, y - 8, 16, hidden ? 0.25f : 1);
                if (hidden) {
                    ui.graphics.outline(x, y - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT, Theme.BAD);
                }
                if (ui.hovered(x, y - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT)) {
                    ui.hint(list.displayName() + (hidden ? ": hidden" : ": shown"), hint);
                }
                x += Ui.SLOT + 1;
            }
        });
    }

    /** Every known kit on two lines. A hidden kit is faded and framed red. */
    private void kitSwitches(SettingsTab tab, String path, String label, String hint, boolean advanced,
            Supplier<java.util.Collection<String>> hidden) {
        addTall(tab, path, label, hint, advanced, 2, (ui, x, y, width) -> {
            java.util.Collection<String> off = hidden.get();
            List<String> kits = Kits.known();
            int across = perRow(width);
            for (int i = 0; i < kits.size(); i++) {
                String kit = kits.get(i);
                int cellX = slotX(x, i, across);
                int cellY = slotY(y, i, across);
                boolean isOff = off.contains(kit);
                if (ui.slot(cellX, cellY, false)) {
                    if (isOff) {
                        off.remove(kit);
                    } else {
                        off.add(kit);
                    }
                }
                dev.tierprofiles.surface.KitIcon.draw(ui.graphics, kit, cellX + 2, cellY - 8, active().style.iconSet, isOff ? 0.25f : 1);
                if (isOff) {
                    ui.graphics.outline(cellX, cellY - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT, Theme.BAD);
                }
                if (ui.hovered(cellX, cellY - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT)) {
                    ui.hint(Kits.displayName(kit) + (isOff ? ": hidden" : ": shown"), hint);
                }
            }
        });
    }

    private void addLists() {
        SettingsTab tab = SettingsTab.LISTS;
        Supplier<Filters> filters = () -> active().filters;
        String path = "Lists";

        listSwitches(tab, path, "Lists", "Click a list to remove it from tags, names and cards. Click again to bring it back.", false,
                list -> !filters.get().isEnabled(list),
                (list, hide) -> {
                    if (hide) {
                        filters.get().disabledLists.add(list);
                    } else {
                        filters.get().disabledLists.remove(list);
                    }
                });
        kitSwitches(tab, path, "Kits", "Click a kit to remove it everywhere. Not into SMP? Hide it and it never shows.", false,
                () -> filters.get().hiddenKits);

        String advanced = "Lists, Advanced";
        add(tab, advanced, "List order", "Click a list to move it up. First wins when a tag only has room for one.", true, (ui, x, y, width) -> {
            List<TierList> order = filters.get().listPriority;
            for (int i = 0; i < order.size(); i++) {
                TierList list = order.get(i);
                boolean clicked = ui.slot(x, y, false);
                dev.tierprofiles.hud.Logos.draw(ui.graphics, list, x + 2, y - 8, 16, 1);
                if (clicked && i > 0) {
                    order.remove(i);
                    order.add(i - 1, list);
                }
                x += Ui.SLOT + 1;
            }
        });

        for (TierList list : TierList.values()) {
            String name = dev.tierprofiles.core.layout.CardGrid.rowName(list);
            add(tab, advanced, name + " minimum", "Only tiers this good show from this list. HT4 means different things on different lists.", true, (ui, x, y, width) -> {
                Filters target = filters.get();
                Tier picked = ui.tierPicker(x, y, target.listMinimum(list), this::colorOf);
                if (picked == null) {
                    target.listMinimums.remove(list);
                } else {
                    target.listMinimums.put(list, picked.label());
                }
            });
        }

        addTall(tab, advanced, "Selected kits", "The kits the Selected gamemode counts. Click to add or remove.", true, 2, (ui, x, y, width) -> {
            List<String> chosen = filters.get().selectedKits;
            List<String> kits = Kits.known();
            int across = perRow(width);
            for (int i = 0; i < kits.size(); i++) {
                String kit = kits.get(i);
                int cellX = slotX(x, i, across);
                int cellY = slotY(y, i, across);
                boolean on = chosen.contains(kit);
                if (ui.slot(cellX, cellY, on)) {
                    if (on) {
                        chosen.remove(kit);
                    } else {
                        chosen.add(kit);
                    }
                }
                screen.drawKit(ui, kit, cellX + 2, cellY - 8);
            }
        });
    }

    private void addTagStyle() {
        SettingsTab tab = SettingsTab.TAGS;
        Supplier<Style> style = () -> active().style;
        String path = "Tags, Format";

        choice(tab, path, "Tag position", "Before or after the player's name.", true, List.of(Style.Position.values()), SettingsCatalog::words, style, s -> s.position, (s, v) -> s.position = v);
        choice(tab, path, "Brackets", "What wraps a tag.", true, List.of(Style.Brackets.values()), SettingsCatalog::bracketName, style, s -> s.brackets, (s, v) -> s.brackets = v);
        choice(tab, path, "Separator", "What goes between tags.", true, List.of(Style.Separator.values()), s -> s.text.isBlank() ? "Space" : s.text.strip(), style, s -> s.separator, (s, v) -> s.separator = v);
        toggle(tab, path, "Long names", "High Tier 3 instead of HT3.", true, style, s -> s.longNames, (s, v) -> s.longNames = v);
    }

    private static String bracketName(Style.Brackets brackets) {
        return brackets == Style.Brackets.NONE ? "None" : brackets.open + " " + brackets.close;
    }

    private void addColours() {
        SettingsTab tab = SettingsTab.COLOURS;
        Supplier<Style> style = () -> active().style;

        add(tab, "Colours", "Preset", "A ready-made set of tier colours.", false, (ui, x, y, width) -> {
            Style target = style.get();
            Palette picked = ui.segments(x, y, List.of(Palette.values()), target.palette, Palette::displayName);
            if (picked != target.palette) {
                target.palette = picked;
                target.tierColors.clear();
            }
        });
        add(tab, "Colours", "Tier colours", "Click a tier for the full colour picker.", false, (ui, x, y, width) -> {
            for (Tier tier : Tier.STANDARD) {
                int rgb = style.get().color(TierList.MCTIERS, tier);
                if (ui.swatch(x, y, rgb, false)) {
                    screen.pickColor(rgb, picked -> style.get().tierColors.put(tier.label(), picked));
                }
                x += Ui.SLOT + 1;
            }
        });

        String advanced = "Colours, Advanced";
        choice(tab, advanced, "Retired chip", "How a retired tier looks on tags.", true, List.of(Style.RetiredMark.values()), SettingsCatalog::words, style, s -> s.retiredMark, (s, v) -> s.retiredMark = v);
        toggle(tab, advanced, "Colours per list", "Give each list its own tier colours.", true, style, s -> s.perListColors, (s, v) -> s.perListColors = v);
        for (TierList list : TierList.values()) {
            add(tab, advanced, list.displayName() + " colours", "Only used with Colours per list on.", true, (ui, x, y, width) -> {
                Style target = style.get();
                for (Tier tier : Tier.STANDARD) {
                    int rgb = target.listColors.getOrDefault(list, java.util.Map.of()).getOrDefault(tier.label(), target.color(TierList.MCTIERS, tier));
                    if (ui.swatch(x, y, rgb, false)) {
                        screen.pickColor(rgb, picked -> style.get().listColors
                                .computeIfAbsent(list, unused -> new java.util.LinkedHashMap<>())
                                .put(tier.label(), picked));
                    }
                    x += Ui.SLOT + 1;
                }
            });
        }
    }

    /** The rule list and editor are drawn by the screen, and these make them searchable. */
    private void addRules() {
        String hint = "Open the Rules tab to edit this.";
        for (String label : List.of("Name colour", "Gradient", "Bold", "Star before name", "Glow around tag", "Ignore retired", "Judge by peak", "If tier", "On list", "In kit")) {
            add(SettingsTab.RULES, "Rules, Rule editor", label, hint, false, (ui, x, y, width) -> {
                if (ui.button(x, y - 8, 96, 16, "Open Rules", false)) {
                    screen.openTab(SettingsTab.RULES);
                }
            });
        }
    }

    private static final String[] PIECES = {"Helmet", "Chestplate", "Leggings", "Boots"};

    /** Which armor piece the trim rows edit. -1 edits all four at once. UI state only. */
    private int piece = -1;

    /** True when the piece being edited has this value, or with All, when every piece does. */
    private boolean pieceIs(Function<Integer, String> get, String id) {
        if (piece >= 0) {
            return id.equals(get.apply(piece));
        }
        for (int slot = 0; slot < 4; slot++) {
            if (!id.equals(get.apply(slot))) {
                return false;
            }
        }
        return true;
    }

    private void setPiece(BiConsumer<Integer, String> set, String id) {
        if (piece >= 0) {
            set.accept(piece, id);
        } else {
            for (int slot = 0; slot < 4; slot++) {
                set.accept(slot, id);
            }
        }
    }

    /** The armor slot the rows below show, which for All is the chestplate. */
    private int shownSlot() {
        return piece < 0 ? 1 : piece;
    }

    private void addProfile() {
        SettingsTab tab = SettingsTab.PROFILE;
        Supplier<Cosmetics> style = this::cosmetics;
        Supplier<CardSettings> card = () -> active().card;
        String path = "Profile";

        add(tab, path, "Background", "A scene behind your card. Other mod users see it too.", false, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            for (String id : Cosmetics.BACKGROUNDS) {
                boolean on = id.equals(target.background);
                int cell = 42;
                int top = y - 10;
                dev.tierprofiles.hud.CardStyle.drawThumbnail(ui.graphics, id, x, top, cell, Ui.SLOT);
                if (on) {
                    ui.frame(x, top, cell, Ui.SLOT);
                }
                if (ui.clicked(x, top, cell, Ui.SLOT)) {
                    target.background = id;
                }
                x += cell + 1;
            }
        });
        choice(tab, path, "Border", "The frame around your card.", false, Cosmetics.BORDERS, SettingsCatalog::words, style, c -> c.border, (c, v) -> c.border = v);
        choice(tab, path, "Effects", "Sparkles and motes drift inside your card.", false, Cosmetics.EFFECTS, SettingsCatalog::words, style, c -> c.effect, (c, v) -> c.effect = v);
        for (int slot = 0; slot < 4; slot++) {
            armorRow(slot);
        }
        listSwitches(tab, path, "Lists on my card", "Leave a list off your own card, for you and everyone else. Handy if you're unranked there.", false,
                list -> style.get().hiddenLists.contains(list.id()),
                (list, hide) -> {
                    List<String> hidden = style.get().hiddenLists;
                    hidden.remove(list.id());
                    if (hide) {
                        hidden.add(list.id());
                    }
                });

        String advanced = "Profile, Advanced";
        kitSwitches(tab, advanced, "Kits on my card", "Leave kits off your own card, for you and everyone else.", true, () -> style.get().hiddenKits);
        add(tab, advanced, "Trims for", "Which piece the trim rows below change. All changes every piece together.", true, (ui, x, y, width) -> {
            List<Integer> choices = List.of(-1, 0, 1, 2, 3);
            piece = ui.segments(x, y, choices, piece, slot -> slot < 0 ? "All" : PIECES[slot]);
        });
        addTall(tab, advanced, "Trim", "A trim pattern, from the real smithing templates.", true, 2, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            int across = perRow(width);
            for (int i = 0; i < Cosmetics.TRIM_PATTERNS.size(); i++) {
                String id = Cosmetics.TRIM_PATTERNS.get(i);
                int cellX = slotX(x, i, across);
                int cellY = slotY(y, i, across);
                if (ui.slot(cellX, cellY, pieceIs(target::trim, id))) {
                    setPiece(target::setTrim, id);
                }
                ui.item(Armor.trimTemplate(id), cellX + 2, cellY - 8);
                if (ui.hovered(cellX, cellY - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT)) {
                    ui.hint(words(id), "A trim pattern, from the real smithing templates.");
                }
            }
        });
        add(tab, advanced, "Trim material", "The colour of the trim.", true, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            for (String id : Cosmetics.TRIM_MATERIALS) {
                if (ui.slot(x, y, pieceIs(target::trimMaterial, id))) {
                    setPiece(target::setTrimMaterial, id);
                }
                ui.item(Armor.trimItem(id), x + 2, y - 8);
                x += Ui.SLOT + 1;
            }
        });
        Object intensity = new Object();
        add(tab, advanced, "Effect strength", "How busy effects are.", true, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            target.intensity = Math.round(ui.slider(intensity, x, y, SLIDER_WIDTH, target.intensity, 0, 100, target.intensity + "%"));
        });
        add(tab, advanced, "Accent", "Tints your title and border.", true, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            for (int color : Cosmetics.ACCENTS) {
                if (ui.swatch(x, y, color, color == target.accent)) {
                    target.accent = color;
                }
                x += Ui.SLOT + 1;
            }
        });
        add(tab, advanced, "Featured kit", "A kit to show off on your card.", true, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            for (String kit : Kits.known().subList(0, Math.min(perRow(width), Kits.known().size()))) {
                if (ui.slot(x, y, kit.equals(target.featuredKit))) {
                    target.featuredKit = kit.equals(target.featuredKit) ? null : kit;
                }
                screen.drawKit(ui, kit, x + 2, y - 8);
                x += Ui.SLOT + 1;
            }
        });
        toggle(tab, advanced, "Model sways", "Let the model turn slowly.", true, card, c -> c.spinModel, (c, v) -> c.spinModel = v);
    }

    /** One row per armor piece, so a diamond helmet can sit over a netherite chestplate. */
    private void armorRow(int slot) {
        add(SettingsTab.PROFILE, "Profile", PIECES[slot], "Only shows on your TierProfiles card. Never changes your real armor.", false, (ui, x, y, width) -> {
            Cosmetics target = cosmetics();
            for (String id : Cosmetics.ARMOR) {
                if (ui.slot(x, y, id.equals(target.armor(slot)))) {
                    target.setArmor(slot, id);
                }
                if (Ui.itemsReady()) {
                    ItemStack item = new ItemStack(Armor.piece(id, slot));
                    if (!item.isEmpty()) {
                        ui.graphics.item(item, x + 2, y - 8);
                    }
                }
                if (ui.hovered(x, y - Ui.SLOT / 2, Ui.SLOT, Ui.SLOT)) {
                    ui.hint(words(id) + " " + PIECES[slot].toLowerCase(Locale.ROOT), "Only shows on your TierProfiles card. Never changes your real armor.");
                }
                x += Ui.SLOT + 1;
            }
        });
    }

    private static String tabPreviewName(CardSettings.TabPreview value) {
        return switch (value) {
            case SMALL -> "Tags only";
            case BIG -> "Full profile";
            case OFF -> "Nothing";
        };
    }

    /** How a Tab choice looks, drawn with your own name and tiers. */
    private Ui.Preview tabPreview(Ui ui, CardSettings.TabPreview value) {
        LiveStage.Subject self = LiveStage.subject();
        Settings settings = active();
        if (value == CardSettings.TabPreview.SMALL) {
            net.minecraft.network.chat.Component tags = dev.tierprofiles.hud.LookAtCard.previewTags(self.profile(), settings);
            int width = dev.tierprofiles.hud.LookAtCard.previewWidth(ui.font, self.name(), tags);
            return new Ui.Preview() {
                public int width() {
                    return width;
                }

                public int height() {
                    return dev.tierprofiles.hud.LookAtCard.previewHeight();
                }

                public void draw(Ui target, int x, int y) {
                    dev.tierprofiles.hud.LookAtCard.drawPreview(target.graphics, target.font, self.name(), tags, x, y, settings.card);
                }
            };
        }
        if (value == CardSettings.TabPreview.BIG) {
            float size = 0.5f;
            int width = Math.round(dev.tierprofiles.hud.ProfileCard.previewWidth(settings) * size);
            int height = Math.round(dev.tierprofiles.hud.ProfileCard.previewHeight(settings, cosmetics(), self.profile()) * size);
            return new Ui.Preview() {
                public int width() {
                    return width;
                }

                public int height() {
                    return height;
                }

                public void draw(Ui target, int x, int y) {
                    dev.tierprofiles.hud.ProfileCard.drawPreview(target.graphics, settings, cosmetics(), self.profile(), self.uuid(), self.name(),
                            x, y, size, screen.uiScale());
                }
            };
        }
        return null;
    }

    private void addCards() {
        SettingsTab tab = SettingsTab.CARDS;
        Supplier<CardSettings> card = () -> active().card;
        String path = "Cards";

        choice(tab, path, "Card detail", "How much each row says. Press the All ranks key on any card to see every kit.", false, List.of(CardSettings.Detail.values()), SettingsCatalog::words, card, c -> c.detail, (c, v) -> c.detail = v);
        choice(tab, path, "Last column", "After the third kit: the date it was achieved, a fourth kit, or nothing.", false,
                List.of(CardSettings.LastColumn.values()), value -> switch (value) {
                    case DATE -> "Date";
                    case FOURTH_TIER -> "4th tier";
                    case NONE -> "Nothing";
                }, card, c -> c.lastColumn, (c, v) -> c.lastColumn = v);
        String tabHint = "What shows as you move through players with Tab held. Hover a choice to see it. Enter keeps the full profile.";
        add(tab, path, "Browsing Tab shows", tabHint, false, (ui, x, y, width) -> {
            CardSettings target = card.get();
            target.tabPreview = ui.segments(x, y, List.of(CardSettings.TabPreview.values()), target.tabPreview, SettingsCatalog::tabPreviewName);
            if (ui.hoveredSegment() instanceof CardSettings.TabPreview hovered) {
                ui.hint(tabPreviewName(hovered), tabHint, tabPreview(ui, hovered));
            }
        });
        add(tab, path, "Tags when aiming", "A small card with a player's tiers when your crosshair is on them. Off hides it.", false, (ui, x, y, width) -> {
            dev.tierprofiles.core.settings.SurfaceSettings aim = active().surface(Surface.LOOK_AT);
            aim.enabled = ui.toggle(x, y, aim.enabled);
            if (ui.hovered(x, y - 9, 60, 18)) {
                ui.hint("Tags when aiming", "A small card with a player's tiers when your crosshair is on them. Off hides it.",
                        tabPreview(ui, CardSettings.TabPreview.SMALL));
            }
        });
        choice(tab, path, "Animation", "How cards arrive.", false, List.of(CardSettings.AnimationStyle.values()), SettingsCatalog::words, card, c -> c.animationStyle, (c, v) -> c.animationStyle = v);
        percent(tab, path, "Speed", "100% is normal. Reduce motion turns all of this off.", false, CardSettings.MIN_SPEED, CardSettings.MAX_SPEED, card, c -> c.animationSpeed, (c, v) -> c.animationSpeed = v);
        add(tab, path, "Stays open", "How long a pinned card stays before it glides away. Zero keeps it until you close it.", false, (ui, x, y, width) -> {
            CardSettings target = card.get();
            String label = target.overlayCloseSeconds == 0 ? "Until closed" : target.overlayCloseSeconds + "s";
            target.overlayCloseSeconds = Math.round(ui.slider(stayOpen, x, y, SLIDER_WIDTH, target.overlayCloseSeconds, 0, CardSettings.MAX_CLOSE_SECONDS, label));
        });

        String advanced = "Cards, Advanced";
        toggle(tab, advanced, "Peak marks", "Show a gold corner when their peak was higher.", true, card, c -> c.showPeak, (c, v) -> c.showPeak = v);
        toggle(tab, advanced, "Retired tiers", "Include retired tiers on the card.", true, card, c -> c.showRetired, (c, v) -> c.showRetired = v);
        toggle(tab, advanced, "Total points", "Show points at the top of the card.", true, card, c -> c.showPoints, (c, v) -> c.showPoints = v);
        percent(tab, advanced, "Size", "Shrinks by itself if it would reach the crosshair.", true, CardSettings.MIN_SCALE, CardSettings.MAX_SCALE, card, c -> c.overlayScale, (c, v) -> c.overlayScale = v);
        percent(tab, advanced, "Opacity", "How solid the background is.", true, 0, 1, card, c -> c.overlayOpacity, (c, v) -> c.overlayOpacity = v);
    }

    private final Object stayOpen = new Object();

    private void addPosition() {
        SettingsTab tab = SettingsTab.POSITION;
        Supplier<CardSettings> card = () -> active().card;
        String path = "Position";

        addTall(tab, path, "Anchor", "Which part of the screen the card sits in.", false, 2, (ui, x, y, width) -> {
            CardSettings target = card.get();
            for (CardSettings.Anchor anchor : CardSettings.Anchor.values()) {
                int cellX = x + anchor.column() * (Ui.SLOT + 1);
                int cellY = y + 16 - Ui.SLOT / 2 + (anchor.row() - 1) * (Ui.SLOT + 1);
                boolean on = anchor == target.anchor;
                ui.graphics.fill(cellX, cellY, cellX + Ui.SLOT, cellY + Ui.SLOT, Theme.ROW);
                ui.graphics.fill(cellX + 8, cellY + 8, cellX + 12, cellY + 12, on ? Theme.ACCENT : Theme.MUTED);
                if (on) {
                    ui.frame(cellX, cellY, Ui.SLOT, Ui.SLOT);
                }
                if (ui.clicked(cellX, cellY, Ui.SLOT, Ui.SLOT)) {
                    target.anchor = anchor;
                    target.offsetX = 0;
                    target.offsetY = 0;
                }
            }
        });
        number(tab, path, "X offset", "Fine adjustment across.", false, -300, 300, "", card, c -> c.offsetX, (c, v) -> c.offsetX = v);
        number(tab, path, "Y offset", "Fine adjustment down.", false, -200, 200, "", card, c -> c.offsetY, (c, v) -> c.offsetY = v);
        toggle(tab, path, "Snap", "Pull the card to anchors and edges while dragging.", false, card, c -> c.snap, (c, v) -> c.snap = v);
        add(tab, path, "Entrance", "The card always arrives from the edge it's closest to.", false, (ui, x, y, width) -> {
            String edge = switch (card.get().anchor.column()) {
                case 0 -> "left";
                case 2 -> "right";
                default -> card.get().anchor.row() == 2 ? "bottom" : "top";
            };
            ui.text("Glides in from the " + edge + " edge", x, y - 4, Theme.TEXT);
        });

        String advanced = "Position, Advanced";
        choice(tab, advanced, "Look-at position", "Where the small card sits.", true, List.of(CardSettings.LookAtAnchor.values()), SettingsCatalog::words, card, c -> c.lookAtAnchor, (c, v) -> c.lookAtAnchor = v);
        percent(tab, advanced, "Look-at scale", "Size of the small card.", true, CardSettings.MIN_SCALE, CardSettings.MAX_SCALE, card, c -> c.lookAtScale, (c, v) -> c.lookAtScale = v);
        percent(tab, advanced, "Look-at opacity", "How solid the small card is.", true, 0, 1, card, c -> c.lookAtOpacity, (c, v) -> c.lookAtOpacity = v);
        number(tab, advanced, "Look-at delay", "Wait this long before showing.", true, 0, CardSettings.MAX_DELAY_MS, "ms", card, c -> c.lookAtDelayMs, (c, v) -> c.lookAtDelayMs = v);
    }

    private void addKeys() {
        addKey("Open settings", Keys.SETTINGS);
        addKey("Pin selected card", Keys.PIN);
        addKey("Show all ranks", Keys.ALL_RANKS);
        addKey("Select player above", Keys.UP);
        addKey("Select player below", Keys.DOWN);
        addKey("Previous column", Keys.LEFT);
        addKey("Next column", Keys.RIGHT);
    }

    private void addKey(String label, KeyMapping key) {
        add(SettingsTab.KEYS, "Keys", label, "Click, then press the new key. Escape cancels.", false, (ui, x, y, width) -> {
            String text = screen.isListeningFor(key) ? "Press a key" : key.getTranslatedKeyMessage().getString();
            if (ui.button(x, y - 10, 110, Ui.SLOT, text, screen.isListeningFor(key))) {
                screen.listenForKey(key);
            }
        });
    }

    private void addMore() {
        SettingsTab tab = SettingsTab.MORE;
        String path = "More";

        toggle(tab, path, "Look up tiers online", "Sends players' names and UUIDs to the tier list sites. Off means nothing is sent and no tiers show.", false, this::global, g -> g.lookupsEnabled, (g, v) -> g.lookupsEnabled = v);
        toggle(tab, path, "Reduce motion", "Turns off every animation in TierProfiles.", false, this::global, g -> g.reduceMotion, (g, v) -> g.reduceMotion = v);
        add(tab, path, "Reset all settings", "Puts every setting and your card style back to how a fresh install starts. Nothing changes until you press Apply, so Discard still undoes it.", false,
                (ui, x, y, width) -> {
                    if (ui.button(x, y - 10, 150, Ui.SLOT, "Reset all settings", false)) {
                        screen.resetAll();
                    }
                });
        Supplier<Style> style = () -> active().style;
        choice(tab, path, "Icon style", "Default is real game items. Classic is the older Programmer Art look, for the items that pack has.", false, List.of(Style.IconSet.values()), SettingsCatalog::words, style, s -> s.iconSet, (s, v) -> s.iconSet = v);

        String advanced = "More, Advanced";
        toggle(tab, advanced, "Official list logos", "Downloads each list's logo from its own website. Off uses our letter marks.", true, this::global, g -> g.officialLogos, (g, v) -> g.officialLogos = v);
        choice(tab, advanced, "Look up from", "Lists asks each list's website yourself. Server goes through TierProfiles, which also shares card styles.", true,
                List.of(Global.Source.values()), source -> source == Global.Source.DIRECT ? "Lists" : "Server", this::global, g -> g.source, (g, v) -> g.source = v);
        add(tab, advanced, "Server address", "Where lookups go when Look up from is Server.", true, (ui, x, y, width) -> screen.drawServerBox(ui, x, y, width));
    }
}
