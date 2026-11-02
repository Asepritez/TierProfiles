package dev.tierprofiles.ui;

import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Filters;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.Palette;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.core.settings.SurfaceSettings;
import dev.tierprofiles.input.Keys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;

/**
 * Every setting in the mod, declared in one place. Adding a line here puts a setting
 * on its tab and in search, with nothing else to wire up.
 */
final class SettingsCatalog {
    private static final int CYCLE_WIDTH = 104;
    private static final int SLIDER_WIDTH = 124;

    private final Supplier<ConfigFile> staged;
    private final SettingsScreen screen;
    private final List<Setting> all = new ArrayList<>();

    SettingsCatalog(Supplier<ConfigFile> staged, SettingsScreen screen) {
        this.staged = staged;
        this.screen = screen;

        for (Surface surface : Surface.values()) {
            addSurface(surface);
        }
        addLists();
        addTagStyle();
        addColours();
        addCard();
        addMore();
        addKeys();
    }

    List<Setting> all() {
        return all;
    }

    /** The settings for a tab, with the Tags tab narrowed to the surface being edited. */
    List<Setting> forTab(SettingsTab tab, Surface surface) {
        List<Setting> out = new ArrayList<>();
        for (Setting setting : all) {
            boolean otherSurface = tab == SettingsTab.TAGS && isSurfaceGroup(setting) && !setting.group().startsWith(surface.displayName());
            if (setting.tab() == tab && !otherSurface) {
                out.add(setting);
            }
        }
        return out;
    }

    private static boolean isSurfaceGroup(Setting setting) {
        return Arrays.stream(Surface.values()).anyMatch(surface -> setting.group().startsWith(surface.displayName()));
    }

    private Settings active() {
        return staged.get().active();
    }

    private Global global() {
        return staged.get().global;
    }

    private void add(SettingsTab tab, String group, String label, String hint, boolean advanced, Setting.Control control) {
        all.add(new Setting(tab, group, label, hint, advanced, control));
    }

    private <O> void toggle(SettingsTab tab, String group, String label, String hint, boolean advanced,
            Supplier<O> owner, Function<O, Boolean> get, BiConsumer<O, Boolean> set) {
        add(tab, group, label, hint, advanced, (ui, right, y) -> {
            O target = owner.get();
            set.accept(target, ui.toggle(right, y, get.apply(target)));
        });
    }

    private <O, E extends Enum<E>> void choice(SettingsTab tab, String group, String label, String hint, boolean advanced,
            E[] values, Function<E, String> name, Supplier<O> owner, Function<O, E> get, BiConsumer<O, E> set) {
        add(tab, group, label, hint, advanced, (ui, right, y) -> {
            O target = owner.get();
            set.accept(target, ui.cycle(right, y, CYCLE_WIDTH, List.of(values), get.apply(target), name));
        });
    }

    private <O> void percent(SettingsTab tab, String group, String label, String hint, boolean advanced,
            float min, float max, Supplier<O> owner, Function<O, Float> get, BiConsumer<O, Float> set) {
        Object id = new Object();
        add(tab, group, label, hint, advanced, (ui, right, y) -> {
            O target = owner.get();
            float value = get.apply(target);
            set.accept(target, ui.slider(id, right, y, SLIDER_WIDTH, value, min, max, Math.round(value * 100) + "%"));
        });
    }

    private <O> void number(SettingsTab tab, String group, String label, String hint, boolean advanced,
            int min, int max, String unit, Supplier<O> owner, Function<O, Integer> get, BiConsumer<O, Integer> set) {
        Object id = new Object();
        add(tab, group, label, hint, advanced, (ui, right, y) -> {
            O target = owner.get();
            int value = get.apply(target);
            float moved = ui.slider(id, right, y, SLIDER_WIDTH, value, min, max, value + unit);
            set.accept(target, Math.round(moved));
        });
    }

