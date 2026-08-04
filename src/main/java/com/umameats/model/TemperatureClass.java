package com.umameats.model;

public final class TemperatureClass {
    public static final String AMBIENT = "AMBIENT";
    public static final String CHILLED = "CHILLED";
    public static final String FROZEN = "FROZEN";

    private TemperatureClass() {}

    public static boolean requiresColdChain(String temperatureClass) {
        return CHILLED.equals(temperatureClass) || FROZEN.equals(temperatureClass);
    }
}
