package in.virit;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;

@ApplicationScoped
public class SmokerHardware {

    static int FAN_GPIO = 25;

    static int HZ_50 = 50;

    private Context pi4j;

    final double dutyCycle0 = 0.5;
    final double dutyCycle180 = 2.4;
    private PwmChip pwmChip;
    private DigitalOutput fanOutput;

    @PostConstruct
    void init() {
        pi4j = Pi4J.newAutoContext();


        // configured to GPIO 12 in system
        pwmChip = new PwmChip(0, 0);

        setServoAngle(0);

        fanOutput = pi4j.digitalOutput().create(FAN_GPIO);

        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("MCP9600").bus(1).device(0x67).build();

        try (I2C mcp9600 = i2CProvider.create(i2cConfig)) {

            // TODO

            /*

            int config = tca9534Dev.readRegister(TCA9534_REG_ADDR_CFG);
            if (config < 0)
                throw new IllegalStateException(
                        "Failed to read configuration from address 0x" + String.format("%02x", TCA9534_REG_ADDR_CFG));

            byte currentState = (byte) tca9534Dev.readRegister(TCA9534_REG_ADDR_OUT_PORT);

            if (config != 0x00) {
                System.out.println("TCA9534 is not configured as OUTPUT, setting register 0x" + String
                        .format("%02x", TCA9534_REG_ADDR_CFG) + " to 0x00");
                currentState = 0x00;
                tca9534Dev.writeRegister(TCA9534_REG_ADDR_OUT_PORT, currentState);
                tca9534Dev.writeRegister(TCA9534_REG_ADDR_CFG, (byte) 0x00);
            }

            // bit 8, is pin 1 on the board itself, so set pins in reverse:
            currentState = setPin(currentState, 8, tca9534Dev, true);
            Thread.sleep(500L);
            currentState = setPin(currentState, 8, tca9534Dev, false);
            Thread.sleep(500L);

            currentState = setPin(currentState, 7, tca9534Dev, true);
            Thread.sleep(500L);
            currentState = setPin(currentState, 7, tca9534Dev, false);
            Thread.sleep(500L);

             */
        }


    }

    @PreDestroy
    void cleanup() {
        try {
            pwmChip.disable();
            pwmChip.unexport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setServoAngle(double servoAngle) {
        if(servoAngle <0 ||servoAngle > 180) {
            throw new IllegalArgumentException("0-180° only");
        }
        try {
            pwmChip.export();
            pwmChip.setPeriodMs(1000/HZ_50);
            double dutyCycleMs = dutyCycle0 + servoAngle / 180 * (dutyCycle180 - dutyCycle0);
            pwmChip.setDutyCycleMs(dutyCycleMs);
            System.out.println(dutyCycleMs + " " + pwmChip.getPeriodMs());
            pwmChip.enable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFan(boolean on) {
        if(on) {
            fanOutput.on();
        } else {
            fanOutput.off();
        }
    }



    public String boardName() {
        return pi4j.boardInfo().getBoardModel().getName();
    }






}
