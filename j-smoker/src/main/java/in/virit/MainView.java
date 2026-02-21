package in.virit;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;

@Route
public class MainView extends VerticalLayout {
    public MainView(SmokerHardware smokerHardware) {
        add("It works!?");
        add(smokerHardware.boardName());

        var value = new Span();

        IntegerField integerField = new IntegerField();
        integerField.setMin(20);
        integerField.setMax(75);
        integerField.setStep(2);
        integerField.setStepButtonsVisible(true);
        integerField.setValueChangeMode(ValueChangeMode.EAGER);
        integerField.addValueChangeListener(e -> {
            value.setText("" + e.getValue() + "°");
            smokerHardware.setServoAngle(e.getValue());
        });
        integerField.setValue(20);

        add(integerField);
        add(value);

        Checkbox fan = new Checkbox("Fan");
        fan.addValueChangeListener(event -> {
            smokerHardware.setFan(event.getValue());
        });

        add(fan);
    }
}
