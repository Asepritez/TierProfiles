package dev.tierprofiles.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigEditor;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.config.ShareCode;
import dev.tierprofiles.core.model.Kits;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Colors;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Palette;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Style;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.surface.KitIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The settings screen, built like an inventory: a rail of categories, options you pick
 * with a hotbar-style frame, a permanent Live Stage showing the result, and a hotbar of
 * your nine setups along the bottom.
 *
 * Changes go to a staged copy and do nothing until Apply. Each tab opens on a few
 * essentials with one Advanced row beneath, and search reaches every setting.
 *
 * Layout numbers are from docs/LAYOUT.md, whose reference size is 960 by 540. Minecraft's
 * own GUI scale often gives much less than that, so this screen picks its own scale
 * through `virtualScale`, and everything below works in those virtual pixels.
 */
public final class SettingsScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int HEADER_Y = 10;
    private static final int HEADER_H = 24;
    private static final int RAIL_W = 88;
    private static final int STAGE_W = 380;
    private static final int BODY_Y = 40;
    private static final int FOOTER = 44;
    private static final int FOOTER_Y = 34;
    private static final int RAIL_ITEM = 34;
    private static final int PADDING = 10;
    private static final int ROW = 28;
    private static final int ROW_STEP = 32;
    private static final int LABEL_W = 130;
    /** The stage and the options' control column both need their full width. */
    private static final int MIN_WIDTH_FOR_STAGE = 950;
    private static final int SCROLL_STEP = 32;
    private static final int RULE_ROW = 24;
    private static final float TITLE_SCALE = 1.5f;
    private static final int FOOTER_BUTTON = 24;

    private static final List<Item> SETUP_ICONS = List.of(
            Items.ENDER_PEARL, Items.NETHERITE_SWORD, Items.MACE, Items.END_CRYSTAL, Items.NAME_TAG,
            Items.GOLDEN_APPLE, Items.BOW, Items.TOTEM_OF_UNDYING, Items.SHIELD);

    private final Screen parent;
    private final ConfigEditor editor = new ConfigEditor(TierProfiles.config());
    private final Ui ui = new Ui();
    private final SettingsCatalog catalog = new SettingsCatalog(editor::staged, this);
    private final LiveStage stage = new LiveStage();

    private SettingsTab tab = SettingsTab.TAGS;
    private Surface surface = Surface.NAMETAG;
    private final Map<SettingsTab, Boolean> advancedOpen = new EnumMap<>(SettingsTab.class);
    private final Map<SettingsTab, Integer> scrollByTab = new EnumMap<>(SettingsTab.class);
    private String footerNote;
    private int footerNoteColor;
    private long footerNoteAt;
    private int contentHeight;
    private int visibleHeight;

    private final TextField search = new TextField(40, "");
    private final TextField serverBox = new TextField(200, "");
    private final TextField nameBox = new TextField(ConfigFile.MAX_NAME_LENGTH, "Type a new name");
    private final TextField codeBox = new TextField(65536, "Paste a code");
    private final TextField hexBox = new TextField(7, "");
    private final Object redSlider = new Object();
    private final Object greenSlider = new Object();
    private final Object blueSlider = new Object();

    private ColorPicker colorPicker;
    private KeyMapping listeningKey;
    private boolean confirmingClose;
    private boolean showingStage;
    private boolean importFilters = true;
    private boolean importColours = true;
    private boolean importRules = true;
    private boolean importCard = true;
    private TierList colourList = TierList.DEFAULT_PRIORITY.getFirst();
    private Tier colourTier = Tier.parse("HT3");
    private int selectedRule;
    private String sharingMessage = "";
    private Settings pendingImport;

    private int virtualWidth;
    private int virtualHeight;
    private float scale;

    public SettingsScreen(Screen parent) {
        super(Component.literal("TierProfiles"));
        this.parent = parent;
        serverBox.value = editor.staged().global.serverUrl;
    }

    private Settings staged() {
        return editor.staged().active();
    }

    private ConfigFile config() {
        return editor.staged();
    }

    /** For tests, and for jumping straight to a tab. */
    public void openTab(SettingsTab wanted) {
        tab = wanted;
        search.value = "";
    }

    /** For tests: type into the search box. */
    public void searchFor(String text) {
        search.value = text;
    }

    public void setAdvanced(boolean open) {
        advancedOpen.put(tab, open);
    }

    public void openDiagnostics() {
        tab = SettingsTab.MORE;
        advancedOpen.put(SettingsTab.MORE, true);
    }

    /** Stages a change from code, for tests. */
    public ConfigFile stagedConfig() {
        return editor.staged();
    }

    /** Stages a full reset. Apply makes it real, and Discard takes it back. */
    void resetAll() {
        editor.replaceStaged(ConfigFile.defaultsKeepingConsent(editor.staged()));
        serverBox.value = editor.staged().global.serverUrl;
        sharingMessage = "";
    }

    public void resetAllForTest() {
        resetAll();
    }

    public void discardForTest() {
        editor.discard();
    }

    /** Screen pixels per virtual pixel, for drawing models inside previews. */
    float uiScale() {
        return scale;
    }

    public int changeCount() {
        return editor.changeCount();
    }

    public void applyChanges() {
        apply();
    }

    public void selectRule(int index) {
        selectedRule = index;
    }

    /**
     * The whole-number size for this window, as physical pixels per virtual pixel. At
     * 1080p that's 2, which gives exactly the reference 960 by 540 whatever the GUI scale.
     */
    private void measure() {
        int physicalWidth = minecraft.getWindow().getWidth();
        int physicalHeight = minecraft.getWindow().getHeight();
        int pixels = Math.max(1, Math.min(physicalWidth / 960, physicalHeight / 540));
        scale = (float) pixels / minecraft.getWindow().getGuiScale();
        virtualWidth = Math.round(width / scale);
        virtualHeight = Math.round(height / scale);
    }

    private boolean stageFits() {
        return virtualWidth >= MIN_WIDTH_FOR_STAGE;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFC090B0C);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        measure();
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        ui.begin(graphics, Math.round(mouseX / scale), Math.round(mouseY / scale));
        ui.setBlocked(colorPicker != null || confirmingClose);

        drawHeader();
        drawRail();
        drawBody();
        drawHotbar();
        if (colorPicker == null && !confirmingClose) {
            drawTooltip();
        }

        if (colorPicker != null && colorPicker.draw(ui, virtualWidth, virtualHeight)) {
            colorPicker = null;
        }
        if (confirmingClose) {
            drawConfirmClose();
        }
        ui.end();
        graphics.pose().popMatrix();
    }

    private void drawHeader() {
        ui.text("TierProfiles", MARGIN + 2, HEADER_Y + 8, 0xFFFFFFFF);
        int x = MARGIN + RAIL_W;
        int width = virtualWidth - x - MARGIN;
        ui.graphics.fill(x, HEADER_Y, x + width, HEADER_Y + HEADER_H, Theme.INK);
        ui.item(Items.SPYGLASS, x + 4, HEADER_Y + 4);

        boolean searching = isSearching();
        String prompt = "Search all " + catalog.all().size() + " settings";
        if (!search.focused && !searching) {
            ui.text(prompt, x + 24, HEADER_Y + 8, Theme.MUTED);
        }
        search.drawBare(ui, x + 20, HEADER_Y + 2, width - 70, HEADER_H - 4);
        String shortcut = searching ? "Esc" : "Ctrl F";
        ui.text(shortcut, x + width - 6 - ui.width(shortcut), HEADER_Y + 8, Theme.MUTED);

        if (!stageFits()) {
            int buttonX = x + width - 100;
            if (ui.button(buttonX, HEADER_Y + 3, 56, 18, showingStage ? "Options" : "Stage", showingStage)) {
                showingStage = !showingStage;
            }
        }
    }

    private boolean isSearching() {
        return !search.value.isBlank();
    }

    private void drawRail() {
        int y = BODY_Y;
        for (SettingsTab candidate : SettingsTab.values()) {
            boolean on = candidate == tab && !isSearching();
            int height = RAIL_ITEM - 2;
            ui.graphics.fill(MARGIN, y, MARGIN + RAIL_W + (on ? 2 : -1), y + height, on ? Theme.INK : 0xFF0E1216);
            if (on) {
                ui.graphics.fill(MARGIN, y, MARGIN + 2, y + height, Theme.ACCENT);
            }
            ui.graphics.outline(MARGIN, y, RAIL_W - 1, height, on ? 0x00000000 : 0xFF1E2329);
            ui.item(candidate.icon(), MARGIN + 6, y + (height - 16) / 2);
            ui.text(candidate.title(), MARGIN + 27, y + (height - 8) / 2, on ? 0xFFFFFFFF : Theme.MUTED);
            if (ui.hovered(MARGIN, y, RAIL_W, height)) {
                ui.hint(candidate.title(), candidate.description());
            }
            if (ui.clicked(MARGIN, y, RAIL_W, height)) {
                openTab(candidate);
            }
            y += RAIL_ITEM;
        }
    }

    private void drawBody() {
        int top = BODY_Y;
        int bottom = virtualHeight - FOOTER;
        int optionsX = MARGIN + RAIL_W;
        boolean stageBeside = stageFits();
        int stageWidth = stageBeside ? STAGE_W : virtualWidth - optionsX - MARGIN;
        int optionsWidth = stageBeside ? virtualWidth - optionsX - STAGE_W - 2 * MARGIN : stageWidth;
        int stageX = stageBeside ? optionsX + optionsWidth + MARGIN : optionsX;

        if (stageBeside || !showingStage) {
            drawOptions(optionsX, top, optionsWidth, bottom - top);
        }
        if (stageBeside || showingStage) {
            stage.draw(ui, stageX, top, stageWidth, bottom - top, tab, editor.staged(), scale);
        }
    }

    void drawKit(Ui ui, String kit, int x, int y) {
        KitIcon.draw(ui.graphics, kit, x, y, staged().style.iconSet);
    }

    private void drawOptions(int x, int top, int width, int height) {
        ui.panel(x, top, width, height);

        int left = x + PADDING;
        int right = x + width - PADDING;
        drawTitle(left, right, top + 13);

        int listTop = top + 38;
        int listBottom = top + height - 1;
        visibleHeight = listBottom - listTop;
        int scroll = scrollByTab.getOrDefault(tab, 0);

        ui.clip(x + 1, listTop, x + width - 1, listBottom);
        int y = listTop - scroll;
        if (isSearching()) {
            y = drawSearchResults(left, right, y);
        } else {
            y = drawTab(left, right, y);
        }
        ui.unclip();
        contentHeight = y + scroll - listTop;
        drawScrollbar(x + width - 4, listTop, listBottom, scroll);
    }

    private void drawScrollbar(int x, int top, int bottom, int scroll) {
        if (contentHeight <= visibleHeight) {
            return;
        }
        int track = bottom - top;
        int thumb = Math.max(16, track * visibleHeight / contentHeight);
        int y = top + (track - thumb) * scroll / Math.max(1, contentHeight - visibleHeight);
        ui.graphics.fill(x, y, x + 2, y + thumb, Theme.BORDER);
    }

    private void drawTitle(int left, int right, int y) {
        String title = isSearching() ? resultCount() + " results" : tab.title();
        ui.bigText(title, left, y, TITLE_SCALE, 0xFFFFFFFF);

        String note = switch (tab) {
            case TAGS -> surface.displayName();
            case LISTS -> listsNote();
            case RULES -> "First match wins";
            case POSITION -> words(staged().card.anchor.name());
            case PROFILE -> TierProfiles.cosmetics().state().message();
            default -> "";
        };
        if (!isSearching()) {
            if (Boolean.TRUE.equals(advancedOpen.get(tab))) {
                note = "Advanced";
            }
            ui.text(note, right - ui.width(note), y + 3, Theme.MUTED);
        }
    }

    private String listsNote() {
        long shown = java.util.Arrays.stream(TierList.values()).filter(staged().filters::isEnabled).count();
        int hiddenKits = staged().filters.hiddenKits.size();
        return shown + " of " + TierList.values().length + " lists" + (hiddenKits > 0 ? ", " + hiddenKits + " kits hidden" : "");
    }

    private static String words(String id) {
        String lower = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int drawTab(int left, int right, int y) {
        boolean advanced = Boolean.TRUE.equals(advancedOpen.get(tab));
        if (tab == SettingsTab.RULES) {
            return drawRules(left, right, y, advanced) + 6;
        }
        if (advanced) {
            y = drawRows(catalog.forTab(tab, surface, true), left, right, y);
            if (tab == SettingsTab.MORE) {
                y = drawDiagnostics(left, y);
            }
            if (tab == SettingsTab.SHARING) {
                y = drawSetups(left, right, y);
            }
            return drawAdvancedRow(left, right, y, "Back to the essentials", true);
        }

        switch (tab) {
            case COLOURS -> y = drawColours(left, right, y);
            case SHARING -> y = drawSharing(left, right, y);
            default -> {
                if (tab == SettingsTab.TAGS) {
                    y = drawSurfaceSlots(left, y);
                }
                y = drawRows(catalog.forTab(tab, surface, false), left, right, y);
            }
        }

        int more = catalog.forTab(tab, surface, true).size() + (tab == SettingsTab.SHARING ? config().profiles.size() : 0)
                + (tab == SettingsTab.MORE ? TierList.values().length : 0);
        if (more > 0) {
            y = drawAdvancedRow(left, right, y, more + " more: " + advancedSummary(), false);
        }
        return y + 6;
    }

    private String advancedSummary() {
        return switch (tab) {
            case TAGS -> "retired, unranked, icons, format";
            case LISTS -> "list order, per-list minimums, selected kits";
            case COLOURS -> "retired chips, colours per list";
            case RULES -> "gradient, gamemode, retired, peak";
            case SHARING -> "your setups";
            case PROFILE -> "kits on my card, accent, featured kit";
            case CARDS -> "peak marks, retired tiers, points, size, opacity";
            case POSITION -> "look-at card";
            case MORE -> "logos, server, diagnostics";
            default -> "";
        };
    }

    private int drawColours(int left, int right, int y) {
        Style style = staged().style;

        y = line("Preset", left, y, (x, c) -> {
            for (Palette palette : Palette.values()) {
                int cellWidth = 68;
                int top = c - Ui.SLOT / 2;
                boolean on = style.palette == palette && style.tierColors.isEmpty();
                ui.graphics.fill(x, top, x + cellWidth, top + Ui.SLOT, Theme.SLOT);
                int swatch = x + 6;
                for (int i = 0; i < Tier.STANDARD.size(); i += 2) {
                    ui.graphics.fill(swatch, top + 5, swatch + 11, top + 15, Theme.opaque(palette.color(Tier.STANDARD.get(i))));
                    swatch += 11;
                }
                if (on) {
                    ui.frame(x, top, cellWidth, Ui.SLOT);
                }
                if (ui.hovered(x, top, cellWidth, Ui.SLOT)) {
                    ui.hint(palette.displayName(), "A ready-made set of tier colours.");
                }
                if (ui.clicked(x, top, cellWidth, Ui.SLOT)) {
                    style.palette = palette;
                    style.tierColors.clear();
                }
                x += cellWidth + 3;
            }
        });

        y = line("Applies to", left, y, (x, c) -> {
            style.perListColors = ui.segments(x, c, List.of(false, true), style.perListColors, on -> on ? "Per list" : "All lists");
            if (style.perListColors) {
                x += 118;
                for (TierList list : TierList.values()) {
                    if (ui.slot(x, c, colourList == list)) {
                        colourList = list;
                    }
                    dev.tierprofiles.hud.Logos.draw(ui.graphics, list, x + 2, c - 8, 16, 1);
                    x += Ui.SLOT + 1;
                }
            }
        });

        y = line("Tier", left, y, (x, c) -> colourTier = ui.tierTabs(x, c, colourTier, tier -> style.color(colourList, tier)));

        int current = style.color(colourList, colourTier);
        int red = channel(left, y, "Red", redSlider, current >> 16 & 0xFF);
        y += ROW_STEP;
        int green = channel(left, y, "Green", greenSlider, current >> 8 & 0xFF);
        y += ROW_STEP;
        int blue = channel(left, y, "Blue", blueSlider, current & 0xFF);
        y += ROW_STEP;
        int edited = red << 16 | green << 8 | blue;
        if (edited != current) {
            setColour(style, edited);
        }

        return line("Hex", left, y, (x, c) -> {
            if (!hexBox.focused) {
                hexBox.value = Colors.toHex(style.color(colourList, colourTier));
            }
            hexBox.draw(ui, x, c - 10, 84, Ui.SLOT);
            Integer typed = Colors.parseHex(hexBox.value);
            if (hexBox.focused && typed != null) {
                setColour(style, typed);
            }
            if (ui.button(x + 88, c - 10, 46, Ui.SLOT, "Copy", false)) {
                minecraft.keyboardHandler.setClipboard(Colors.toHex(style.color(colourList, colourTier)));
            }
            if (ui.button(x + 138, c - 10, 50, Ui.SLOT, "Reset", false)) {
                style.tierColors.remove(colourTier.label());
                Map<String, Integer> forList = style.listColors.get(colourList);
                if (forList != null) {
                    forList.remove(colourTier.label());
                }
            }
        });
    }

    private int channel(int left, int y, String label, Object id, int value) {
        int center = y + ROW / 2;
        ui.text(label, left, center - 4, Theme.TEXT);
        return Math.round(ui.slider(id, left + LABEL_W, center, 200, value, 0, 255, Integer.toString(value)));
    }

    private void setColour(Style style, int rgb) {
        if (style.perListColors) {
            style.listColors.computeIfAbsent(colourList, unused -> new java.util.LinkedHashMap<>()).put(colourTier.label(), rgb);
        } else {
            style.tierColors.put(colourTier.label(), rgb);
        }
    }

    private int drawSurfaceSlots(int left, int y) {
        int center = y + ROW / 2;
        ui.text("Show on", left, center - 4, Theme.TEXT);
        int x = left + LABEL_W;
        Item[] icons = {Items.NAME_TAG, Items.BOOK, Items.PAPER, Items.SPYGLASS, Items.ARMOR_STAND};
        Surface[] surfaces = Surface.values();
        for (int i = 0; i < surfaces.length; i++) {
            if (ui.slot(x, center, surfaces[i] == surface)) {
                surface = surfaces[i];
            }
            ui.item(icons[i], x + 2, center - 8);
            if (ui.hovered(x, center - 10, Ui.SLOT, Ui.SLOT)) {
                ui.hint(surfaces[i].displayName(), "Everything below applies to this surface.");
            }
            x += Ui.SLOT + 1;
        }
        ui.text(surface.displayName(), x + 6, center - 4, Theme.MUTED);
        return y + ROW_STEP;
    }

    private int drawRows(List<Setting> settings, int left, int right, int y) {
        for (Setting setting : settings) {
            y = drawRow(setting, left, right, y);
        }
        return y;
    }

    private int drawRow(Setting setting, int left, int right, int y) {
        int height = ROW + (setting.rows() - 1) * ROW_STEP;
        int center = y + ROW / 2;
        if (ui.hovered(left, y, right - left, height)) {
            ui.hint(setting.label(), setting.hint());
        }
        ui.text(setting.label(), left, center - 4, Theme.TEXT);
        setting.control().draw(ui, left + LABEL_W, center, right - left - LABEL_W);
        return y + setting.rows() * ROW_STEP;
    }

    private int drawAdvancedRow(int left, int right, int y, String summary, boolean open) {
        int width = right - left;
        ui.graphics.fill(left, y, right, y + ROW, ui.hovered(left, y, width, ROW) ? Theme.SELECTED : 0xFF131A20);
        ui.graphics.outline(left, y, width, ROW, 0xFF2E3338);
        ui.text(open ? "‹" : "›", left + 7, y + 10, Theme.ACCENT);
        ui.text("Advanced", left + 16, y + 10, 0xFFFFFFFF);
        ui.text(summary, left + LABEL_W, y + 10, Theme.MUTED);
        if (ui.clicked(left, y, width, ROW)) {
            advancedOpen.put(tab, !open);
            scrollByTab.put(tab, 0);
        }
        return y + ROW_STEP;
    }

    private static final long TOOLTIP_DELAY_MILLIS = 250;
    private static final long FOOTER_NOTE_MILLIS = 3000;
    private static final long TOOLTIP_FADE_MILLIS = 120;
    private static final int TOOLTIP_WIDTH = 220;
    private String tooltipFor;
    private long tooltipSince;

    /**
     * A vanilla-style tooltip beside the mouse that says what the hovered setting does.
     * It waits a moment so it doesn't flicker past as the mouse crosses the screen, and
     * it's drawn last, so nothing covers it.
     */
    private void drawTooltip() {
        String title = ui.hoverTitle();
        String hint = ui.hoverHint();
        long now = System.currentTimeMillis();
        String key = title + "|" + hint;
        if (hint == null) {
            tooltipFor = null;
            return;
        }
        if (!key.equals(tooltipFor)) {
            tooltipFor = key;
            tooltipSince = now;
        }
        boolean reduceMotion = editor.staged().global.reduceMotion;
        long shown = now - tooltipSince - TOOLTIP_DELAY_MILLIS;
        if (shown < 0) {
            return;
        }
        float alpha = reduceMotion ? 1 : Math.min(1, shown / (float) TOOLTIP_FADE_MILLIS);

        List<String> lines = new ArrayList<>();
        ui.font.getSplitter().splitLines(hint, TOOLTIP_WIDTH - 12, net.minecraft.network.chat.Style.EMPTY)
                .forEach(line -> lines.add(line.getString()));
        Ui.Preview preview = ui.hoverPreview();
        int width = Math.max(ui.width(title), lines.stream().mapToInt(ui::width).max().orElse(0)) + 12;
        int height = 18 + lines.size() * 10;
        if (preview != null) {
            width = Math.max(width, preview.width() + 12);
            height += preview.height() + 6;
        }

        // Below and right of the pointer, flipped to stay on screen.
        int x = ui.mouseX + 12;
        int y = ui.mouseY + 12;
        if (x + width > virtualWidth - 4) {
            x = ui.mouseX - 12 - width;
        }
        if (y + height > virtualHeight - 4) {
            y = ui.mouseY - 12 - height;
        }
        x = Math.max(4, x);
        y = Math.max(4, y);

        ui.graphics.nextStratum();
        ui.graphics.fill(x, y, x + width, y + height, Theme.fade(0xF0100010, alpha));
        ui.graphics.outline(x, y, width, height, Theme.fade(0xFF3A0F75, alpha));
        ui.text(title, x + 6, y + 5, Theme.fade(0xFFFFFFFF, alpha));
        int lineY = y + 17;
        for (String line : lines) {
            ui.text(line, x + 6, lineY, Theme.fade(0xFFCFCFCF, alpha));
            lineY += 10;
        }
        if (preview != null && alpha >= 1) {
            preview.draw(ui, x + 6, lineY + 2);
        }
    }

    private long resultCount() {
        return catalog.all().stream().filter(setting -> setting.matches(search.value.trim())).count();
    }

    /**
     * Results use the same columns as a tab, so every control has its usual room. Where
     * a setting lives is written small under its name.
     */
    private int drawSearchResults(int left, int right, int y) {
        String query = search.value.trim();
        for (Setting setting : catalog.all()) {
            if (!setting.matches(query)) {
                continue;
            }
            int height = ROW + (setting.rows() - 1) * ROW_STEP;
            int center = y + ROW / 2;
            if (ui.hovered(left, y, right - left, height)) {
                ui.hint(setting.label(), setting.hint());
            }
            ui.text(setting.label(), left, center - 8, Theme.TEXT);
            ui.text(ui.font.plainSubstrByWidth(setting.path(), LABEL_W - 8), left, center + 3, Theme.MUTED);
            setting.control().draw(ui, left + LABEL_W, center, right - left - LABEL_W);
            y += setting.rows() * ROW_STEP;
        }
        return y;
    }

    void drawServerBox(Ui ui, int x, int y, int width) {
        serverBox.draw(ui, x, y - 10, Math.min(260, width), Ui.SLOT);
        String value = serverBox.value.trim();
        if (Global.isValidServerUrl(value)) {
            config().global.serverUrl = value;
        }
    }

    void pickColor(int rgb, IntConsumer onPick) {
        colorPicker = new ColorPicker(rgb, onPick);
    }

    boolean isListeningFor(KeyMapping key) {
        return listeningKey == key;
    }

    void listenForKey(KeyMapping key) {
        listeningKey = key;
    }

    private static String colourName(int rgb) {
        return switch (rgb) {
            case 0xFF7777 -> "Red";
            case 0xFFCF4A -> "Gold";
            case 0xC58BFF -> "Purple";
            case 0x68B5D5 -> "Blue";
            case 0x77D877 -> "Green";
            case 0xFF5FA2 -> "Pink";
            case 0xFFFFFF -> "White";
            default -> dev.tierprofiles.core.settings.Colors.toHex(rgb);
        };
    }

    private static String ruleEffect(HighlightRule rule) {
        List<String> parts = new ArrayList<>();
        parts.add(rule.color == null ? "No colour" : colourName(rule.color));
        if (rule.gradientEnd != null) {
            parts.add("gradient");
        }
        if (rule.bold) {
            parts.add("bold");
        }
        if (rule.star) {
            parts.add("star");
        }
        if (rule.glow) {
            parts.add("glow");
        }
        return String.join(", ", parts);
    }

    private int drawRules(int left, int right, int y, boolean advanced) {
        List<HighlightRule> rules = staged().rules;
        for (int i = 0; i < rules.size(); i++) {
            y = drawRuleRow(rules, i, left, right, y);
        }
        if (ui.button(left, y + 4, 90, Ui.SLOT, "Add a rule", false) && rules.size() < Settings.MAX_RULES) {
            rules.add(new HighlightRule());
            selectedRule = rules.size() - 1;
        }
        y += ROW + 4;

        if (selectedRule < rules.size()) {
            y = drawRuleEditor(rules.get(selectedRule), left, y);
            y = drawAdvancedRow(left, right, y, advanced ? "Back to the essentials" : "4 more: " + advancedSummary(), advanced);
            if (advanced) {
                y = drawRuleExtras(rules.get(selectedRule), left, y);
            }
        }
        return y;
    }

    private int drawRuleRow(List<HighlightRule> rules, int index, int left, int right, int y) {
        HighlightRule rule = rules.get(index);
        boolean selected = index == selectedRule;
        int width = right - left;
        if (selected) {
            ui.graphics.fill(left, y, right, y + RULE_ROW, Theme.SELECTED);
            ui.graphics.fill(left, y, left + 2, y + RULE_ROW, Theme.ACCENT);
        }
        if (ui.clicked(left, y, width - 106, RULE_ROW)) {
            selectedRule = index;
        }

        String comparison = switch (rule.comparison) {
            case AT -> "At ";
            case ABOVE -> "Above ";
            case AT_OR_ABOVE -> "From ";
        };
        String where = rule.list == null ? "any list" : dev.tierprofiles.core.layout.CardGrid.rowName(rule.list);
        ui.text((index + 1) + "  " + comparison + rule.minimumTier + ", " + where, left + 6, y + 8, rule.enabled ? Theme.TEXT : Theme.MUTED);
        int color = rule.color == null ? Theme.TEXT : Theme.opaque(rule.color);
        ui.text(ruleEffect(rule), left + LABEL_W + 40, y + 8, rule.enabled ? color : Theme.MUTED);

        rule.enabled = ui.toggle(right - 106, y + RULE_ROW / 2, rule.enabled);

        int x = right - 42;
        if (ui.button(x, y + 4, 13, 16, "↑", false) && index > 0) {
            rules.remove(index);
            rules.add(index - 1, rule);
            selectedRule = index - 1;
        }
        if (ui.button(x + 14, y + 4, 13, 16, "↓", false) && index < rules.size() - 1) {
            rules.remove(index);
            rules.add(index + 1, rule);
            selectedRule = index + 1;
        }
        if (ui.button(x + 28, y + 4, 13, 16, "x", false)) {
            rules.remove(index);
            selectedRule = Math.max(0, Math.min(selectedRule, rules.size() - 1));
            return y;
        }
        return y + RULE_ROW + 2;
    }

    @FunctionalInterface
    private interface LineControl {
        void draw(int x, int centerY);
    }

    private int line(String label, int left, int y, LineControl control) {
        int center = y + ROW / 2;
        ui.text(label, left, center - 4, Theme.TEXT);
        control.draw(left + LABEL_W, center);
        return y + ROW_STEP;
    }

    private int drawRuleEditor(HighlightRule rule, int left, int y) {
        y = line("If tier", left, y, (x, c) -> {
            rule.comparison = ui.segments(x, c, List.of(HighlightRule.Comparison.values()), rule.comparison, value -> switch (value) {
                case AT -> "At";
                case ABOVE -> "Above";
                case AT_OR_ABOVE -> "At or above";
            });
            int after = x + 170;
            rule.minimumTier = ui.cycleTier(after, c, rule.minimum(), staged().style.palette::color).label();
        });
        y = line("On list", left, y, (x, c) -> {
            if (ui.slot(x, c, rule.list == null)) {
                rule.list = null;
            }
            ui.graphics.centeredText(ui.font, "Any", x + Ui.SLOT / 2, c - 4, Theme.TEXT);
            x += Ui.SLOT + 1;
            for (TierList list : TierList.values()) {
                if (ui.slot(x, c, rule.list == list)) {
                    rule.list = list;
                }
                dev.tierprofiles.hud.Logos.draw(ui.graphics, list, x + 2, c - 8, 16, 1);
                x += Ui.SLOT + 1;
            }
        });
        y = line("Name colour", left, y, (x, c) -> {
            for (int color : List.of(0xFF7777, 0xFFCF4A, 0xC58BFF, 0x68B5D5, 0x77D877, 0xFF5FA2, 0xFFFFFF)) {
                if (ui.swatch(x, c, color, rule.color != null && rule.color == color)) {
                    rule.color = color;
                }
                x += Ui.SLOT + 1;
            }
            int shown = rule.color == null ? 0xFFFFFF : rule.color;
            if (ui.button(x + 4, c - 10, 56, Ui.SLOT, "Custom", false)) {
                pickColor(shown, picked -> rule.color = picked);
            }
        });
        return line("Style", left, y, (x, c) -> {
            int cell = 50;
            rule.bold = styleCell(x, c, cell, "Bold", rule.bold);
            rule.star = styleCell(x + cell + 1, c, cell, "Star", rule.star);
            rule.glow = styleCell(x + 2 * (cell + 1), c, cell, "Glow", rule.glow);
        });
    }

    private int drawRuleExtras(HighlightRule rule, int left, int y) {
        y = line("Gradient", left, y, (x, c) -> {
            boolean on = ui.toggle(x, c, rule.gradientEnd != null);
            if (on && rule.gradientEnd == null) {
                rule.gradientEnd = 0xC58BFF;
            } else if (!on) {
                rule.gradientEnd = null;
            }
            if (rule.gradientEnd != null && ui.swatch(x + 64, c, rule.gradientEnd, false)) {
                pickColor(rule.gradientEnd, picked -> rule.gradientEnd = picked);
            }
        });
        y = line("In kit", left, y, (x, c) -> {
            if (ui.slot(x, c, rule.kit == null)) {
                rule.kit = null;
            }
            ui.graphics.centeredText(ui.font, "Any", x + Ui.SLOT / 2, c - 4, Theme.TEXT);
            x += Ui.SLOT + 1;
            for (String kit : Kits.known().subList(0, 10)) {
                if (ui.slot(x, c, kit.equals(rule.kit))) {
                    rule.kit = kit;
                }
                drawKit(ui, kit, x + 2, c - 8);
                x += Ui.SLOT + 1;
            }
        });
        y = line("Ignore retired", left, y, (x, c) -> rule.ignoreRetired = ui.toggle(x, c, rule.ignoreRetired));
        return line("Judge by peak", left, y, (x, c) -> rule.usePeak = ui.toggle(x, c, rule.usePeak));
    }

    private boolean styleCell(int x, int center, int width, String label, boolean value) {
        int y = center - Ui.SLOT / 2;
        ui.graphics.fill(x, y, x + width, y + Ui.SLOT, ui.hovered(x, y, width, Ui.SLOT) ? Theme.SELECTED : Theme.ROW);
        if (value) {
            ui.frame(x, y, width, Ui.SLOT);
        }
        ui.graphics.centeredText(ui.font, label, x + width / 2, center - 4, value ? 0xFFFFFFFF : Theme.MUTED);
        return ui.clicked(x, y, width, Ui.SLOT) != value;
    }

    private int drawSharing(int left, int right, int y) {
        int x = left + LABEL_W;
        int width = right - x;

        y = line("Setup name", left, y, (cx, c) -> {
            nameBox.draw(ui, cx, c - 10, width - 74, Ui.SLOT);
            if (ui.button(right - 70, c - 10, 70, Ui.SLOT, "Rename", false) && editor.renameProfile(config().activeProfile, nameBox.value.trim())) {
                nameBox.value = "";
            }
        });
        y = line("Slot icon", left, y, (cx, c) -> {
            for (int i = 0; i <= Settings.MAX_ICON; i++) {
                if (ui.slot(cx, c, staged().icon == i)) {
                    staged().icon = i;
                }
                ui.item(SETUP_ICONS.get(i), cx + 2, c - 8);
                cx += Ui.SLOT + 1;
            }
        });
        y = line("Share code", left, y, (cx, c) -> {
            ui.graphics.fill(cx, c - 10, cx + width - 74, c + 10, Theme.ROW);
            ui.text(ui.font.plainSubstrByWidth(shareCode(), width - 86), cx + 5, c - 4, Theme.ACCENT);
            if (ui.button(right - 70, c - 10, 70, Ui.SLOT, "Copy", false)) {
                copyCode();
            }
        });
        y = line("Import", left, y, (cx, c) -> {
            codeBox.draw(ui, cx, c - 10, width - 74, Ui.SLOT);
            if (ui.button(right - 70, c - 10, 70, Ui.SLOT, "Preview", false)) {
                previewCode();
            }
        });
        y = line("Include", left, y, (cx, c) -> {
            int cell = 62;
            importFilters = styleCell(cx, c, cell, "Filters", importFilters);
            importColours = styleCell(cx + cell + 1, c, cell, "Colours", importColours);
            importRules = styleCell(cx + 2 * (cell + 1), c, cell, "Rules", importRules);
            importCard = styleCell(cx + 3 * (cell + 1), c, cell, "Card", importCard);
        });

        if (!sharingMessage.isEmpty()) {
            ui.text(sharingMessage, x, y + 2, Theme.MUTED);
            y += 16;
        }
        if (pendingImport != null) {
            if (ui.button(x, y, 110, Ui.SLOT, "Use this code", true)) {
                useImport();
            }
            y += ROW;
        }
        return y;
    }

    /** Takes only the parts you ticked, so a code can't touch what you want to keep. */
    private void useImport() {
        Settings target = staged();
        Settings incoming = ConfigStore.copy(pendingImport);
        if (importFilters) {
            target.filters = incoming.filters;
            target.surfaces = incoming.surfaces;
        }
        if (importColours) {
            target.style = incoming.style;
        }
        if (importRules) {
            target.rules = incoming.rules;
        }
        if (importCard) {
            target.card = incoming.card;
        }
        pendingImport = null;
        codeBox.value = "";
        sharingMessage = "Imported. Apply to keep it.";
    }

    private String shareCode() {
        try {
            return ShareCode.encode(staged());
        } catch (IOException e) {
            return "";
        }
    }

    private int drawSetups(int left, int right, int y) {
        for (String name : new ArrayList<>(config().profiles.keySet())) {
            boolean active = name.equals(config().activeProfile);
            ui.graphics.fill(left, y, right, y + RULE_ROW, active ? Theme.SELECTED : Theme.ROW);
            ui.text(name, left + 6, y + 8, active ? 0xFFFFFFFF : Theme.TEXT);
            if (!active && ui.button(right - 112, y + 3, 54, 18, "Use", false)) {
                editor.switchProfile(name);
            }
            if (ui.button(right - 56, y + 3, 54, 18, "Delete", false)) {
                editor.deleteProfile(name);
            }
            y += RULE_ROW + 2;
        }
        return y + 6;
    }

    private void copyCode() {
        try {
            minecraft.keyboardHandler.setClipboard(ShareCode.encode(staged()));
            sharingMessage = "Copied. Send it to a friend.";
        } catch (IOException e) {
            sharingMessage = "Could not make a code.";
        }
    }

    /** Shows what would change first, because a code comes from someone else. */
    private void previewCode() {
        try {
            pendingImport = ShareCode.decode(codeBox.value);
            ConfigEditor comparison = new ConfigEditor(config());
            comparison.staged().profiles.put(comparison.staged().activeProfile, ConfigStore.copy(pendingImport));
            sharingMessage = "This code would change " + comparison.changeCount() + " settings.";
        } catch (IOException e) {
            pendingImport = null;
            sharingMessage = e.getMessage();
        }
    }

    /** The truth behind the card's Unranked: what each list really did. */
    private int drawDiagnostics(int left, int y) {
        ui.text("Diagnostics", left, y + 2, Theme.ACCENT);
        y += 16;
        for (Diagnostics.Row row : Diagnostics.lists()) {
            int center = y + ROW / 2;
            dev.tierprofiles.hud.Logos.draw(ui.graphics, row.list(), left, center - 8, 16, 1);
            ui.text(row.list().displayName(), left + 20, center - 4, Theme.TEXT);
            ui.text(row.text(), left + LABEL_W, center - 4, row.healthy() ? Theme.GOOD : Theme.BAD);
            y += ROW_STEP;
        }
        for (String line : Diagnostics.server()) {
            ui.text(line, left, y, Theme.MUTED);
            y += 12;
        }
        return y + 8;
    }

    /** The bottom row: your nine setups, like a hotbar, then Discard and Apply. */
    private void drawHotbar() {
        int y = virtualHeight - FOOTER_Y;
        int center = y + 12;
        int x = MARGIN + RAIL_W;

        List<String> names = new ArrayList<>(config().profiles.keySet());
        for (int i = 0; i < 9; i++) {
            boolean used = i < names.size();
            boolean active = used && names.get(i).equals(config().activeProfile);
            boolean clicked = ui.slot(x, center, active);
            if (clicked && used) {
                editor.switchProfile(names.get(i));
            } else if (clicked) {
                addSetup(i);
            }
            if (used) {
                int icon = config().profiles.get(names.get(i)).icon;
                ui.item(SETUP_ICONS.get(icon), x + 2, center - 8);
            }
            if (ui.hovered(x, center - 10, Ui.SLOT, Ui.SLOT)) {
                ui.hint(used ? names.get(i) : "Empty slot", used ? "Press " + (i + 1) + " to use this setup." : "Click to save a copy of this setup here.");
            }
            x += Ui.SLOT + 1;
        }

        ui.text(config().activeProfile, x + 8, y + 1, 0xFFFFFFFF);
        ui.text("Press 1 to 9 to swap setups", x + 8, y + 13, Theme.MUTED);

        int right = virtualWidth - MARGIN;
        int changes = editor.changeCount();
        long noteAge = System.currentTimeMillis() - footerNoteAt;
        if (changes > 0) {
            String message = changes == 1 ? "1 change staged" : changes + " changes staged";
            ui.text(message, right - 136 - ui.width(message), center - 4, Theme.BETA);
        } else if (footerNote != null && noteAge < FOOTER_NOTE_MILLIS) {
            // What Apply or Discard just did, fading out over its last half second.
            float alpha = Math.min(1, (FOOTER_NOTE_MILLIS - noteAge) / 500f);
            ui.text(footerNote, right - 136 - ui.width(footerNote), center - 4, Theme.fade(footerNoteColor, alpha));
        }
        if (ui.button(right - 56, y, 56, FOOTER_BUTTON, "Apply", true)) {
            apply();
        }
        if (ui.button(right - 118, y, 56, FOOTER_BUTTON, "Discard", false)) {
            discard();
        }
    }

    private void addSetup(int slot) {
        String name = "Setup " + (slot + 1);
        if (editor.createProfile(name, staged())) {
            config().profiles.get(name).icon = Math.min(slot, Settings.MAX_ICON);
            editor.switchProfile(name);
        }
    }

    private void apply() {
        int changes = editor.changeCount();
        String before = ConfigStore.GSON.toJson(TierProfiles.config().global.cosmetics);
        TierProfiles.publish(editor.apply());
        if (!before.equals(ConfigStore.GSON.toJson(TierProfiles.config().global.cosmetics))) {
            TierProfiles.saveCosmetics();
        }
        note(changes == 0 ? "Nothing to apply" : changes == 1 ? "✔ 1 change applied" : "✔ " + changes + " changes applied",
                changes == 0 ? Theme.MUTED : Theme.GOOD);
    }

    private void discard() {
        int changes = editor.changeCount();
        note(changes == 0 ? "Nothing to discard" : changes == 1 ? "1 change discarded" : changes + " changes discarded",
                changes == 0 ? Theme.MUTED : Theme.BAD);
        editor.discard();
        serverBox.value = config().global.serverUrl;
        pendingImport = null;
    }

    /** A short message beside the buttons saying what Apply or Discard did. */
    private void note(String message, int color) {
        footerNote = message;
        footerNoteColor = color;
        footerNoteAt = System.currentTimeMillis();
    }

    public String footerNoteForTest() {
        return System.currentTimeMillis() - footerNoteAt < FOOTER_NOTE_MILLIS ? footerNote : null;
    }

    private void drawConfirmClose() {
        int boxWidth = 250;
        int boxHeight = 66;
        int x = (virtualWidth - boxWidth) / 2;
        int y = (virtualHeight - boxHeight) / 2;
        ui.setBlocked(false);
        ui.graphics.fill(0, 0, virtualWidth, virtualHeight, 0x88000000);
        ui.panel(x, y, boxWidth, boxHeight);
        ui.text("You have changes that aren't applied.", x + 10, y + 10, Theme.TEXT);
        if (ui.button(x + 10, y + 36, 72, Ui.SLOT, "Apply", true)) {
            apply();
            finish();
        }
        if (ui.button(x + 88, y + 36, 72, Ui.SLOT, "Discard", false)) {
            finish();
        }
        if (ui.button(x + 166, y + 36, 74, Ui.SLOT, "Keep editing", false)) {
            confirmingClose = false;
        }
        ui.consumeClick();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKey != null) {
            if (event.input() != InputConstants.KEY_ESCAPE) {
                listeningKey.setKey(InputConstants.getKey(event));
                KeyMapping.resetMapping();
                minecraft.options.save();
            }
            listeningKey = null;
            return true;
        }
        if (event.input() == InputConstants.KEY_ESCAPE) {
            return onEscape();
        }
        if (search.onKey(event) || serverBox.onKey(event) || nameBox.onKey(event) || codeBox.onKey(event) || hexBox.onKey(event)) {
            return true;
        }
        if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk()) {
            search.focused = true;
            return true;
        }
        return swapSetupByNumber(event) || super.keyPressed(event);
    }

    private boolean onEscape() {
        if (colorPicker != null) {
            colorPicker = null;
        } else if (isSearching()) {
            search.value = "";
            search.focused = false;
        } else if (confirmingClose) {
            confirmingClose = false;
        } else {
            onClose();
        }
        return true;
    }

    private boolean swapSetupByNumber(KeyEvent event) {
        int slot = event.key() - InputConstants.KEY_1;
        if (slot < 0 || slot > 8) {
            return false;
        }
        List<String> names = new ArrayList<>(config().profiles.keySet());
        if (slot < names.size()) {
            editor.switchProfile(names.get(slot));
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return search.onChar(event) || serverBox.onChar(event) || nameBox.onChar(event) || codeBox.onChar(event) || hexBox.onChar(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ui.onMousePressed(event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ui.onMouseReleased();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - visibleHeight + 6);
        int scroll = scrollByTab.getOrDefault(tab, 0) - (int) (scrollY * SCROLL_STEP);
        scrollByTab.put(tab, Math.clamp(scroll, 0, max));
        return true;
    }

    @Override
    public void onClose() {
        if (editor.isDirty()) {
            confirmingClose = true;
        } else {
            finish();
        }
    }

    private void finish() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For tests: how many settings exist in total. */
    public int settingCount() {
        return catalog.all().size();
    }
}
