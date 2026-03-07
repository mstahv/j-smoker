# j-smoker

Quarkus + Vaadin application for automatic temperature control of a stone pit smoker.

## Running the application in dev mode

```shell script
./mvnw quarkus:dev
```

## Automatic control

### Sensors
- **iBBQ 1 (chamber)** — food chamber temperature (primary control input)
- **Meater ambient** — fallback for chamber measurement if iBBQ is unavailable
- **PROBE (MCP9600)** — fire box temperature (fast feedback, safety logic)

### Actuators
- **Throttle (servo)** — 0–100%, natural draft
- **Blower (PWM)** — 0–100%, forced air when throttle alone isn't enough

### PID control
PID output 0–200 is split between two actuators:

| PID output | Throttle  | Blower   |
|------------|-----------|----------|
| 0–100      | 0–100%    | Off      |
| 100–200    | 100%      | 0–100%   |

### State machine

```
OFF → HEATING → SMOKING ↔ FLAME_ALERT
                   ↕
               LOW_FUEL
```

| State | Description | Action |
|-------|-------------|--------|
| OFF | No control | – |
| HEATING | Initial heat-up | PID active, flames OK |
| SMOKING | Normal smoking | PID + safety logic |
| FLAME_ALERT | Flames detected | Throttle closed, blower off |
| LOW_FUEL | Fuel running low | PID keeps trying, alert to user |

### Default parameters

```
Kp = 2.0    (proportional response to error)
Ki = 0.02   (slow integration)
Kd = 0.5    (derivative-on-measurement)
```

Safety thresholds:
- Flame detection: >15°C/30s in fire box
- Wood addition: <-20°C/30s in fire box
- Low fuel: PID output >80% while fire box cooling for >2 min
- Dry water pan: PID output ~0% but chamber above setpoint for >2 min

All parameters can be adjusted live from the UI (both AutomaticView and SimulationView).

## Simulation testing (dev mode)

The SimulationView allows testing the automation without real hardware:

1. Open **SimulationView** (flask icon)
2. Click **"Simulation OFF"** to enable it (stops real sensor readings)
3. Set desired chamber and fire box temperatures using the input fields
4. Set the target temperature and click **Start**
5. Monitor PID diagnostics and state machine behavior in real time
6. Use **scenario buttons** to test safety logic:
   - **Simulate flame** — rapidly raises fire box temperature → FLAME_ALERT
   - **Simulate wood addition** — drops fire box temperature → detection notification
   - **Simulate low fuel** — cools fire box and chamber → LOW_FUEL

In simulation mode, temperatures are injected every 5s from the UI. Change field values to see how the PID responds.

## Testing on real hardware

### 1. Prerequisites

- Verify iBBQ 1 (chamber) and PROBE (fire box) show readings in ThermometersView
- Place water pan on top of the stones
- Light a small fire in the fire box, let embers form

### 2. First test run — throttle only (low target)

- Open AutomaticView, set target to **100–110°C**
- Click "Start" → state: HEATING
- Observe:
  - Throttle % increases as PID responds
  - Blower stays at 0% (PID output below 100)
  - Chamber temperature rises toward the target
- When target is reached → automatic transition to SMOKING
- Let the system stabilize for 10–15 min, watch PID diagnostics

### 3. Safety logic testing

**Flame detection:**
- Toss wood chips into the fire box so they catch fire
- Fire box rate of change exceeds 15°C/30s → FLAME_ALERT
- Throttle closes, blower stops
- When flames die down → returns to SMOKING

**Wood addition:**
- Open fire box door, add wood
- Fire box temperature drops → "Wood addition detected" notification
- PID continues normally

**Low fuel:**
- Let the fire burn out without adding wood
- PID maxes out output, fire box cools → LOW_FUEL
- "Add more wood" alert

### 4. PID tuning

Monitor logs during and after a smoking session:

```bash
grep "\[SMOKER\]" quarkus.log
```

| Symptom | Adjustment |
|---------|------------|
| Slow response | Increase Kp (2.0→5.0) |
| Overshoot, oscillation | Decrease Kp, increase Kd |
| Persistent small offset | Increase Ki (0.02→0.05) |
| Blower runs unnecessarily | Kp too high |
| Flame detection too sensitive | Increase flame threshold (15→20) |

### 5. Checklist

- [ ] iBBQ 1 and PROBE visible in ThermometersView
- [ ] Start → HEATING, throttle opens
- [ ] Target reached → automatic SMOKING transition
- [ ] Steady state: low throttle %, chamber within ±2°C of target
- [ ] Flame detection triggers and recovers
- [ ] Wood addition detected
- [ ] ActuatorsView controls disabled during automatic mode
- [ ] Stop resets throttle and blower
- [ ] Meater ambient works as fallback without iBBQ

## Logging

All automation events are logged with the `[SMOKER]` prefix:

```
[SMOKER] state=SMOKING chamber=122.3°C fire=245.1°C setpoint=120.0°C error=+2.3°C
         pid_output=45.2 P=4.6 I=38.1 D=2.5 throttle=45% blower=0%
         fire_rate=+1.2°C/30s chamber_rate=+0.3°C/30s
```

State transitions:
```
[SMOKER] STATE CHANGE: SMOKING → FLAME_ALERT (fire_rate=+18.5°C/30s, threshold=15.0)
[SMOKER] ALERT: Low fuel detected
[SMOKER] WOOD_ADDITION: fire_temp dropped 35.2°C in 30s
```

## Packaging

```shell script
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```
