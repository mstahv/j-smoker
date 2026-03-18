package in.virit;

import in.virit.color.HexColor;
import in.virit.color.HslColor;
import in.virit.color.NamedColor;
import org.vaadin.firitin.components.VSvg;
import org.vaadin.firitin.element.svg.AnimateMotionElement;
import org.vaadin.firitin.element.svg.AnimateTransformElement;
import org.vaadin.firitin.element.svg.CircleElement;
import org.vaadin.firitin.element.svg.ClipPathElement;
import org.vaadin.firitin.element.svg.DefsElement;
import org.vaadin.firitin.element.svg.GElement;
import org.vaadin.firitin.element.svg.PathElement;
import org.vaadin.firitin.element.svg.RectElement;
import org.vaadin.firitin.element.svg.TextElement;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * SVG diagram visualizing the smoker airflow system:
 * Blower (fan) → Throttle (flap valve) → Hose → Fire chamber.
 * <p>
 * The fan spins when the blower is active and the throttle flap
 * tilts from closed (vertical, 0 %) to open (nearly horizontal, 100 %).
 */
class AirflowDiagram extends VSvg {

    // --- layout constants (viewBox units) ---
    private static final int FAN_CX = 50, FAN_CY = 60, FAN_R = 28;
    private static final int DUCT_Y = 48, DUCT_H = 24;
    private static final int THROTTLE_X = 108, THROTTLE_W = 24;
    private static final int FLAP_CX = THROTTLE_X + THROTTLE_W / 2;
    private static final int FLAP_CY = DUCT_Y + DUCT_H / 2;
    private static final int HOSE_X1 = THROTTLE_X + THROTTLE_W;
    private static final int HOSE_X2 = 220;
    private static final int FIRE_X = 220, FIRE_Y = 22, FIRE_W = 90, FIRE_H = 76;

    // Food chamber + stones above fire chamber
    private static final int STONE_H = 10;
    private static final int STONE_Y = FIRE_Y - STONE_H;
    private static final int FOOD_H = 60;
    private static final int FOOD_Y = STONE_Y - FOOD_H;
    private static final int ROOF_H = 20;

    private static final int ARROW_SPACING = 35;
    private static final int FLAME_BASE_Y = FIRE_Y + FIRE_H - 10;
    private static final HexColor DUCT_FILL = HexColor.of("#d5d5d5");
    private static final HexColor DUCT_STROKE = HexColor.of("#999999");

    // Flame base HSL colors (hue, saturation, lightness at full glow)
    private static final HslColor[] FLAME_COLORS = {
            new HslColor(16, 100, 50, 1),   // red-orange
            new HslColor(18, 100, 60, 1),   // orange
            new HslColor(39, 100, 50, 1),   // amber
            new HslColor(16, 100, 50, 1),   // red-orange
            new HslColor(28, 100, 50, 1),   // dark orange
    };

    private final GElement bladeGroup;
    private final GElement flapGroup;
    private final GElement arrowGroup;
    private final List<GElement> flameWrappers;
    private final List<PathElement> flameElements;
    private final List<PathElement> flameCoreElements;
    private final TextElement throttleText;
    private final TextElement blowerText;
    private final TextElement fireTempText;
    private final TextElement foodChamberTempText;
    private final TextElement foodProbeTempText;
    enum BlowerMode { OFF, PWM_50, FULL }

    private AnimateTransformElement fanAnimation;
    private AnimateMotionElement arrowAnimation;
    private int currentThrottle = -1;
    private int currentBlowerSpeed = -1;
    private Consumer<BlowerMode> blowerClickListener;
    private Consumer<Integer> throttleDragListener;

