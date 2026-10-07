package com.pathetictry.packhub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public class PackHubMod implements ModInitializer {
    public static final String ID = "packhub";
    private static final Logger LOG = LoggerFactory.getLogger("packhub");

    /** config/pathetictry-hub.properties -> dragonSkull=true/false (also switchable live in /packs). */
    public static volatile boolean skullSwapEnabled = true;

    /** One bundled pack as listed in packs.txt. packId is the id the pack has in the game's pack list. */
    public record BundledPack(String folder, String name, String packId, String desc) {}

    /** Filled in by registerBundledPacks(); read by the /packs screen. */
    public static final List<BundledPack> BUNDLED = new CopyOnWriteArrayList<>();

    /** True if a pack id from the game's pack list is the given bundled pack. */
    public static boolean matches(BundledPack bp, String id) {
        if (id.equals(bp.packId())) return true;
        return id.contains(ID) && (id.endsWith(":" + bp.folder()) || id.endsWith("/" + bp.folder()));
    }

    /** True if a pack id belongs to any of our bundled packs (used to hide them from the vanilla menu). */
    public static boolean isHubPackId(String id) {
        for (BundledPack bp : BUNDLED) {
            if (matches(bp, id)) return true;
        }
        return false;
    }

    @Override
    public void onInitialize() {
        loadConfig();

        // Skeleton Skull item: real Dragon Head model, epic (purple) name, "Dragon Head" label.
        // Always registered; when /packs turns the look OFF, ItemStackMixin puts the vanilla values back.
        // Always registered; when /packs turns the look OFF, ItemStackMixin puts the vanilla values back.
        DefaultItemComponentEvents.MODIFY.register(context ->
            context.modify(Items.SKELETON_SKULL, builder -> {
                builder.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("dragon_head"));
                builder.set(DataComponents.RARITY, Rarity.EPIC);
                builder.set(DataComponents.ITEM_NAME, Component.literal("Dragon Head"));
            }));

        registerBundledPacks();
    }

    /** Turns the Dragon Head skull look on/off instantly (no reload) and remembers it in the config file. */
    public static void setSkullSwap(boolean on) {
        skullSwapEnabled = on;
        Path file = FabricLoader.getInstance().getConfigDir().resolve("pathetictry-hub.properties");
        Properties p = new Properties();
        p.setProperty("dragonSkull", Boolean.toString(on));
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "PatheticTry Pack Hub - dragonSkull=true/false (toggle it with /packs)");
        } catch (IOException e) {
            LOG.warn("Could not save config", e);
        }
    }

    private static void loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("pathetictry-hub.properties");
        Properties p = new Properties();
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            } else {
                p.setProperty("dragonSkull", "true");
                try (OutputStream out = Files.newOutputStream(file)) {
                    p.store(out, "PatheticTry Pack Hub - dragonSkull=true/false (toggle it with /packs)");
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read/write config, using defaults", e);
        }
        skullSwapEnabled = Boolean.parseBoolean(p.getProperty("dragonSkull", "true").trim());
    }

    /** Reads assets/packhub/packs.txt and registers each folder in resourcepacks/ as a toggleable pack. */
    private static void registerBundledPacks() {
        Optional<ModContainer> self = FabricLoader.getInstance().getModContainer(ID);
        if (self.isEmpty()) return;
        ModContainer mod = self.get();
        Optional<Path> index = mod.findPath("assets/packhub/packs.txt");
        if (index.isEmpty()) return;
        try {
            List<String> lines = Files.readAllLines(index.get());
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length < 2) continue;
                String folder = parts[0].trim();
                String name = parts[1].trim();
                boolean on = parts.length > 2 && parts[2].trim().equalsIgnoreCase("on");
                String desc = parts.length > 3 ? parts[3].trim() : "";
                Identifier id = Identifier.fromNamespaceAndPath(ID, folder);
                ResourceLoader.registerBuiltinPack(id, mod, Component.literal(name),
                    on ? PackActivationType.DEFAULT_ENABLED : PackActivationType.NORMAL);
                BUNDLED.add(new BundledPack(folder, name, id.toString(), desc));
                LOG.info("Registered bundled pack '{}' ({})", name, folder);
            }
        } catch (Exception e) {
            LOG.warn("Could not register bundled packs", e);
        }
    }
}
