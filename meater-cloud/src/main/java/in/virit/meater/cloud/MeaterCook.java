package in.virit.meater.cloud;

public record MeaterCook(
        String id,
        String name,
        String state,
        Double targetTemperature,
        Double peakTemperature,
        Integer timeElapsed,
        Integer timeRemaining
) {}