    AirflowDiagram() {
        super(0, FOOD_Y - ROOF_H - 5, 320, FIRE_Y + FIRE_H + 20 - (FOOD_Y - ROOF_H - 5));
        setWidthFull();
        getStyle().set("max-width", "450px");

        // ── fire chamber ──
        var fireOuter = rect(FIRE_X, FIRE_Y, FIRE_W, FIRE_H, 4)
                .fill(HexColor.of("#8b6914")).stroke(HexColor.of("#5a4510")).strokeWidth(2);
        var fireInner = rect(FIRE_X + 4, FIRE_Y + 4, FIRE_W - 8, FIRE_H - 8, 2)
                .fill(HexColor.of("#1a1a1a"));
        // ── wood logs at base of fire ──
        int logY = FLAME_BASE_Y + 2;
        int logCx = FIRE_X + FIRE_W / 2;
        var woodGroup = new GElement();
        // Two crossed logs
        var log1 = rect(logCx - 30, logY, 60, 8, 3)
                .fill(HexColor.of("#6b3a1f")).stroke(HexColor.of("#4a2810")).strokeWidth(0.8);
        var log2 = rect(logCx - 28, logY - 5, 56, 7, 3)
                .fill(HexColor.of("#7a4528")).stroke(HexColor.of("#4a2810")).strokeWidth(0.8);
        // Angled log crossing
        var log3 = rect(logCx - 22, logY - 9, 44, 6, 2)
                .fill(HexColor.of("#5c3018")).stroke(HexColor.of("#3a1a08")).strokeWidth(0.8);
        log1.rotate(-8, logCx, logY + 4);
        log2.rotate(10, logCx, logY - 1);
        log3.rotate(-3, logCx, logY - 6);
        // Wood grain details (small lines)
        for (int gx = -18; gx <= 18; gx += 12) {
            woodGroup.add(new PathElement()
                    .moveTo(logCx + gx, logY + 1)
                    .lineTo(logCx + gx + 4, logY + 5)
                    .stroke(HexColor.of("#4a2510")).strokeWidth(0.5).fillOpacity(0));
        }
        woodGroup.add(log1);
        woodGroup.add(log2);
        woodGroup.add(log3);
        // Small ember dots around the wood
        var embers = new GElement();
        int[] emberX = {-20, -8, 5, 15, 22, -14, 10};
        int[] emberY = {-2, 3, -4, 1, -1, -6, -7};
        for (int i = 0; i < emberX.length; i++) {
            embers.add(new CircleElement()
                    .center(logCx + emberX[i], logY + emberY[i]).r(1)
                    .fill(HexColor.of("#ff6600")).fillOpacity(0.6));
        }
        woodGroup.add(embers);

        int[] flameXOffsets = {18, 34, 50, 64, 76};
        int[] flameHeights = {40, 52, 36, 48, 32};
        // Flicker: different wobble paths and durations per flame for natural look
        String[] flickerPaths = {
                "M 0 0 Q 0.6 -1 0 -1.8 Q -0.5 -0.8 0 0",
                "M 0 0 Q -0.7 -1.5 0 -2.2 Q 0.5 -1 0 0",
                "M 0 0 Q 0.4 -0.8 0 -1.5 Q -0.4 -0.7 0 0",
                "M 0 0 Q -0.5 -1.2 0.3 -1.8 Q 0 -0.7 0 0",
                "M 0 0 Q 0.5 -1 -0.3 -1.3 Q 0 -0.5 0 0",
        };
        int[] flickerDurations = {600, 780, 550, 700, 650};
        var flames = new GElement();
        var flameList = new java.util.ArrayList<PathElement>();
        var coreList = new java.util.ArrayList<PathElement>();
        var wrapperList = new java.util.ArrayList<GElement>();
        for (int i = 0; i < flameXOffsets.length; i++) {
            int fx = FIRE_X + flameXOffsets[i];
            var f = flame(fx, FLAME_BASE_Y, flameHeights[i],
                    FLAME_COLORS[i].withLuminance(35).withAlpha(0.50));
            var core = flameCore(fx, FLAME_BASE_Y, flameHeights[i],
                    FLAME_COLORS[i]);
            var flameGroup = new GElement();
            flameGroup.add(f);
            flameGroup.add(core);
            flameGroup.appendChild(new AnimateMotionElement()
                    .path(flickerPaths[i])
                    .dur(Duration.ofMillis(flickerDurations[i]))
                    .repeatIndefinitely());
            var wrapper = new GElement();
            wrapper.add(flameGroup);
            flameList.add(f);
            coreList.add(core);
            wrapperList.add(wrapper);
            flames.add(wrapper);
        }
        flameElements = List.copyOf(flameList);
        flameCoreElements = List.copyOf(coreList);
        flameWrappers = List.copyOf(wrapperList);
        var fireInlet = rect(FIRE_X - 1, DUCT_Y, 5, DUCT_H, 0)
                .fill(HexColor.of("#333333"));
        fireTempText = label(FIRE_X + FIRE_W / 2, FIRE_Y + 16, "");
        fireTempText.fontSize("11px");
        fireTempText.fill(HexColor.of("#cccccc"));
        fireTempText.setVisible(false);

        // ── stone layer between chambers ──
        var stones = rect(FIRE_X, STONE_Y, FIRE_W, STONE_H, 0)
                .fill(HexColor.of("#888888")).stroke(HexColor.of("#666666")).strokeWidth(1);
        // Small circles to suggest stones
        var stoneDetails = new GElement();
        int[] stoneXOffsets = {10, 25, 40, 55, 68, 80};
        for (int sx : stoneXOffsets) {
            stoneDetails.add(new CircleElement()
                    .center(FIRE_X + sx, STONE_Y + STONE_H / 2).r(3)
                    .fill(HexColor.of("#999999")).stroke(HexColor.of("#777777")).strokeWidth(0.5));
        }

        // ── food chamber ──
        var foodOuter = rect(FIRE_X, FOOD_Y, FIRE_W, FOOD_H, 4)
                .fill(HexColor.of("#8b6914")).stroke(HexColor.of("#5a4510")).strokeWidth(2);
        var foodInner = rect(FIRE_X + 4, FOOD_Y + 4, FIRE_W - 8, FOOD_H - 8, 2)
                .fill(HexColor.of("#2a2a2a"));
        foodChamberTempText = label(FIRE_X + FIRE_W / 2, FOOD_Y + 18, "");
        foodChamberTempText.fontSize("11px");
        foodChamberTempText.fill(HexColor.of("#cccccc"));
        foodChamberTempText.setVisible(false);

        // ── meat ──
        int meatCx = FIRE_X + FIRE_W / 2;
        int meatCy = FOOD_Y + FOOD_H / 2 + 10;
        int meatHalfW = 30;
        int meatTopH = 20;
        int meatBottomH = 15;
        int grillLineHalfSpan = 16;
        int grillLineSpacing = 8;
        int grillLineTop = 8;
        int grillLineBottom = 6;
        HslColor stroke = NamedColor.SADDLEBROWN.toRgbColor().toHslColor().darken(10);
        var meat = new PathElement()
                .moveTo(meatCx - meatHalfW, meatCy)
                .cubicBezierTo(meatCx - meatHalfW, meatCy - meatTopH, meatCx + meatHalfW, meatCy - meatTopH, meatCx + meatHalfW, meatCy)
                .cubicBezierTo(meatCx + meatHalfW, meatCy + meatBottomH, meatCx - meatHalfW, meatCy + meatBottomH, meatCx - meatHalfW, meatCy)
                .closePath()
                .fill(NamedColor.SADDLEBROWN).stroke(stroke).strokeWidth(2);
        // Grill lines on meat
        var grillLines = new GElement();
        for (int gx = -grillLineHalfSpan; gx <= grillLineHalfSpan; gx += grillLineSpacing) {
            grillLines.add(new PathElement()
                    .moveTo(meatCx + gx, meatCy - grillLineTop)
                    .lineTo(meatCx + gx, meatCy + grillLineBottom)
                    .closePath()
                    .stroke(stroke).strokeWidth(1));
        }
        foodProbeTempText = label(meatCx, meatCy, "");
        foodProbeTempText.dominantBaseline(TextElement.DominantBaseline.MIDDLE);
        foodProbeTempText.fontSize("10px");
        foodProbeTempText.fill(HexColor.of("#ffddaa"));
        foodProbeTempText.setVisible(false);

        // ── gable roofs ──
        int roofOverhang = 6;
        // Food chamber roof (top)
        var foodRoof = new PathElement()
                .moveTo(FIRE_X - roofOverhang, FOOD_Y)
                .lineTo(FIRE_X + FIRE_W / 2, FOOD_Y - ROOF_H)
                .lineTo(FIRE_X + FIRE_W + roofOverhang, FOOD_Y)
                .closePath()
                .fill(HexColor.of("#6b4c1e")).stroke(HexColor.of("#4a3510")).strokeWidth(2);
        // Fire chamber roof (between food chamber bottom and fire chamber top - visible on sides)
        // Not needed since they share a wall via stones

        // ── single hose from blower to fire chamber ──
        int hoseStart = FAN_CX + FAN_R + 3;
        var hose = rect(hoseStart, DUCT_Y, HOSE_X2 - hoseStart, DUCT_H, 0)
                .fill(DUCT_FILL).stroke(DUCT_STROKE).strokeWidth(1);

        // Animated arrows clipped to full hose
        var hoseClip = new ClipPathElement("hoseClip")
                .add(rect(hoseStart, DUCT_Y, HOSE_X2 - hoseStart, DUCT_H, 0));
        var defs = new DefsElement(hoseClip);

        int hoseLen = HOSE_X2 - hoseStart;
        int arrowCount = hoseLen / ARROW_SPACING + 1;
        int arrowY = DUCT_Y + DUCT_H / 2;
        arrowGroup = new GElement();
        for (int i = -1; i < arrowCount; i++) {
            arrowGroup.add(arrow(hoseStart + 10 + i * ARROW_SPACING, arrowY));
        }
        arrowGroup.clipPath(hoseClip);

        // ── throttle valve ──
        var throttleHousing = rect(THROTTLE_X, DUCT_Y - 2, THROTTLE_W, DUCT_H + 4, 0)
                .rx(2)
                .ry(2)
                .fill(HexColor.of("#a0a0a0")).fillOpacity(0.4).stroke(HexColor.of("#666666")).strokeWidth(2);
        flapGroup = new GElement();
        flapGroup.add(rect(FLAP_CX - 2, DUCT_Y + 1, 4, DUCT_H - 2, 1)
                .fill(HexColor.of("#e74c3c")).stroke(HexColor.of("#c0392b")).strokeWidth(0.5));
        var flapPivot = new CircleElement()
                .center(FLAP_CX, FLAP_CY).r(3)
                .fill(HexColor.of("#333333"));
        throttleText = label(FLAP_CX, DUCT_Y + DUCT_H + 20, "Throttle 0 %");

        record Detail(int percent){}

        // Draggable hit area over the throttle
        var throttleHitArea = rect(THROTTLE_X - 8, DUCT_Y - 10, THROTTLE_W + 16, DUCT_H + 20, 0)
                .fill(HexColor.of("#000000")).fillOpacity(0).strokeWidth(0);
        throttleHitArea.getStyle().setCursor("ew-resize");
        throttleHitArea.getStyle().set("touch-action", "none"); // TODO improve Style
        throttleHitArea.setAttribute("data-role", "throttle");
        throttleHitArea.addEventListener("throttle-drag", e -> {
            int percent = e.getEventDetail(Detail.class).percent();
            if (throttleDragListener != null) {
                throttleDragListener.accept(percent);
            }
        }).addEventDetail(Detail.class).debounce(10);

        // ── blower fan (clickable) ──
        var blowerHousing = new CircleElement()
                .center(FAN_CX, FAN_CY).r(FAN_R + 3)
                .fill(HexColor.of("#e0e0e0")).stroke(HexColor.of("#888888")).strokeWidth(2);
        bladeGroup = new GElement();
        for (int i = 0; i < 6; i++) {
            bladeGroup.add(blade(FAN_CX, FAN_CY, FAN_R - 5, i * 60));
        }
        var blowerHub = new CircleElement()
                .center(FAN_CX, FAN_CY).r(6)
                .fill(HexColor.of("#555555")).stroke(HexColor.of("#333333")).strokeWidth(1);
        blowerText = label(FAN_CX, FAN_CY + FAN_R + 18, "Blower OFF");

        // Clickable hit area over the blower
        var blowerHitArea = new CircleElement()
                .center(FAN_CX, FAN_CY).r(FAN_R + 5)
                .fill(HexColor.of("#000000")).fillOpacity(0)
                .strokeWidth(0);
        blowerHitArea.getStyle().setCursor("pointer");
        blowerHitArea.addEventListener("click", e -> {
            if (blowerClickListener != null) {
                BlowerMode next = switch (currentBlowerSpeed) {
                    case 0 -> BlowerMode.PWM_50;
                    case 100 -> BlowerMode.OFF;
                    default -> BlowerMode.FULL;
                };
                blowerClickListener.accept(next);
            }
        });

        // ── assemble (painter's order) ──
        getElement().appendChild(
                defs, hose, arrowGroup,
                fireOuter, fireInner, woodGroup, flames, fireInlet, fireTempText,
                stones, stoneDetails,
                foodOuter, foodInner, meat, grillLines,
                foodChamberTempText, foodProbeTempText,
                foodRoof,
                throttleHousing, flapGroup, flapPivot, throttleText, throttleHitArea,
                blowerHousing, bladeGroup, blowerHub, blowerText, blowerHitArea
        );

        // Set initial flame state (tiny/dim for zero airflow)
        currentThrottle = 0;
        currentBlowerSpeed = 0;
        updateFlames();

        // Register client-side drag handler for throttle
        int hitLeft = THROTTLE_X - 8;
        int hitWidth = THROTTLE_W + 16;
        getElement().executeJs("""
                const svg = this;
                const hit = svg.querySelector('[data-role=throttle]');
                if (!hit) return;
                let dragging = false;
                function toPct(e) {
                    const pt = svg.createSVGPoint();
                    pt.x = e.clientX; pt.y = e.clientY;
                    const x = pt.matrixTransform(svg.getScreenCTM().inverse()).x;
                    return Math.round(Math.max(0, Math.min(100,
                        (x - $0) / $1 * 100)));
                }
                function send(e) {
                    hit.dispatchEvent(new CustomEvent('throttle-drag',
                        {detail: {percent: toPct(e)}}));
                }
                hit.addEventListener('pointerdown', e => {
                    dragging = true;
                    hit.setPointerCapture(e.pointerId);
                    send(e);
                    e.preventDefault();
                });
                hit.addEventListener('pointermove', e => { if (dragging) send(e); });
                hit.addEventListener('pointermove', e => { if (dragging) e.preventDefault(); }, {passive: false});
                hit.addEventListener('pointerup', () => { dragging = false; });
                hit.addEventListener('pointercancel', () => { dragging = false; });
                hit.addEventListener('touchmove', e => { if (dragging) e.preventDefault(); }, {passive: false});
                """, hitLeft, hitWidth);
    }