    private static String words(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int colorOf(Tier tier) {
        return active().style.palette.color(tier);
    }

    private void addSurface(Surface surface) {
        String name = surface.displayName();
        String filter = name + " / Filter";
        String format = name + " / Tag format";
        Supplier<SurfaceSettings> rules = () -> active().surface(surface);
        SettingsTab tab = SettingsTab.TAGS;

        toggle(tab, filter, "Show on " + name.toLowerCase(Locale.ROOT), "Turn this surface on or off.", false, rules, s -> s.enabled, (s, v) -> s.enabled = v);
        add(tab, filter, "Minimum tier", "Hide tags below this tier on this surface.", false, (ui, right, y) -> {
            SurfaceSettings target = rules.get();
            Tier picked = ui.tierPicker(right, y, target.minimum(), this::colorOf);
            target.minimumTier = picked == null ? null : picked.label();
        });
        choice(tab, filter, "Which tier", "What to show when current and peak differ.", false, SurfaceSettings.TierMode.values(), SettingsCatalog::words, rules, s -> s.tierMode, (s, v) -> s.tierMode = v);
        choice(tab, filter, "Gamemode", "Highest uses their best kit. Selected uses your kit list. Auto reads your inventory.", false, Filters.KitMode.values(), SettingsCatalog::words, rules, s -> s.kitMode, (s, v) -> s.kitMode = v);
        toggle(tab, filter, "Hide retired", "Ignore tiers the player has retired from.", true, rules, s -> s.hideRetired, (s, v) -> s.hideRetired = v);
        toggle(tab, filter, "Hide unranked", "Show nothing for players with no tiers.", true, rules, s -> s.hideUnranked, (s, v) -> s.hideUnranked = v);
        toggle(tab, filter, "Hide my own tag", "Leave your own name untagged.", true, rules, s -> s.hideOwnTag, (s, v) -> s.hideOwnTag = v);
        toggle(tab, filter, "Apply highlight rules", "Let name rules recolour names here.", true, rules, s -> s.highlightName, (s, v) -> s.highlightName = v);
        toggle(tab, format, "Gamemode icon", "Show the kit icon in the tag.", true, rules, s -> s.kitIcon, (s, v) -> s.kitIcon = v);
        toggle(tab, format, "List logo", "Show which list the tier came from.", true, rules, s -> s.listLogo, (s, v) -> s.listLogo = v);
        toggle(tab, format, "Region", "Show the player's region.", true, rules, s -> s.region, (s, v) -> s.region = v);
        number(tab, format, "Tags at once", "How many lists may show a tag together.", true, 1, SurfaceSettings.MAX_TAGS, "", rules, s -> s.maxTags, (s, v) -> s.maxTags = v);
    }

    private void addLists() {
        SettingsTab tab = SettingsTab.TAGS;
        Supplier<Filters> filters = () -> active().filters;

        add(tab, "Lists", "List priority", "Which list wins when a tag only has room for one.", false,
                (ui, right, y) -> screen.drawPriorityHint(ui, right, y));

        for (TierList list : TierList.values()) {
            String name = list.displayName() + (list.isBeta() ? " (beta)" : "");
            add(tab, "Per-list", "Use " + name, "Switch this list off everywhere.", true, (ui, right, y) -> {
                Filters target = filters.get();
                boolean on = ui.toggle(right, y, target.isEnabled(list));
                if (on) {
                    target.disabledLists.remove(list);
                } else {
                    target.disabledLists.add(list);
                }
            });
            add(tab, "Per-list", name + " minimum", "HT4 means different things on different lists.", true, (ui, right, y) -> {
                Filters target = filters.get();
                Tier picked = ui.tierPicker(right, y, target.listMinimum(list), this::colorOf);
                if (picked == null) {
                    target.listMinimums.remove(list);
                } else {
                    target.listMinimums.put(list, picked.label());
                }
            });
        }
    }

    private void addTagStyle() {
        SettingsTab tab = SettingsTab.TAGS;
        Supplier<Style> style = () -> active().style;
        String group = "Tag style";

        choice(tab, group, "Tag position", "Before or after the player's name.", false, Style.Position.values(), SettingsCatalog::words, style, s -> s.position, (s, v) -> s.position = v);
        choice(tab, group, "Icon style", "Classic is the older look, from the game's own Programmer Art pack.", false, Style.IconSet.values(), SettingsCatalog::words, style, s -> s.iconSet, (s, v) -> s.iconSet = v);
        choice(tab, group, "Brackets", "What wraps a tag.", true, Style.Brackets.values(), SettingsCatalog::words, style, s -> s.brackets, (s, v) -> s.brackets = v);
        choice(tab, group, "Separator", "What goes between tags.", true, Style.Separator.values(), SettingsCatalog::words, style, s -> s.separator, (s, v) -> s.separator = v);
        toggle(tab, group, "Long names", "High Tier 3 instead of HT3.", true, style, s -> s.longNames, (s, v) -> s.longNames = v);
    }

    private void addColours() {
        SettingsTab tab = SettingsTab.COLOURS;
        Supplier<Style> style = () -> active().style;

        add(tab, "Presets", "Tier preset", "A ready-made set of tier colours.", false, (ui, right, y) -> {
            Style target = style.get();
            Palette picked = ui.cycle(right, y, CYCLE_WIDTH, List.of(Palette.values()), target.palette, Palette::displayName);
            if (picked != target.palette) {
                target.palette = picked;
                target.tierColors.clear();
            }
        });
        choice(tab, "Presets", "Retired style", "How a retired tier looks on tags.", true, Style.RetiredMark.values(), SettingsCatalog::words, style, s -> s.retiredMark, (s, v) -> s.retiredMark = v);
        toggle(tab, "Presets", "Colours per list", "Give each list its own colours.", true, style, s -> s.perListColors, (s, v) -> s.perListColors = v);

        for (Tier tier : Tier.STANDARD) {
            add(tab, "Tier colours", tier.label() + " colour", "Click to choose any colour.", false, (ui, right, y) -> {
                Style target = style.get();
                int rgb = target.color(TierList.MCTIERS, tier);
                if (ui.swatch(right - 14, y - 7, 14, rgb)) {
                    screen.pickColor(rgb, picked -> style.get().tierColors.put(tier.label(), picked));
                }
            });
        }
        for (TierList list : TierList.values()) {
            for (Tier tier : Tier.STANDARD) {
                add(tab, list.displayName() + " colours", list.displayName() + " " + tier.label(), "Only used with Colours per list on.", true, (ui, right, y) -> {
                    Style target = style.get();
                    int rgb = target.listColors.getOrDefault(list, java.util.Map.of()).getOrDefault(tier.label(), target.color(TierList.MCTIERS, tier));
                    if (ui.swatch(right - 14, y - 7, 14, rgb)) {
                        screen.pickColor(rgb, picked -> style.get().listColors
                                .computeIfAbsent(list, unused -> new java.util.LinkedHashMap<>())
                                .put(tier.label(), picked));
                    }
                });
            }
        }
    }

    private void addCard() {
        SettingsTab tab = SettingsTab.MORE;
        Supplier<CardSettings> card = () -> active().card;
        String overlay = "Profile card";
        String look = "Look-at card";
        String motion = "Animation";

        choice(tab, overlay, "Card detail", "How much each row says. Standard adds dates.", false, CardSettings.Detail.values(), SettingsCatalog::words, card, c -> c.detail, (c, v) -> c.detail = v);
        choice(tab, overlay, "Position", "Where the card is pinned. It's kept clear of the crosshair and hotbar.", false, CardSettings.Anchor.values(), SettingsCatalog::words, card, c -> c.anchor, (c, v) -> c.anchor = v);
        number(tab, overlay, "Offset X", "Fine adjustment across.", true, -300, 300, "", card, c -> c.offsetX, (c, v) -> c.offsetX = v);
        number(tab, overlay, "Offset Y", "Fine adjustment down.", true, -200, 200, "", card, c -> c.offsetY, (c, v) -> c.offsetY = v);
        percent(tab, overlay, "Card scale", "Shrinks by itself if it would reach the crosshair.", true, CardSettings.MIN_SCALE, CardSettings.MAX_SCALE, card, c -> c.overlayScale, (c, v) -> c.overlayScale = v);
        percent(tab, overlay, "Card opacity", "How solid the background is.", true, 0, 1, card, c -> c.overlayOpacity, (c, v) -> c.overlayOpacity = v);
        number(tab, overlay, "Close after", "Zero keeps it open until you close it.", true, 0, CardSettings.MAX_CLOSE_SECONDS, "s", card, c -> c.overlayCloseSeconds, (c, v) -> c.overlayCloseSeconds = v);
        toggle(tab, overlay, "Player model", "Show the 3D model.", true, card, c -> c.showModel, (c, v) -> c.showModel = v);
        toggle(tab, overlay, "Model sways", "Let the model turn slowly.", true, card, c -> c.spinModel, (c, v) -> c.spinModel = v);
        toggle(tab, overlay, "Peak marks", "Mark tiers whose peak was higher.", true, card, c -> c.showPeak, (c, v) -> c.showPeak = v);
        toggle(tab, overlay, "Dates", "Show when tiers were achieved.", true, card, c -> c.showAttained, (c, v) -> c.showAttained = v);
        toggle(tab, overlay, "Retired tiers", "Include retired tiers on the card.", true, card, c -> c.showRetired, (c, v) -> c.showRetired = v);
                toggle(tab, overlay, "Total points", "Show points at the top of the card.", true, card, c -> c.showPoints, (c, v) -> c.showPoints = v);

        choice(tab, motion, "Animation style", "How cards enter and leave.", false, CardSettings.AnimationStyle.values(), SettingsCatalog::words, card, c -> c.animationStyle, (c, v) -> c.animationStyle = v);
        percent(tab, motion, "Animation speed", "100% is normal.", true, CardSettings.MIN_SPEED, CardSettings.MAX_SPEED, card, c -> c.animationSpeed, (c, v) -> c.animationSpeed = v);
        toggle(tab, motion, "Reduce motion", "Turns off every animation in TierProfiles.", false, this::global, g -> g.reduceMotion, (g, v) -> g.reduceMotion = v);

        choice(tab, look, "Look-at position", "Where the small card sits.", true, CardSettings.LookAtAnchor.values(), SettingsCatalog::words, card, c -> c.lookAtAnchor, (c, v) -> c.lookAtAnchor = v);
        percent(tab, look, "Look-at scale", "Size of the small card.", true, CardSettings.MIN_SCALE, CardSettings.MAX_SCALE, card, c -> c.lookAtScale, (c, v) -> c.lookAtScale = v);
        percent(tab, look, "Look-at opacity", "How solid the small card is.", true, 0, 1, card, c -> c.lookAtOpacity, (c, v) -> c.lookAtOpacity = v);
        number(tab, look, "Look-at delay", "Wait this long before showing.", true, 0, CardSettings.MAX_DELAY_MS, "ms", card, c -> c.lookAtDelayMs, (c, v) -> c.lookAtDelayMs = v);
    }

    private void addMore() {
        SettingsTab tab = SettingsTab.MORE;
        String group = "Privacy and network";

        toggle(tab, group, "Look up tiers online", "Off means no data is sent and no tiers show.", false, this::global, g -> g.lookupsEnabled, (g, v) -> g.lookupsEnabled = v);
        toggle(tab, group, "Official list logos", "Downloads each list's logo from its own website. Off uses letter marks.", true, this::global, g -> g.officialLogos, (g, v) -> g.officialLogos = v);
        add(tab, group, "Server address", "Where lookups go.", true, (ui, right, y) -> screen.drawServerBox(ui, right, y));
    }

    private void addKeys() {
        SettingsTab tab = SettingsTab.KEYS;
        addKey(tab, "Open settings", Keys.SETTINGS);
        addKey(tab, "Pin selected card", Keys.PIN);
        addKey(tab, "Select player above", Keys.UP);
        addKey(tab, "Select player below", Keys.DOWN);
        addKey(tab, "Previous column", Keys.LEFT);
        addKey(tab, "Next column", Keys.RIGHT);
    }

    private void addKey(SettingsTab tab, String label, KeyMapping key) {
        add(tab, "Controls", label, "Click, then press the new key.", false, (ui, right, y) -> {
            String text = screen.isListeningFor(key) ? "Press a key..." : key.getTranslatedKeyMessage().getString();
            if (ui.button(right - CYCLE_WIDTH, y - 7, CYCLE_WIDTH, 14, text, screen.isListeningFor(key))) {
                screen.listenForKey(key);
            }
        });
    }
}
