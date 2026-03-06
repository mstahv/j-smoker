package in.virit.slider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class NumberSliderTest {

    @Test
    void integerSliderDefaultRange() {
        var slider = new NumberSlider<>(Integer.class);
        assertEquals(Integer.valueOf(0), slider.getMin());
        assertEquals(Integer.valueOf(100), slider.getMax());
        assertEquals(Integer.valueOf(1), slider.getStep());
        // clear() sets value to min
        assertEquals(Integer.valueOf(0), slider.getValue());
    }

    @Test
    void integerSliderCustomRange() {
        var slider = new NumberSlider<>(Integer.class, 10, 50);
        assertEquals(Integer.valueOf(10), slider.getMin());
        assertEquals(Integer.valueOf(50), slider.getMax());
        assertEquals(Integer.valueOf(10), slider.getValue());
    }

    @Test
    void integerSliderWithLabel() {
        var slider = new NumberSlider<>("Throttle", Integer.class, 0, 100);
        assertEquals("Throttle", slider.getLabel());
        assertEquals(Integer.valueOf(0), slider.getMin());
        assertEquals(Integer.valueOf(100), slider.getMax());
    }

    @Test
    void integerSetAndGetValue() {
        var slider = new NumberSlider<>(Integer.class);
        slider.setValue(42);
        assertEquals(Integer.valueOf(42), slider.getValue());
    }

    @Test
    void integerSetMinMax() {
        var slider = new NumberSlider<>(Integer.class);
        slider.setMin(5);
        slider.setMax(200);
        assertEquals(Integer.valueOf(5), slider.getMin());
        assertEquals(Integer.valueOf(200), slider.getMax());
    }

    @Test
    void integerSetStep() {
        var slider = new NumberSlider<>(Integer.class);
        slider.setStep(5);
        assertEquals(Integer.valueOf(5), slider.getStep());
    }

    @Test
    void longSlider() {
        var slider = new NumberSlider<>(Long.class, 0, 1000);
        assertEquals(Long.valueOf(0), slider.getValue());
        slider.setValue(500L);
        assertEquals(Long.valueOf(500), slider.getValue());
        assertEquals(Long.valueOf(0), slider.getMin());
        assertEquals(Long.valueOf(1000), slider.getMax());
    }

    @Test
    void floatSlider() {
        var slider = new NumberSlider<>(Float.class);
        slider.setValue(3.14f);
        assertEquals(3.14f, slider.getValue(), 0.001f);
    }

    @Test
    void doubleSlider() {
        var slider = new NumberSlider<>(Double.class, 0, 1);
        slider.setValue(0.5);
        assertEquals(0.5, slider.getValue());
    }

    @Test
    void shortSlider() {
        var slider = new NumberSlider<>(Short.class, 0, 50);
        slider.setValue((short) 25);
        assertEquals(Short.valueOf((short) 25), slider.getValue());
    }

    @Test
    void byteSlider() {
        var slider = new NumberSlider<>(Byte.class, 0, 127);
        slider.setValue((byte) 64);
        assertEquals(Byte.valueOf((byte) 64), slider.getValue());
    }

    @Test
    void valueChangeListenerReceivesTypedValue() {
        var slider = new NumberSlider<>(Integer.class);
        AtomicReference<Integer> received = new AtomicReference<>();
        slider.addValueChangeListener(e -> received.set(e.getValue()));
        slider.setValue(77);
        assertEquals(Integer.valueOf(77), received.get());
    }

    @Test
    void clearResetsToMin() {
        var slider = new NumberSlider<>(Integer.class, 10, 90);
        slider.setValue(50);
        slider.clear();
        assertEquals(Integer.valueOf(10), slider.getValue());
    }

    @Test
    void unsupportedTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new NumberSlider<>(java.math.BigDecimal.class));
    }

    @Test
    void minMaxVisible() {
        var slider = new NumberSlider<>(Integer.class);
        assertFalse(slider.isMinMaxVisible());
        slider.setMinMaxVisible(true);
        assertTrue(slider.isMinMaxVisible());
    }

    @Test
    void valueAlwaysVisible() {
        var slider = new NumberSlider<>(Integer.class);
        assertFalse(slider.isValueAlwaysVisible());
        slider.setValueAlwaysVisible(true);
        assertTrue(slider.isValueAlwaysVisible());
    }

    @Test
    void ariaLabel() {
        var slider = new NumberSlider<>("Test", Integer.class, 0, 100);
        slider.setAriaLabel("Volume control");
        assertEquals("Volume control", slider.getAriaLabel().orElse(null));
    }

    @Test
    void customConverterConstructor() {
        var slider = new NumberSlider<>(0, 100,
                d -> (int) Math.round(d),
                Integer::doubleValue);
        slider.setValue(42);
        assertEquals(42, slider.getValue());
    }
}