    // ── public API ────────────────────────────────────────

    void setThrottlePercent(int percent) {
        if (percent == currentThrottle) return;
        int angle = percent * 90 / 100;
        flapGroup.transform("rotate(%d %d %d)".formatted(angle, FLAP_CX, FLAP_CY));
        throttleText.setText("Throttle %d %%".formatted(percent));
        currentThrottle = percent;
        updateArrowAnimation();
        updateFlames();
    }

    /**
     * @param speedPercent 0 = stop, 1-100 = spin speed (100 = full, 800ms/rev)
     */
    void setBlowerSpeed(int speedPercent) {
        if (speedPercent == currentBlowerSpeed) return;
        // Remove previous animation
        if (fanAnimation != null && fanAnimation.getParent() != null) {
            fanAnimation.getParent().removeChild(fanAnimation);
            fanAnimation = null;
        }
        if (speedPercent <= 0) {
            currentBlowerSpeed = 0;
            updateArrowAnimation();
            updateFlames();
            return;
        }

        // 100% → 800ms, 50% → 1600ms, 10% → 8000ms
        long durationMs = 800 * 100 / speedPercent;
        fanAnimation = new AnimateTransformElement()
                .rotateFromTo(0, 360, FAN_CX, FAN_CY)
                .dur(Duration.ofMillis(durationMs))
                .repeatIndefinitely();
        bladeGroup.appendChild(fanAnimation);
        fanAnimation.beginElement();
        currentBlowerSpeed = speedPercent;
        updateArrowAnimation();
        updateFlames();
    }

