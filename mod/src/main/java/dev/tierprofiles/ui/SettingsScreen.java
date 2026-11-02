package dev.tierprofiles.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.config.ConfigEditor;
import dev.tierprofiles.core.config.ConfigStore;
import dev.tierprofiles.core.config.ShareCode;
import dev.tierprofiles.core.logic.HighlightEngine;
import dev.tierprofiles.core.model.Kits;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.ConfigFile;
import dev.tierprofiles.core.settings.Global;
import dev.tierprofiles.core.settings.HighlightRule;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.core.settings.Surface;
import dev.tierprofiles.surface.Decorator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The settings screen. Changes go to a staged copy and do nothing until Apply. Each tab
 * opens on a few essentials, with everything else under one Advanced section, and the
 * search box looks through every setting at once.
 */
public final class SettingsScreen extends Screen {
    private static final int MARGIN = 6;
    private static final int HEADER = 46;
    private static final int FOOTER = 24;
    private static final int TAB_HEIGHT = 20;
    private static final int PREVIEW_MIN_WIDTH = 520;
    private static final int PREVIEW_WIDTH = 170;
    private static final int SCROLL_STEP = 19;
    private static final int LABEL_WIDTH_GUESS = 8;
    private static final int RULE_ROW = 16;

    private final Screen parent;
    private final ConfigEditor editor = new ConfigEditor(TierProfiles.config());
    private final Ui ui = new Ui();
    private final SettingsCatalog catalog = new SettingsCatalog(editor::staged, this);

    private SettingsTab tab = SettingsTab.TAGS;
    private Surface surface = Surface.NAMETAG;
    private final Set<String> openAdvanced = new HashSet<>();
    private final Map<SettingsTab, Integer> scrollByTab = new LinkedHashMap<>();
    private int contentHeight;

    private EditBox search;
    private EditBox serverBox;
    private EditBox nameBox;
    private EditBox codeBox;
    private boolean serverBoxWanted;

    private ColorPicker colorPicker;
    private KeyMapping listeningKey;
    private boolean confirmingClose;
    private int selectedRule;
    private String sharingMessage = "";

    public SettingsScreen(Screen parent) {
        super(Component.literal("TierProfiles"));
        this.parent = parent;
    }

    private Settings staged() {
        return editor.staged().active();
    }

    private ConfigFile config() {
        return editor.staged();
    }

    @Override
    protected void init() {
        search = box(160, "Search every setting");
        search.setResponder(text -> scrollByTab.clear());
        serverBox = box(120, "");
        serverBox.setMaxLength(200);
        serverBox.setValue(config().global.serverUrl);
        nameBox = box(100, "New profile name");
        nameBox.setMaxLength(ConfigFile.MAX_NAME_LENGTH);
        codeBox = box(160, "Paste a share code");
        codeBox.setMaxLength(65536);
    }

    private EditBox box(int width, String hint) {
        EditBox box = new EditBox(font, 0, 0, width, 12, Component.literal(hint));
        box.setBordered(false);
        box.setHint(Component.literal(hint));
        box.setMaxLength(100);
        addRenderableWidget(box);
        return box;
    }

    private void placeBox(EditBox box, int x, int y, int width) {
        box.setX(x + 4);
        box.setY(y + 3);
        box.setWidth(width - 8);
        ui.graphics.fill(x, y, x + width, y + 14, Theme.ROW);
        ui.graphics.outline(x, y, width, 14, box.isFocused() ? Theme.ACCENT : Theme.BORDER);
        box.visible = true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Theme.BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ui.begin(graphics, mouseX, mouseY);
        ui.setBlocked(colorPicker != null || confirmingClose);
        hideBoxes();

        drawHeader();
        drawBody();
        drawFooter();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();

        if (colorPicker != null && colorPicker.draw(ui, width, height)) {
            colorPicker = null;
        }
        if (confirmingClose) {
            drawConfirmClose();
        }
        ui.end();
    }

    private void hideBoxes() {
        nameBox.visible = false;
        codeBox.visible = false;
        serverBox.visible = false;
        serverBoxWanted = false;
    }

