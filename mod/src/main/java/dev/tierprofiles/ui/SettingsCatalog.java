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

    private void addLists() {
        SettingsTab tab = SettingsTab.TAGS;
        Supplier<Filters> filters = () -> active().filters;
        String path = "Tags, Advanced";

        add(tab, path, "List order", "Click a list to move it up. First wins when a tag only has room for one.", true, (ui, x, y, width) -> {
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
            add(tab, path, name, "Use this list at all, and the lowest tier it may show. HT4 means different things on different lists.", true, (ui, x, y, width) -> {
                Filters target = filters.get();
                boolean on = ui.toggle(x, y, target.isEnabled(list));
                if (on) {
                    target.disabledLists.remove(list);
                } else {
                    target.disabledLists.add(list);
                }
            });
            add(tab, path, name + " minimum", "Only tiers this good show from this list.", true, (ui, x, y, width) -> {
                Filters target = filters.get();
                Tier picked = ui.tierPicker(x, y, target.listMinimum(list), this::colorOf);
                if (picked == null) {
                    target.listMinimums.remove(list);
                } else {
                    target.listMinimums.put(list, picked.label());
                }
            });
        }

        add(tab, path, "Selected kits", "The kits Selected mode counts. Click to add or remove.", true, (ui, x, y, width) -> {
            List<String> chosen = filters.get().selectedKits;
            int perRow = width / (Ui.SLOT + 1);
            int shown = 0;
            for (String kit : Kits.known()) {
                if (shown >= perRow) {
                    break;
                }
                boolean on = chosen.contains(kit);
                if (ui.slot(x, y, on)) {
                    if (on) {
                        chosen.remove(kit);
                    } else {
                        chosen.add(kit);
                    }
                }
                screen.drawKit(ui, kit, x + 2, y - 8);
                x += Ui.SLOT + 1;
                shown += 1;
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

    private void armor(String label, Function<Cosmetics, String> get, BiConsumer<Cosmetics, String> set, Function<String, ItemStack> stack) {
        add(SettingsTab.PROFILE, "Profile, Advanced", label, "Only shows on your TierProfiles card. Never changes your real armor.", true, (ui, x, y, width) -> {
            Cosmetics target = cosmetics();
            for (String id : Cosmetics.ARMOR) {
                boolean on = id.equals(get.apply(target));
                if (ui.slot(x, y, on)) {
                    set.accept(target, id);
                }
                if (Ui.itemsReady()) {
                    ItemStack item = stack.apply(id);
                    if (!item.isEmpty()) {
                        ui.graphics.item(item, x + 2, y - 8);
                    }
                }
                x += Ui.SLOT + 1;
            }
        });
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
        choice(tab, path, "Card detail", "How much each row says. Standard adds dates.", false, List.of(CardSettings.Detail.values()), SettingsCatalog::words, card, c -> c.detail, (c, v) -> c.detail = v);
        choice(tab, path, "Animation", "How cards arrive.", false, List.of(CardSettings.AnimationStyle.values()), SettingsCatalog::words, card, c -> c.animationStyle, (c, v) -> c.animationStyle = v);
        percent(tab, path, "Speed", "100% is normal. Reduce motion turns all of this off.", false, CardSettings.MIN_SPEED, CardSettings.MAX_SPEED, card, c -> c.animationSpeed, (c, v) -> c.animationSpeed = v);

        armor("Helmet", c -> c.helmet, (c, v) -> c.helmet = v, id -> new ItemStack(Armor.piece(id, 0)));
        armor("Chestplate", c -> c.chestplate, (c, v) -> c.chestplate = v, id -> new ItemStack(Armor.piece(id, 1)));
        armor("Leggings", c -> c.leggings, (c, v) -> c.leggings = v, id -> new ItemStack(Armor.piece(id, 2)));
        armor("Boots", c -> c.boots, (c, v) -> c.boots = v, id -> new ItemStack(Armor.piece(id, 3)));

        String advanced = "Profile, Advanced";
        choice(tab, advanced, "Trim", "A trim pattern for your card's armor.", true, Cosmetics.TRIM_PATTERNS.subList(0, 8), SettingsCatalog::words, style, c -> c.trimPattern, (c, v) -> c.trimPattern = v);
        choice(tab, advanced, "More trims", "More trim patterns.", true, Cosmetics.TRIM_PATTERNS.subList(8, 15), SettingsCatalog::words, style, c -> c.trimPattern, (c, v) -> c.trimPattern = v);
        choice(tab, advanced, "Even more trims", "The rest of the trim patterns.", true, Cosmetics.TRIM_PATTERNS.subList(15, Cosmetics.TRIM_PATTERNS.size()), SettingsCatalog::words, style, c -> c.trimPattern, (c, v) -> c.trimPattern = v);
        add(tab, advanced, "Trim material", "The colour of the trim.", true, (ui, x, y, width) -> {
            Cosmetics target = style.get();
            for (String id : Cosmetics.TRIM_MATERIALS) {
                if (ui.slot(x, y, id.equals(target.trimMaterial))) {
                    target.trimMaterial = id;
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
            int shown = 0;
            for (String kit : Kits.known().subList(0, 12)) {
                if (ui.slot(x, y, kit.equals(target.featuredKit))) {
                    target.featuredKit = kit.equals(target.featuredKit) ? null : kit;
                }
                screen.drawKit(ui, kit, x + 2, y - 8);
                x += Ui.SLOT + 1;
                shown += 1;
            }
        });
        toggle(tab, advanced, "Model sways", "Let the model turn slowly.", true, card, c -> c.spinModel, (c, v) -> c.spinModel = v);
        toggle(tab, advanced, "Peak marks", "Show a gold corner when their peak was higher.", true, card, c -> c.showPeak, (c, v) -> c.showPeak = v);
        toggle(tab, advanced, "Dates", "Show when tiers were achieved.", true, card, c -> c.showAttained, (c, v) -> c.showAttained = v);
        toggle(tab, advanced, "Retired tiers", "Include retired tiers on the card.", true, card, c -> c.showRetired, (c, v) -> c.showRetired = v);
        toggle(tab, advanced, "Total points", "Show points at the top of the card.", true, card, c -> c.showPoints, (c, v) -> c.showPoints = v);
        percent(tab, advanced, "Size", "Shrinks by itself if it would reach the crosshair.", true, CardSettings.MIN_SCALE, CardSettings.MAX_SCALE, card, c -> c.overlayScale, (c, v) -> c.overlayScale = v);
        percent(tab, advanced, "Opacity", "How solid the background is.", true, 0, 1, card, c -> c.overlayOpacity, (c, v) -> c.overlayOpacity = v);
        number(tab, advanced, "Close after", "Zero keeps it open until you close it.", true, 0, CardSettings.MAX_CLOSE_SECONDS, "s", card, c -> c.overlayCloseSeconds, (c, v) -> c.overlayCloseSeconds = v);
    }

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

        toggle(tab, path, "Look up tiers online", "Off means no data is sent and no tiers show.", false, this::global, g -> g.lookupsEnabled, (g, v) -> g.lookupsEnabled = v);
        toggle(tab, path, "Reduce motion", "Turns off every animation in TierProfiles.", false, this::global, g -> g.reduceMotion, (g, v) -> g.reduceMotion = v);
        Supplier<Style> style = () -> active().style;
        choice(tab, path, "Icon style", "Default is real game items. Classic is the older Programmer Art look, for the items that pack has.", false, List.of(Style.IconSet.values()), SettingsCatalog::words, style, s -> s.iconSet, (s, v) -> s.iconSet = v);

        String advanced = "More, Advanced";
        toggle(tab, advanced, "Official list logos", "Downloads each list's logo from its own website. Off uses our letter marks.", true, this::global, g -> g.officialLogos, (g, v) -> g.officialLogos = v);
        add(tab, advanced, "Server address", "Where lookups go.", true, (ui, x, y, width) -> screen.drawServerBox(ui, x, y, width));
    }
}