    void setBlowerLabel(String text) {
        blowerText.setText(text);
    }

    void onBlowerClick(Consumer<BlowerMode> listener) {
        this.blowerClickListener = listener;
    }

    void onThrottleDrag(Consumer<Integer> listener) {
        this.throttleDragListener = listener;
    }

    void setFireTemp(String text) {
        fireTempText.setText(text);
        fireTempText.setVisible(true);
    }

    void setFoodChamberTemp(String text) {
        foodChamberTempText.setText(text);
        foodChamberTempText.setVisible(true);
    }

    void setFoodProbeTemp(String text) {
        foodProbeTempText.setText(text);
        foodProbeTempText.setVisible(true);
    }

    private void updateFlames() {
        // Effective airflow: 0 (no air) to 500 (full throttle + full blower)
        int blowerMultiplier = 100 + currentBlowerSpeed * 4;
        int airflow = currentThrottle * blowerMultiplier / 100;

        // Color: luminance 35..55, alpha 0.50..0.90
        int luminance = 35 + airflow * 20 / 500;
        double alpha = 0.50 + airflow * 0.40 / 500;

        // Height: scale 0.3 (embers) to 1.0 (full flames), anchored at flame base
        int scalePercent = 30 + airflow * 70 / 500;

        // Locale-safe scale value (avoid decimal comma from Finnish locale)
        int whole = scalePercent / 100;
        int frac = scalePercent % 100;
        String scaleY = whole + "." + (frac < 10 ? "0" : "") + frac;
        String transform = "translate(0 " + FLAME_BASE_Y + ") scale(1 " + scaleY + ") translate(0 " + (-FLAME_BASE_Y) + ")";

        // Core: brighter yellow-white, slightly more transparent at low airflow
        int coreLuminance = 60 + airflow * 15 / 500;
        double coreAlpha = 0.40 + airflow * 0.50 / 500;

        for (int i = 0; i < flameElements.size(); i++) {
            flameElements.get(i).fill(FLAME_COLORS[i].withLuminance(luminance).withAlpha(alpha));
            flameCoreElements.get(i).fill(
                    FLAME_COLORS[i].withHue(45).withSaturation(100)
                            .withLuminance(coreLuminance).withAlpha(coreAlpha));
            flameWrappers.get(i).transform(transform);
        }
    }