    private void drawHeader() {
        ui.text(Component.literal("TierProfiles").withStyle(s -> s.withBold(true)), MARGIN + 2, 8, 0xFFFFFFFF);
        ui.text("Settings: " + config().activeProfile, MARGIN + 80, 8, Theme.MUTED);

        int searchWidth = Math.min(170, width / 3);
        placeBox(search, width - MARGIN - searchWidth, 4, searchWidth);

        SettingsTab[] tabs = SettingsTab.values();
        int gap = 3;
        int tabWidth = (width - 2 * MARGIN - gap * (tabs.length - 1)) / tabs.length;
        int x = MARGIN;
        for (SettingsTab candidate : tabs) {
            boolean on = candidate == tab && !isSearching();
            ui.graphics.fill(x, 22, x + tabWidth, 22 + TAB_HEIGHT, on ? Theme.SELECTED : Theme.ROW);
            ui.graphics.outline(x, 22, tabWidth, TAB_HEIGHT, on ? Theme.ACCENT : Theme.BORDER);
            ui.graphics.item(new ItemStack(candidate.icon()), x + 3, 24);
            if (tabWidth > 52) {
                ui.text(candidate.title(), x + 21, 28, on ? 0xFFFFFFFF : Theme.MUTED);
            }
            if (ui.clicked(x, 22, tabWidth, TAB_HEIGHT)) {
                tab = candidate;
                search.setValue("");
            }
            x += tabWidth + gap;
        }
    }

    private boolean isSearching() {
        return !search.getValue().isBlank();
    }

    private void drawBody() {
        int top = HEADER;
        int bottom = height - FOOTER;
        boolean preview = width >= PREVIEW_MIN_WIDTH && !isSearching() && (tab == SettingsTab.TAGS || tab == SettingsTab.COLOURS || tab == SettingsTab.RULES);
        int listWidth = preview ? width - 2 * MARGIN - PREVIEW_WIDTH - MARGIN : width - 2 * MARGIN;

        ui.graphics.fill(MARGIN, top, MARGIN + listWidth, bottom, Theme.PANEL);
        ui.graphics.outline(MARGIN, top, listWidth, bottom - top, Theme.BORDER);

        int scroll = scrollByTab.getOrDefault(tab, 0);
        ui.clip(MARGIN + 1, top + 1, MARGIN + listWidth - 1, bottom - 1);
        int y = top + 3 - scroll;
        int left = MARGIN + 4;
        int right = MARGIN + listWidth - 8;

        if (isSearching()) {
            y = drawSearchResults(left, right, y);
        } else {
            switch (tab) {
                case RULES -> y = drawRules(left, right, y);
                case SHARING -> y = drawSharing(left, right, y);
                case PROFILE -> y = drawProfile(left, right, y);
                case TAGS -> {
                    y = drawSurfacePicker(left, right, y);
                    y = drawSettings(catalog.forTab(tab, surface), left, right, y);
                }
                default -> y = drawSettings(catalog.forTab(tab, surface), left, right, y);
            }
        }
        ui.unclip();
        contentHeight = y + scroll - top;

        if (preview) {
            drawPreview(MARGIN + listWidth + MARGIN, top, PREVIEW_WIDTH, bottom - top);
        }
        if (serverBoxWanted) {
            serverBox.visible = true;
        }
    }

    /** A row of surfaces. The list below always edits the one chosen here. */
    private int drawSurfacePicker(int left, int right, int y) {
        int count = Surface.values().length;
        int each = (right - left) / count;
        int x = left;
        for (Surface candidate : Surface.values()) {
            boolean on = candidate == surface;
            ui.graphics.fill(x, y, x + each - 2, y + 15, on ? Theme.SELECTED : Theme.ROW);
            ui.graphics.outline(x, y, each - 2, 15, on ? Theme.ACCENT : Theme.BORDER);
            ui.graphics.centeredText(font, candidate.displayName(), x + each / 2 - 1, y + 4, on ? 0xFFFFFFFF : Theme.MUTED);
            if (ui.clicked(x, y, each - 2, 15)) {
                surface = candidate;
            }
            x += each;
        }
        return y + 19;
    }

