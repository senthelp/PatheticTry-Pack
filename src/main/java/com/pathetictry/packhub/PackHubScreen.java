package com.pathetictry.packhub;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pathetictry.packhub.PackHubMod.BundledPack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * /packs screen. The buttons do the work; everything pretty is painted on top of them every frame:
 * animated rainbow title, active-pack meter, coloured row tints and status LEDs, hover outlines,
 * sliding light on the frame, corner brackets and floating sparkles.
 *
 * All drawing goes through two tiny helpers (rect and txt) at the bottom of the "painting" section,
 * so if a Minecraft update renames a drawing call there are only two lines to fix.
 */
public class PackHubScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("packhub");

    // layout
    private static final int W = 260;
    private static final int ROW = 22;
    private static final int GAP = 4;
    private static final int TITLE_H = 46;
    private static final int SEC_H = 12;

    // row kinds
    private static final int PACK = 0;
    private static final int SKULL = 1;
    private static final int ALL = 2;
    private static final int DONE = 3;

    /** Accent colours (RGB) cycled over the pack rows. */
    private static final int[] ACCENTS = {0xB266FF, 0x4DE1FF, 0xFFC94D, 0x5CFF8A, 0xFF6FA8};
    private static final ChatFormatting[] LABEL_COLORS = {
        ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.GREEN, ChatFormatting.RED
    };

    private record Row(int kind, int index, int x, int y, int w, int h, int accent) {}

    private final Screen parent;
    private final List<Button> packButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Button toggleAllButton;
    private Button skullButton;
    private int panelTop;
    private int panelBottom;
    private int sectionY1;
    private int sectionY2;

    public PackHubScreen(Screen parent) {
        super(Component.literal("Pack Hub"));
        this.parent = parent;
    }

    // ---- widgets -----------------------------------------------------------------------------

    @Override
    protected void init() {
        // init() runs again after every resource reload, so all state is read from the live pack list.
        packButtons.clear();
        rows.clear();
        List<BundledPack> packs = PackHubMod.BUNDLED;
        int n = packs.size();

        int total = TITLE_H + 6 + SEC_H + (n + 1) * (ROW + GAP) + 6 + SEC_H + (ROW + GAP) + 4 + ROW;
        int x = this.width / 2 - W / 2;
        int top = Math.max(12, this.height / 2 - total / 2);
        panelTop = top;
        panelBottom = top + total;

        int y = top + TITLE_H + 6;
        sectionY1 = y;
        y += SEC_H;

        for (int i = 0; i < n; i++) {
            BundledPack bp = packs.get(i);
            final int idx = i;
            Button.Builder builder = Button.builder(label(bp, LABEL_COLORS[i % LABEL_COLORS.length]), b -> toggleOne(idx))
                .bounds(x, y, W, ROW);
            if (!bp.desc().isEmpty()) {
                builder.tooltip(Tooltip.create(Component.literal(bp.desc()).withStyle(ChatFormatting.GRAY)));
            }
            Button button = builder.build();
            packButtons.add(button);
            this.addRenderableWidget(button);
            rows.add(new Row(PACK, i, x, y, W, ROW, ACCENTS[i % ACCENTS.length]));
            y += ROW + GAP;
        }

        toggleAllButton = Button.builder(toggleAllLabel(), b -> toggleAll())
            .bounds(x, y, W, ROW)
            .tooltip(Tooltip.create(Component.literal("Turns every pack on (or all off if they are all on). Reloads only once.")
                .withStyle(ChatFormatting.GRAY)))
            .build();
        this.addRenderableWidget(toggleAllButton);
        rows.add(new Row(ALL, 0, x, y, W, ROW, 0xFFD24D));
        y += ROW + GAP;

        y += 6;
        sectionY2 = y;
        y += SEC_H;

        skullButton = Button.builder(skullLabel(), b -> {
                PackHubMod.setSkullSwap(!PackHubMod.skullSwapEnabled);
                refresh();
            })
            .bounds(x, y, W, ROW)
            .tooltip(Tooltip.create(Component.literal("Skeleton Skulls look and are named Dragon Head. Instant, no reload. Only you see it.")
                .withStyle(ChatFormatting.GRAY)))
            .build();
        this.addRenderableWidget(skullButton);
        rows.add(new Row(SKULL, 0, x, y, W, ROW, 0xD07CFF));
        y += ROW + GAP;

        y += 4;
        this.addRenderableWidget(Button.builder(Component.literal("Done").withStyle(ChatFormatting.WHITE), b -> this.onClose())
            .bounds(x, y, W, ROW).build());
        rows.add(new Row(DONE, 0, x, y, W, ROW, 0xFFFFFF));
    }

    @Override
    public void onClose() {
        open(this.parent);
    }

    // ---- button text -------------------------------------------------------------------------

    private static Component label(BundledPack bp, ChatFormatting nameColor) {
        PackRepository repo = repo();
        Pack pack = find(repo, bp);
        MutableComponent out = Component.literal("");
        if (pack == null) {
            out.append(Component.literal(bp.name()).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.literal("   NOT FOUND").withStyle(ChatFormatting.RED));
        } else if (isOn(repo, pack)) {
            out.append(Component.literal(bp.name()).withStyle(nameColor, ChatFormatting.BOLD));
            out.append(Component.literal("   ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            out.append(Component.literal(bp.name()).withStyle(ChatFormatting.GRAY));
            out.append(Component.literal("   OFF").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static Component skullLabel() {
        MutableComponent out = Component.literal("");
        if (PackHubMod.skullSwapEnabled) {
            out.append(Component.literal("Skull → Dragon Head").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            out.append(Component.literal("   ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            out.append(Component.literal("Skull → Dragon Head").withStyle(ChatFormatting.GRAY));
            out.append(Component.literal("   OFF").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static Component toggleAllLabel() {
        return allOn(repo())
            ? Component.literal("Toggle All  →  turn ALL OFF").withStyle(ChatFormatting.RED)
            : Component.literal("Toggle All  →  turn ALL ON").withStyle(ChatFormatting.GREEN);
    }

    private void refresh() {
        List<BundledPack> packs = PackHubMod.BUNDLED;
        for (int i = 0; i < packButtons.size() && i < packs.size(); i++) {
            packButtons.get(i).setMessage(label(packs.get(i), LABEL_COLORS[i % LABEL_COLORS.length]));
        }
        toggleAllButton.setMessage(toggleAllLabel());
        skullButton.setMessage(skullLabel());
    }

    // ---- painting (runs every frame, after the vanilla background and the buttons) ------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        try {
            paint(graphics, mouseX, mouseY);
        } catch (RuntimeException e) {
            // Never let a cosmetic problem take the screen down.
            LOG.warn("Pack Hub effects failed", e);
        }
    }

    private void paint(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        double s = (System.currentTimeMillis() % 100_000_000L) / 1000.0;
        int cx = this.width / 2;
        int x = cx - W / 2;
        PackRepository repo = repo();
        List<BundledPack> packs = PackHubMod.BUNDLED;

        sparkles(g, s);

        // row tints, accent bars, LEDs, hover outlines
        for (Row r : rows) {
            boolean on = rowOn(r, repo, packs);
            boolean found = r.kind() != PACK || find(repo, packs.get(r.index())) != null;
            if (r.kind() != DONE) {
                int tint = !found ? 0x22000000 | 0x555555 : on ? argb(0x26, 0x20FF70) : argb(0x1C, 0xFF3355);
                if (r.kind() == ALL) tint = on ? argb(0x1C, 0xFF3355) : argb(0x22, 0x20FF70); // ALL row: colour = what a click does
                rect(g, r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, tint);

                // accent bar on the left edge
                rect(g, r.x(), r.y(), r.x() + 3, r.y() + r.h(), argb(0xFF, r.accent()));

                // status LED (pulses when on)
                int ly = r.y() + r.h() / 2;
                int lx = r.x() + 13;
                if (r.kind() != ALL) {
                    if (on) {
                        double pulse = 0.5 + 0.5 * Math.sin(s * 3.0 + r.index());
                        rect(g, lx - 5, ly - 5, lx + 5, ly + 5, argb((int) (30 + 50 * pulse), 0x30FF80));
                        rect(g, lx - 3, ly - 3, lx + 3, ly + 3, argb(0xFF, 0x4DFF9A));
                        rect(g, lx - 1, ly - 1, lx + 1, ly + 1, argb(0xFF, 0xE8FFF1));
                    } else {
                        rect(g, lx - 3, ly - 3, lx + 3, ly + 3, argb(0xFF, found ? 0x5A1F2A : 0x333333));
                    }
                }
            }
            boolean hover = mouseX >= r.x() && mouseX < r.x() + r.w() && mouseY >= r.y() && mouseY < r.y() + r.h();
            if (hover) {
                int c = argb(0xFF, r.accent());
                rect(g, r.x(), r.y(), r.x() + r.w(), r.y() + 1, c);
                rect(g, r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), c);
                rect(g, r.x(), r.y(), r.x() + 1, r.y() + r.h(), c);
                rect(g, r.x() + r.w() - 1, r.y(), r.x() + r.w(), r.y() + r.h(), c);
            }
        }

        title(g, s, cx, panelTop + 4);

        // active-pack line + segmented meter
        int active = 0;
        int totalPacks = 0;
        for (BundledPack bp : packs) {
            Pack p = find(repo, bp);
            if (p == null) continue;
            totalPacks++;
            if (isOn(repo, p)) active++;
        }
        int countColor = active == 0 ? 0xA0A0A0 : active == totalPacks ? 0x5CFF8A : 0xFFD24D;
        String sub = active + " of " + totalPacks + " packs active";
        txtCentered(g, sub, cx, panelTop + 24, argb(0xFF, countColor));

        int segGap = 3;
        int segs = Math.max(1, packs.size());
        int segW = (W - (segs - 1) * segGap) / segs;
        int segY = panelTop + 36;
        for (int i = 0; i < packs.size(); i++) {
            Pack p = find(repo, packs.get(i));
            boolean on = p != null && isOn(repo, p);
            int sx = x + i * (segW + segGap);
            if (on) {
                double pulse = 0.5 + 0.5 * Math.sin(s * 2.5 + i * 0.9);
                int a = ACCENTS[i % ACCENTS.length];
                rect(g, sx, segY, sx + segW, segY + 4, argb(0xFF, a));
                rect(g, sx, segY, sx + segW, segY + 1, argb((int) (90 + 120 * pulse), 0xFFFFFF));
            } else {
                rect(g, sx, segY, sx + segW, segY + 4, argb(0xFF, 0x2B2B36));
            }
        }

        // section captions
        sectionLabel(g, "TEXTURE PACKS  ·  reloads the game", x, sectionY1 + 2, W, s);
        sectionLabel(g, "INSTANT  ·  no reload", x, sectionY2 + 2, W, s);

        frame(g, s, x - 14, panelTop - 8, W + 28, panelBottom - panelTop + 16);

        if (panelBottom + 22 < this.height) {
            txtCentered(g, "/packs  ·  client-side  ·  only you see this", cx, panelBottom + 13, argb(0xFF, 0x707080));
        }
    }

    private void title(GuiGraphicsExtractor g, double s, int cx, int y) {
        String text = "PATHETICTRY PACK HUB";
        int totalW = 0;
        for (int i = 0; i < text.length(); i++) totalW += this.font.width(String.valueOf(text.charAt(i))) + 1;
        int px = cx - totalW / 2;
        // glow behind the title
        double glow = 0.5 + 0.5 * Math.sin(s * 1.6);
        rect(g, cx - totalW / 2 - 8, y - 3, cx + totalW / 2 + 8, y + 13, argb((int) (14 + 22 * glow), hsv(s * 0.1, 0.8, 1.0)));
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int w = this.font.width(ch);
            int bob = (int) Math.round(Math.sin(s * 3.2 + i * 0.55) * 1.6);
            int color = argb(0xFF, hsv(s * 0.12 + i * 0.045, 0.55, 1.0));
            txt(g, ch, px, y + bob, color);
            px += w + 1;
        }
    }

    private void sectionLabel(GuiGraphicsExtractor g, String text, int x, int y, int w, double s) {
        int tw = this.font.width(text);
        int cx = x + w / 2;
        int lineY = y + 4;
        int left = cx - tw / 2 - 8;
        int right = cx + tw / 2 + 8;
        int c1 = argb(0xFF, hsv(s * 0.08, 0.5, 0.85));
        fadeLine(g, x, lineY, left, c1, true);
        fadeLine(g, right, lineY, x + w, c1, false);
        txtCentered(g, text, cx, y, argb(0xFF, 0x9AA0B8));
    }

    /** 1px line that fades out towards the outer end. */
    private void fadeLine(GuiGraphicsExtractor g, int x1, int y, int x2, int rgbArgb, boolean fadeLeft) {
        int len = x2 - x1;
        if (len <= 0) return;
        int steps = Math.min(len, 12);
        int stepW = Math.max(1, len / steps);
        int rgb = rgbArgb & 0xFFFFFF;
        for (int i = 0; i < steps; i++) {
            int a = (int) (200.0 * (i + 1) / steps);
            int sx = fadeLeft ? x1 + i * stepW : x2 - (i + 1) * stepW;
            rect(g, sx, y, sx + stepW, y + 1, argb(fadeLeft ? a : 200 - a + 20, rgb));
        }
    }

    /** Corner brackets plus a bright segment that slides along the top and bottom edges. */
    private void frame(GuiGraphicsExtractor g, double s, int x, int y, int w, int h) {
        int c = argb(0xFF, hsv(s * 0.07, 0.6, 1.0));
        int len = 16;
        // top-left, top-right, bottom-left, bottom-right
        rect(g, x, y, x + len, y + 2, c);
        rect(g, x, y, x + 2, y + len, c);
        rect(g, x + w - len, y, x + w, y + 2, c);
        rect(g, x + w - 2, y, x + w, y + len, c);
        rect(g, x, y + h - 2, x + len, y + h, c);
        rect(g, x, y + h - len, x + 2, y + h, c);
        rect(g, x + w - len, y + h - 2, x + w, y + h, c);
        rect(g, x + w - 2, y + h - len, x + w, y + h, c);

        // faint full border
        int faint = argb(0x22, 0xFFFFFF);
        rect(g, x, y, x + w, y + 1, faint);
        rect(g, x, y + h - 1, x + w, y + h, faint);
        rect(g, x, y, x + 1, y + h, faint);
        rect(g, x + w - 1, y, x + w, y + h, faint);

        // sliding light
        int seg = 46;
        double t = (s * 0.35) % 1.0;
        int sx = x + (int) ((w + seg) * t) - seg;
        int a0 = Math.max(x, sx);
        int a1 = Math.min(x + w, sx + seg);
        if (a1 > a0) {
            rect(g, a0, y, a1, y + 1, argb(0xFF, 0xFFFFFF));
            rect(g, x + w - (a1 - x), y + h - 1, x + w - (a0 - x), y + h, argb(0xFF, 0xFFFFFF));
        }
    }

    /** Small twinkling dots drifting upwards, only in empty space (never on top of a button). */
    private void sparkles(GuiGraphicsExtractor g, double s) {
        int[] palette = {0xB266FF, 0x4DE1FF, 0xFFFFFF, 0xFFC94D};
        for (int i = 0; i < 32; i++) {
            double seed = i * 12.9898;
            double fx = frac(Math.sin(seed) * 43758.5453);
            double fy = frac(Math.sin(seed * 1.7 + 3.1) * 24634.6345);
            double speed = 5 + 14 * frac(Math.sin(seed * 2.3) * 9137.31);
            int px = (int) (fx * this.width);
            int py = (int) ((((fy * this.height - s * speed) % this.height) + this.height) % this.height);
            boolean covered = false;
            for (Row r : rows) {
                if (px >= r.x() - 2 && px <= r.x() + r.w() + 2 && py >= r.y() - 2 && py <= r.y() + r.h() + 2) {
                    covered = true;
                    break;
                }
            }
            if (covered) continue;
            double tw = 0.5 + 0.5 * Math.sin(s * 2.0 + i);
            int alpha = (int) (35 + 110 * tw);
            int size = (i % 4 == 0) ? 2 : 1;
            rect(g, px, py, px + size, py + size, argb(alpha, palette[i % palette.length]));
        }
    }

    // ---- the only two places that call Minecraft's drawing API --------------------------------

    private void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    private void txt(GuiGraphicsExtractor g, String text, int x, int y, int argb) {
        g.text(this.font, text, x, y, argb, true);
    }

    private void txtCentered(GuiGraphicsExtractor g, String text, int cx, int y, int argb) {
        txt(g, text, cx - this.font.width(text) / 2, y, argb);
    }

    // ---- colour / math helpers ---------------------------------------------------------------

    private static int argb(int a, int rgb) {
        return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static int hsv(double h, double sat, double val) {
        h = frac(h);
        double r;
        double g;
        double b;
        int i = (int) (h * 6);
        double f = h * 6 - i;
        double p = val * (1 - sat);
        double q = val * (1 - f * sat);
        double t = val * (1 - (1 - f) * sat);
        switch (i % 6) {
            case 0: r = val; g = t; b = p; break;
            case 1: r = q; g = val; b = p; break;
            case 2: r = p; g = val; b = t; break;
            case 3: r = p; g = q; b = val; break;
            case 4: r = t; g = p; b = val; break;
            default: r = val; g = p; b = q; break;
        }
        return (((int) (r * 255)) << 16) | (((int) (g * 255)) << 8) | ((int) (b * 255));
    }

    // ---- pack logic --------------------------------------------------------------------------

    private static PackRepository repo() {
        return Minecraft.getInstance().getResourcePackRepository();
    }

    /** Finds the game's Pack object for one of our bundled packs, or null. */
    private static Pack find(PackRepository repo, BundledPack bp) {
        for (Pack p : repo.getAvailablePacks()) {
            if (PackHubMod.matches(bp, p.getId())) return p;
        }
        return null;
    }

    private static boolean isOn(PackRepository repo, Pack pack) {
        for (Pack p : repo.getSelectedPacks()) {
            if (p.getId().equals(pack.getId())) return true;
        }
        return false;
    }

    private static boolean allOn(PackRepository repo) {
        boolean any = false;
        for (BundledPack bp : PackHubMod.BUNDLED) {
            Pack p = find(repo, bp);
            if (p == null) continue;
            any = true;
            if (!isOn(repo, p)) return false;
        }
        return any;
    }

    private static boolean rowOn(Row r, PackRepository repo, List<BundledPack> packs) {
        switch (r.kind()) {
            case PACK: {
                Pack p = find(repo, packs.get(r.index()));
                return p != null && isOn(repo, p);
            }
            case SKULL:
                return PackHubMod.skullSwapEnabled;
            case ALL:
                return allOn(repo);
            default:
                return false;
        }
    }

    private void toggleOne(int index) {
        BundledPack bp = PackHubMod.BUNDLED.get(index);
        PackRepository repo = repo();
        Pack pack = find(repo, bp);
        if (pack == null) {
            LOG.warn("Pack '{}' not found. Available ids: {}", bp.packId(),
                repo.getAvailablePacks().stream().map(Pack::getId).collect(Collectors.toList()));
            return;
        }
        List<String> ids = selectedIds(repo);
        if (!ids.remove(pack.getId())) {
            ids.add(pack.getId()); // added last = highest priority
        }
        apply(repo, ids);
        refresh();
    }

    /** One list change, one reload. */
    private void toggleAll() {
        PackRepository repo = repo();
        boolean turnOff = allOn(repo);
        List<String> ids = selectedIds(repo);
        for (BundledPack bp : PackHubMod.BUNDLED) {
            Pack p = find(repo, bp);
            if (p == null) continue;
            ids.remove(p.getId());
            if (!turnOff) ids.add(p.getId());
        }
        apply(repo, ids);
        refresh();
    }

    private static List<String> selectedIds(PackRepository repo) {
        return repo.getSelectedPacks().stream().map(Pack::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    private static void apply(PackRepository repo, List<String> ids) {
        repo.setSelected(ids);
        Minecraft.getInstance().options.updateResourcePacks(repo); // saves options.txt and reloads resources if the list changed
    }

    // ---- opening screens ---------------------------------------------------------------------

    /**
     * Opens a screen (null closes). Written with reflection because setScreen moved from Minecraft
     * to Minecraft.gui in the newest versions; this works with either layout.
     */
    static void open(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Method m = Minecraft.class.getMethod("setScreen", Screen.class);
            m.invoke(mc, screen);
            return;
        } catch (NoSuchMethodException ignored) {
            // fall through to the newer layout
        } catch (ReflectiveOperationException e) {
            LOG.warn("setScreen failed", e);
            return;
        }
        try {
            Field f;
            try {
                f = Minecraft.class.getField("gui");
            } catch (NoSuchFieldException e) {
                f = Minecraft.class.getDeclaredField("gui");
                f.setAccessible(true);
            }
            Object gui = f.get(mc);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Could not open the Pack Hub screen", e);
        }
    }
}