    private void updateArrowAnimation() {
        // Remove previous animation
        if (arrowAnimation != null && arrowAnimation.getParent() != null) {
            arrowAnimation.getParent().removeChild(arrowAnimation);
            arrowAnimation = null;
        }
        if (currentThrottle <= 0) return;

        // Throttle opens airflow path, blower multiplies speed
        // Base: throttle alone at 100% → 2700ms per cycle
        // Blower at 100% multiplies speed by 5x → ~530ms per cycle
        int blowerMultiplier = 100 + currentBlowerSpeed * 4; // 100..500
        int effectiveSpeed = currentThrottle * blowerMultiplier / 100; // 1..500
        long durationMs = 2700 * 100 / effectiveSpeed;
        durationMs = Math.max(200, Math.min(durationMs, 8000));

        arrowAnimation = new AnimateMotionElement()
                .path("M 0 0 H %d".formatted(ARROW_SPACING))
                .dur(Duration.ofMillis(durationMs))
                .repeatIndefinitely();
        arrowGroup.appendChild(arrowAnimation);
        arrowAnimation.beginElement();
    }

    // ── helpers ───────────────────────────────────────────

    private static RectElement rect(int x, int y, int w, int h, int r) {
        var re = new RectElement().bounds(x, y, w, h);
        if (r > 0) re.cornerRadius(r);
        return re;
    }