    private int drawSettings(List<Setting> settings, int left, int right, int y) {
        Map<String, List<Setting>> essentials = new LinkedHashMap<>();
        List<Setting> advanced = new ArrayList<>();
        for (Setting setting : settings) {
            if (setting.advanced()) {
                advanced.add(setting);
            } else {
                essentials.computeIfAbsent(setting.group(), unused -> new ArrayList<>()).add(setting);
            }
        }

        for (Map.Entry<String, List<Setting>> group : essentials.entrySet()) {
            ui.text(group.getKey(), left, y + 4, Theme.ACCENT);
            y += 14;
            for (Setting setting : group.getValue()) {
                y = drawRow(setting, left, right, y, false);
            }
            y += 4;
        }

        if (!advanced.isEmpty()) {
            y = drawAdvanced(advanced, left, right, y);
        }
        return y;
    }

    private int drawAdvanced(List<Setting> advanced, int left, int right, int y) {
        String key = tab.name();
        boolean open = openAdvanced.contains(key);
        ui.graphics.fill(left - 1, y, right + 3, y + 15, Theme.ROW);
        ui.graphics.outline(left - 1, y, right - left + 4, 15, Theme.BORDER);
        ui.text((open ? "▼ " : "▶ ") + "Advanced", left + 3, y + 4, Theme.TEXT);
        String count = advanced.size() + " settings";
        ui.text(count, right - ui.width(count), y + 4, Theme.MUTED);
        if (ui.clicked(left - 1, y, right - left + 4, 15)) {
            if (open) {
                openAdvanced.remove(key);
            } else {
                openAdvanced.add(key);
            }
        }
        y += 18;

        if (open) {
            String lastGroup = "";
            for (Setting setting : advanced) {
                if (!setting.group().equals(lastGroup)) {
                    lastGroup = setting.group();
                    ui.text(lastGroup, left, y + 4, Theme.MUTED);
                    y += 14;
                }
                y = drawRow(setting, left, right, y, false);
            }
        }
        return y;
    }

    private int drawRow(Setting setting, int left, int right, int y, boolean withPath) {
        boolean hover = ui.hovered(left - 1, y, right - left + 4, Ui.ROW_HEIGHT);
        if (hover) {
            ui.graphics.fill(left - 1, y, right + 3, y + Ui.ROW_HEIGHT, 0x22FFFFFF);
            ui.hint(setting.hint());
        }
        int center = y + Ui.ROW_HEIGHT / 2;
        ui.text(setting.label(), left + 3, center - 4, Theme.TEXT);
        if (withPath) {
            ui.text(setting.path(), left + 3 + LABEL_WIDTH_GUESS * 16, center - 4, Theme.MUTED);
        }
        setting.control().draw(ui, right, center);
        return y + Ui.ROW_HEIGHT;
    }

    private int drawSearchResults(int left, int right, int y) {
        String query = search.getValue().trim();
        List<Setting> matches = new ArrayList<>();
        for (Setting setting : catalog.all()) {
            if (setting.matches(query)) {
                matches.add(setting);
            }
        }
        ui.text(matches.size() + " results for \"" + query + "\"", left, y + 4, Theme.TEXT);
        y += 16;
        for (Setting setting : matches) {
            y = drawRow(setting, left, right, y, right - left > 330);
        }
        return y;
    }

    private void drawPreview(int x, int top, int width, int height) {
        ui.panel(x, top, width, height);
        ui.text("Live preview", x + 5, top + 5, Theme.ACCENT);

        Settings settings = staged();
        Surface shown = surface == Surface.LOOK_AT || surface == Surface.OVERLAY ? Surface.NAMETAG : surface;
        ui.graphics.enableScissor(x + 1, top + 1, x + width - 1, top + height - 1);
        int y = top + 19;
        int tagged = 0;
        for (PlayerProfile player : SamplePlayers.all()) {
            Component line = Decorator.preview(player, Component.literal(player.name()), shown, settings);
            ui.text(line, x + 5, y, 0xFFFFFFFF);
            if (!line.getString().equals(player.name())) {
                tagged += 1;
            }
            y += 12;
        }
        ui.graphics.disableScissor();
        ui.text(tagged + " of " + SamplePlayers.all().size() + " tagged", x + 5, top + height - 12, Theme.MUTED);
    }

