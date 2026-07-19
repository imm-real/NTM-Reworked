package com.hbm.ntm.ror;

import java.util.Locale;

public interface RorInteractive extends RorInfo {
    void runRorFunction(String name, String[] parameters) throws RorFunctionException;

    static String command(String message) {
        int separator = message.indexOf('!');
        return (separator < 0 ? message : message.substring(0, separator)).toLowerCase(Locale.ROOT);
    }

    static String[] parameters(String message) {
        int separator = message.indexOf('!');
        return separator < 0 || separator == message.length() - 1
                ? new String[0] : message.substring(separator + 1).split(":", -1);
    }

    static int integer(String value, int minimum, int maximum) throws RorFunctionException {
        try {
            int parsed = (int) Math.round(Double.parseDouble(value));
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new RorFunctionException("Expected " + minimum + ".." + maximum + ", got " + value);
        }
    }
}