    private static TextElement label(int x, int y, String text) {
        return new TextElement(x, y, text)
                .textAnchor(TextElement.TextAnchor.MIDDLE)
                .fontSize("9px")
                .fill(HexColor.of("#666666"));
    }

    private static PathElement blade(int cx, int cy, int r, int rotation) {
        return new PathElement()
                .moveTo(cx - 2, cy)
                .lineTo(cx + r * 0.3, cy - 6)
                .lineTo(cx + r, cy - 4)
                .lineTo(cx + r, cy + 4)
                .lineTo(cx + r * 0.3, cy + 6)
                .lineTo(cx - 2, cy)
                .closePath()
                .fill(HexColor.of("#5b9bd5"))
                .stroke(HexColor.of("#3a7cc0"))
                .strokeWidth(0.5)
                .rotate(rotation, cx, cy);
    }

    /**
     * Outer flame shape inspired by bonfire SVG: wide base, wavy sides with
     * indentations that create sub-tongue shapes, tapered forked tip.
     */
    private static PathElement flame(int x, int y, int h, HslColor color) {
        double w = h * 0.20;
        return new PathElement()
                // wide base
                .moveTo(x - w * 0.7, y)
                // left side: bulge out low
                .cubicBezierTo(x - w * 1.2, y - h * 0.15, x - w * 1.1, y - h * 0.30, x - w * 0.7, y - h * 0.35)
                // left indentation (sub-tongue effect)
                .cubicBezierTo(x - w * 0.4, y - h * 0.38, x - w * 0.5, y - h * 0.42, x - w * 0.6, y - h * 0.50)
                // left side narrows up
                .cubicBezierTo(x - w * 0.7, y - h * 0.60, x - w * 0.4, y - h * 0.72, x - w * 0.2, y - h * 0.82)
                // tip: lean slightly left, taper to point
                .cubicBezierTo(x - w * 0.08, y - h * 0.92, x - w * 0.02, y - h * 0.98, x, y - h)
                // right side of tip
                .cubicBezierTo(x + w * 0.03, y - h * 0.97, x + w * 0.12, y - h * 0.90, x + w * 0.25, y - h * 0.80)
                // right indentation
                .cubicBezierTo(x + w * 0.45, y - h * 0.68, x + w * 0.7, y - h * 0.58, x + w * 0.55, y - h * 0.45)
                // right sub-tongue
                .cubicBezierTo(x + w * 0.4, y - h * 0.38, x + w * 0.5, y - h * 0.33, x + w * 0.75, y - h * 0.28)
                // right side base bulge
                .cubicBezierTo(x + w * 1.1, y - h * 0.18, x + w * 1.15, y - h * 0.08, x + w * 0.7, y)
                .closePath()
                .fill(color);
    }

