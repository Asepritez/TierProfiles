package dev.tierprofiles.surface;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.hud.LogoStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A resource pack that exists only in memory. Our fonts ask it for images such as
 * tierprofiles:icon/classic/sword.png, and it finds them inside the player's own
 * game at runtime, so the icons look like real items while the mod ships none of
 * Mojang's art. Lookups try Programmer Art, then the current game, then our pixel
 * art, so an icon can't go missing.
 */
public final class IconPack implements PackResources {
    public static final IconPack INSTANCE = new IconPack();

    private static final String CLASSIC_PREFIX = "textures/icon/classic/";
    private static final String CURRENT_PREFIX = "textures/icon/current/";
    private static final String LOGO_PREFIX = "textures/logo/";
    private static final String PNG = ".png";
    private static final String PROGRAMMER_ART = "programmer_art";

    private static final int HEALING_PINK = 0xF82423;

    /** Kit id to vanilla item texture. Kits missing here have no flat vanilla image. */
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("sword", "netherite_sword"),
            Map.entry("crystal", "end_crystal"),
            Map.entry("axe", "diamond_axe"),
            Map.entry("mace", "mace"),
            Map.entry("neth_pot", "netherite_chestplate"),
            Map.entry("smp", "ender_pearl"),
            Map.entry("uhc", "golden_apple"),
            Map.entry("spear", "netherite_spear"),
            Map.entry("dia_smp", "diamond_chestplate"),
            Map.entry("dia_crystal", "end_crystal"),
            Map.entry("og_vanilla", "golden_sword"),
            Map.entry("minecart", "tnt_minecart"),
            Map.entry("trident", "trident"),
            Map.entry("speed", "sugar"),
            Map.entry("debuff", "fermented_spider_eye"),
            Map.entry("creeper", "creeper_spawn_egg"),
            Map.entry("early_game", "iron_sword"),
            Map.entry("late_game", "diamond_sword"),
            Map.entry("end_game", "end_crystal"),
            Map.entry("elytra", "elytra"),
            Map.entry("bow", "bow"));

    /** Where our own art differs from the kit id. */
    private static final Map<String, String> OWN_ART = Map.of(
            "neth_pot", "nethop",
            "dia_smp", "diamond_smp",
            "dia_crystal", "crystal",
            "og_vanilla", "vanilla",
            "minecart", "cart",
            "debuff", "pot");

    private final PackLocationInfo location =
            new PackLocationInfo("tierprofiles_icons", Component.literal("TierProfiles icons"), PackSource.BUILT_IN, Optional.empty());

    private IconPack() {
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.CLIENT_RESOURCES || !id.getNamespace().equals(TierProfiles.MOD_ID)) {
            return null;
        }
        String path = id.getPath();
        if (path.startsWith(CLASSIC_PREFIX)) {
            return icon(nameOf(path, CLASSIC_PREFIX), true);
        }
        if (path.startsWith(CURRENT_PREFIX)) {
            return icon(nameOf(path, CURRENT_PREFIX), false);
        }
        if (path.startsWith(LOGO_PREFIX)) {
            return logo(nameOf(path, LOGO_PREFIX));
        }
        return null;
    }

    private static String nameOf(String path, String prefix) {
        return path.substring(prefix.length(), path.length() - PNG.length());
    }

    private IoSupplier<InputStream> icon(String kit, boolean classic) {
        return () -> {
            List<PackResources> sources = openSources(classic);
            try {
                byte[] image = findIcon(kit, sources);
                return new ByteArrayInputStream(image != null ? image : ownArt(kit));
            } finally {
                sources.forEach(PackResources::close);
            }
        };
    }

    /** Newest pack last, so Classic falls through to the current art for newer items. */
    private static List<PackResources> openSources(boolean classic) {
        Minecraft minecraft = Minecraft.getInstance();
        List<PackResources> sources = new ArrayList<>();
        Pack programmerArt = classic ? minecraft.getResourcePackRepository().getPack(PROGRAMMER_ART) : null;
        if (programmerArt != null) {
            programmerArt.open().forEach(sources::add);
        }
        // The vanilla pack belongs to the game, so closing our list mustn't close it.
        sources.add(new KeptOpen(minecraft.getVanillaPackResources().fullResources()));
        return sources;
    }

    private static byte[] findIcon(String kit, List<PackResources> sources) throws IOException {
        if (kit.equals("pot")) {
            return potion(sources);
        }
        String item = ITEMS.get(kit);
        return item == null ? null : read(sources, item);
    }

    private static byte[] read(List<PackResources> sources, String item) throws IOException {
        Identifier id = Identifier.withDefaultNamespace("textures/item/" + item + PNG);
        for (PackResources pack : sources) {
            IoSupplier<InputStream> resource = pack.getResource(PackType.CLIENT_RESOURCES, id);
            if (resource != null) {
                try (InputStream in = resource.get()) {
                    return in.readAllBytes();
                }
            }
        }
        return null;
    }

    /** A potion's liquid is a separate tinted layer, so we paint it into the bottle. */
    private static byte[] potion(List<PackResources> sources) throws IOException {
        byte[] bottle = read(sources, "splash_potion");
        byte[] liquid = read(sources, "potion_overlay");
        if (bottle == null || liquid == null) {
            return null;
        }
        BufferedImage out = decode(bottle);
        BufferedImage overlay = decode(liquid);
        if (out.getWidth() != overlay.getWidth() || out.getHeight() != overlay.getHeight()) {
            return bottle;
        }
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int pixel = overlay.getRGB(x, y);
                if (pixel >>> 24 > 0) {
                    out.setRGB(x, y, 0xFF000000 | tint(pixel, HEALING_PINK));
                }
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(out, "png", png);
        return png.toByteArray();
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
        if (source == null) {
            throw new IOException("unreadable image");
        }
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(source, 0, 0, null);
        return copy;
    }

    private static int tint(int gray, int color) {
        int out = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (gray >> shift & 0xFF) * (color >> shift & 0xFF) / 255;
            out |= channel << shift;
        }
        return out;
    }

    private static byte[] ownArt(String kit) throws IOException {
        String file = OWN_ART.getOrDefault(kit, kit);
        String path = "/assets/tierprofiles/textures/kit/" + file + PNG;
        try (InputStream in = IconPack.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing icon " + path);
            }
            return in.readAllBytes();
        }
    }

    /** A logo downloaded earlier, or else our neutral letter mark. */
    private static IoSupplier<InputStream> logo(String list) {
        return () -> {
            Path cached = LogoStore.file(list);
            if (Files.isRegularFile(cached)) {
                return Files.newInputStream(cached);
            }
            InputStream own = IconPack.class.getResourceAsStream("/assets/tierprofiles/textures/list/" + list + PNG);
            if (own == null) {
                throw new IOException("missing logo " + list);
            }
            return own;
        };
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(TierProfiles.MOD_ID) : Set.of();
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }

    /** Forwards reads, and ignores close. */
    private record KeptOpen(PackResources pack) implements PackResources {
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return pack.getRootResource(path);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            return pack.getResource(type, id);
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            pack.listResources(type, namespace, directory, output);
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return pack.getNamespaces(type);
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
            return pack.getMetadataSection(type);
        }

        @Override
        public PackLocationInfo location() {
            return pack.location();
        }

        @Override
        public void close() {
        }
    }
}