    void drawPriorityHint(Ui ui, int right, int y) {
        Settings settings = staged();
        List<TierList> order = settings.filters.listPriority;
        int x = right - 5 * 46;
        for (int i = 0; i < order.size(); i++) {
            TierList list = order.get(i);
            boolean hover = ui.hovered(x, y - 7, 44, 14);
            ui.graphics.fill(x, y - 7, x + 44, y + 7, hover ? Theme.SELECTED : Theme.ROW);
            ui.graphics.outline(x, y - 7, 44, 14, Theme.BORDER);
            ui.graphics.centeredText(font, list.shortName(), x + 22, y - 3, list.isBeta() ? Theme.BETA : Theme.TEXT);
            if (ui.clicked(x, y - 7, 44, 14) && i > 0) {
                order.remove(i);
                order.add(i - 1, list);
            }
            x += 46;
        }
        ui.hint("Click a list to move it up. First wins.");
    }

    void drawServerBox(Ui ui, int right, int y) {
        int boxWidth = Math.min(190, width / 3);
        placeBox(serverBox, right - boxWidth, y - 7, boxWidth);
        serverBoxWanted = true;
        String value = serverBox.getValue().trim();
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

    private int drawRules(int left, int right, int y) {
        Settings settings = staged();
        List<HighlightRule> rules = settings.rules;
        ui.text("Rules run top to bottom, and the first match wins.", left, y + 3, Theme.MUTED);
        y += 15;

        for (int i = 0; i < rules.size(); i++) {
            y = drawRuleRow(rules, i, left, right, y);
        }
        if (ui.button(left, y + 2, 90, 14, "Add a rule", false) && rules.size() < Settings.MAX_RULES) {
            rules.add(new HighlightRule());
            selectedRule = rules.size() - 1;
        }
        y += 20;

        if (selectedRule < rules.size() && !rules.isEmpty()) {
            y = drawRuleEditor(rules.get(selectedRule), left, right, y);
        }
        return y;
    }

    private int drawRuleRow(List<HighlightRule> rules, int index, int left, int right, int y) {
        HighlightRule rule = rules.get(index);
        boolean selected = index == selectedRule;
        ui.graphics.fill(left - 1, y, right + 3, y + RULE_ROW - 1, selected ? Theme.SELECTED : Theme.ROW);
        if (ui.clicked(left - 1, y, right - left - 60, RULE_ROW)) {
            selectedRule = index;
        }

        String listName = rule.list == null ? "any list" : rule.list.displayName();
        String kitName = rule.kit == null ? "" : " in " + Kits.displayName(rule.kit);
        int color = rule.color == null ? Theme.TEXT : Theme.opaque(rule.color);
        ui.text((index + 1) + ". " + rule.minimumTier + " and above on " + listName + kitName, left + 3, y + 4, rule.enabled ? color : Theme.MUTED);

        int x = right - 42;
        if (ui.button(x, y + 1, 13, 13, "↑", false) && index > 0) {
            rules.remove(index);
            rules.add(index - 1, rule);
            selectedRule = index - 1;
        }
        if (ui.button(x + 15, y + 1, 13, 13, "↓", false) && index < rules.size() - 1) {
            rules.remove(index);
            rules.add(index + 1, rule);
            selectedRule = index + 1;
        }
        if (ui.button(x + 30, y + 1, 13, 13, "x", false)) {
            rules.remove(index);
            selectedRule = Math.max(0, selectedRule - 1);
            return y;
        }
        return y + RULE_ROW;
    }

    private int drawRuleEditor(HighlightRule rule, int left, int right, int y) {
        ui.text("Edit rule " + (selectedRule + 1), left, y + 4, Theme.ACCENT);
        y += 15;

        y = line("Rule is on", left, right, y, (r, c) -> rule.enabled = ui.toggle(r, c, rule.enabled));
        y = line("Tier is at or above", left, right, y, (r, c) -> {
            Tier picked = ui.tierPicker(r, c, rule.minimum(), staged().style.palette::color);
            if (picked != null) {
                rule.minimumTier = picked.label();
            }
        });
        y = line("On list", left, right, y, (r, c) -> {
            List<TierList> lists = new ArrayList<>();
            lists.add(null);
            lists.addAll(List.of(TierList.values()));
            rule.list = ui.cycle(r, c, 104, lists, rule.list, list -> list == null ? "Any list" : list.displayName());
        });
        y = line("In kit", left, right, y, (r, c) -> {
            List<String> kits = new ArrayList<>();
            kits.add(null);
            kits.addAll(Kits.known());
            rule.kit = ui.cycle(r, c, 104, kits, rule.kit, kit -> kit == null ? "Any kit" : Kits.displayName(kit));
        });
        y = line("Name colour", left, right, y, (r, c) -> {
            int rgb = rule.color == null ? 0xFFFFFF : rule.color;
            if (ui.swatch(r - 14, c - 7, 14, rgb)) {
                pickColor(rgb, picked -> rule.color = picked);
            }
        });
        y = line("Gradient", left, right, y, (r, c) -> {
            boolean on = ui.toggle(r - 20, c, rule.gradientEnd != null);
            if (on && rule.gradientEnd == null) {
                rule.gradientEnd = 0xAA00FF;
            } else if (!on) {
                rule.gradientEnd = null;
            }
            if (rule.gradientEnd != null && ui.swatch(r - 14, c - 7, 14, rule.gradientEnd)) {
                pickColor(rule.gradientEnd, picked -> rule.gradientEnd = picked);
            }
        });
        y = line("Bold", left, right, y, (r, c) -> rule.bold = ui.toggle(r, c, rule.bold));
        y = line("Glow around tag", left, right, y, (r, c) -> rule.glow = ui.toggle(r, c, rule.glow));
        y = line("Star before name", left, right, y, (r, c) -> rule.star = ui.toggle(r, c, rule.star));
        y = line("Ignore retired tiers", left, right, y, (r, c) -> rule.ignoreRetired = ui.toggle(r, c, rule.ignoreRetired));
        y = line("Judge by peak", left, right, y, (r, c) -> rule.usePeak = ui.toggle(r, c, rule.usePeak));

        int caught = 0;
        for (PlayerProfile player : SamplePlayers.all()) {
            if (!HighlightEngine.evaluate(player, staged().rules, staged().filters).isPlain()) {
                caught += 1;
            }
        }
        ui.text("Rules style " + caught + " of " + SamplePlayers.all().size() + " sample players.", left, y + 4, Theme.MUTED);
        return y + 16;
    }

    @FunctionalInterface
    private interface LineControl {
        void draw(int right, int centerY);
    }

    private int line(String label, int left, int right, int y, LineControl control) {
        ui.text(label, left + 3, y + Ui.ROW_HEIGHT / 2 - 4, Theme.TEXT);
        control.draw(right, y + Ui.ROW_HEIGHT / 2);
        return y + Ui.ROW_HEIGHT;
    }

    private int drawSharing(int left, int right, int y) {
        ui.text("Named profiles", left, y + 4, Theme.ACCENT);
        y += 15;

        for (String name : new ArrayList<>(config().profiles.keySet())) {
            boolean active = name.equals(config().activeProfile);
            ui.graphics.fill(left - 1, y, right + 3, y + RULE_ROW - 1, active ? Theme.SELECTED : Theme.ROW);
            ui.text(name, left + 3, y + 4, active ? 0xFFFFFFFF : Theme.TEXT);
            if (!active && ui.button(right - 90, y + 1, 44, 13, "Use", false)) {
                editor.switchProfile(name);
            }
            if (ui.button(right - 44, y + 1, 44, 13, "Delete", false)) {
                editor.deleteProfile(name);
                sharingMessage = "";
            }
            y += RULE_ROW;
        }

        y += 3;
        int boxWidth = Math.min(150, right - left - 130);
        placeBox(nameBox, left, y, boxWidth);
        if (ui.button(left + boxWidth + 4, y, 60, 14, "New", false)) {
            if (editor.createProfile(nameBox.getValue().trim(), staged())) {
                nameBox.setValue("");
            }
        }
        if (ui.button(left + boxWidth + 68, y, 60, 14, "Rename", false)) {
            if (editor.renameProfile(config().activeProfile, nameBox.getValue().trim())) {
                nameBox.setValue("");
            }
        }
        y += 24;

        ui.text("Share code", left, y + 4, Theme.ACCENT);
        y += 15;
        if (ui.button(left, y, 120, 14, "Copy my code", false)) {
            try {
                minecraft.keyboardHandler.setClipboard(ShareCode.encode(staged()));
                sharingMessage = "Copied. Send it to a friend.";
            } catch (IOException e) {
                sharingMessage = "Could not make a code.";
            }
        }
        y += 19;

        int importWidth = right - left - 84;
        placeBox(codeBox, left, y, importWidth);
        if (ui.button(left + importWidth + 4, y, 80, 14, "Import", false)) {
            importCode();
        }
        y += 19;
        ui.text(sharingMessage, left, y + 2, Theme.MUTED);
        return y + 14;
    }

    private void importCode() {
        try {
            Settings imported = ShareCode.decode(codeBox.getValue());
            config().profiles.put(config().activeProfile, ConfigStore.copy(imported));
            codeBox.setValue("");
            sharingMessage = "Imported into this profile. Apply to keep it.";
        } catch (IOException e) {
            sharingMessage = e.getMessage();
        }
    }

    /** Filled in by the Profile tab work. */
    private int drawProfile(int left, int right, int y) {
        ui.text("Your card style", left, y + 4, Theme.ACCENT);
        return y + 16;
    }

    private void drawFooter() {
        int y = height - FOOTER + 3;
        boolean dirty = editor.isDirty();
        String hint = ui.hoverHint();
        if (hint != null) {
            ui.text(hint, MARGIN + 2, y + 5, Theme.MUTED);
        } else if (dirty) {
            ui.text("Changes staged until Apply", MARGIN + 2, y + 5, Theme.BETA);
        }

        int right = width - MARGIN;
        if (ui.button(right - 56, y, 56, Ui.CONTROL_HEIGHT, "Apply", true)) {
            apply();
        }
        if (ui.button(right - 116, y, 56, Ui.CONTROL_HEIGHT, "Discard", false)) {
            discard();
        }
        if (ui.button(right - 186, y, 66, Ui.CONTROL_HEIGHT, "Reset tab", false)) {
            resetTab();
        }
    }

    private void apply() {
        TierProfiles.publish(editor.apply());
    }

    private void discard() {
        editor.discard();
        serverBox.setValue(config().global.serverUrl);
    }

    private void resetTab() {
        Settings fresh = new Settings().sanitize();
        Settings current = staged();
        switch (tab) {
            case TAGS -> {
                current.surfaces = fresh.surfaces;
                current.filters = fresh.filters;
            }
            case COLOURS -> current.style = fresh.style;
            case RULES -> current.rules = fresh.rules;
            case MORE -> current.card = fresh.card;
            default -> {
            }
        }
    }

    private void drawConfirmClose() {
        int boxWidth = 220;
        int boxHeight = 56;
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        ui.setBlocked(false);
        ui.graphics.fill(0, 0, width, height, 0x88000000);
        ui.panel(x, y, boxWidth, boxHeight);
        ui.text("You have changes that aren't applied.", x + 8, y + 8, Theme.TEXT);
        if (ui.button(x + 8, y + 32, 64, 16, "Apply", true)) {
            apply();
            finish();
        }
        if (ui.button(x + 78, y + 32, 64, 16, "Discard", false)) {
            finish();
        }
        if (ui.button(x + 148, y + 32, 64, 16, "Keep editing", false)) {
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
        if (event.input() == InputConstants.KEY_ESCAPE && colorPicker != null) {
            colorPicker = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (colorPicker == null && !confirmingClose && super.mouseClicked(event, doubleClick)) {
            return true;
        }
        ui.onMousePressed(event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ui.onMouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int visible = height - HEADER - FOOTER;
        int max = Math.max(0, contentHeight - visible + 6);
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