    /**
     * Inner core flame: narrower teardrop, brighter yellow-white.
     */
    private static PathElement flameCore(int x, int y, int h, HslColor baseColor) {
        double coreH = h * 0.58;
        double cw = coreH * 0.20;
        var coreColor = baseColor.withHue(45).withSaturation(100).withLuminance(70).withAlpha(0.8);
        return new PathElement()
                .moveTo(x - cw * 0.4, y)
                // left side with slight bulge
                .cubicBezierTo(x - cw * 1.0, y - coreH * 0.25, x - cw * 0.8, y - coreH * 0.55, x - cw * 0.3, y - coreH * 0.78)
                // tip
                .cubicBezierTo(x - cw * 0.1, y - coreH * 0.92, x, y - coreH, x + cw * 0.05, y - coreH * 0.95)
                // right side
                .cubicBezierTo(x + cw * 0.6, y - coreH * 0.70, x + cw * 0.9, y - coreH * 0.40, x + cw * 0.4, y)
                .closePath()
                .fill(coreColor);
    }

    private static PathElement arrow(int x, int y) {
        return new PathElement()
                .moveTo(x - 6, y - 3)
                .lineTo(x + 2, y - 3)
                .lineTo(x + 2, y - 5)
                .lineTo(x + 8, y)
                .lineTo(x + 2, y + 5)
                .lineTo(x + 2, y + 3)
                .lineTo(x - 6, y + 3)
                .closePath()
                .fill(HexColor.of("#5b9bd5"))
                .fillOpacity(0.5);
    }
}
