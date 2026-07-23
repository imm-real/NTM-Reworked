package com.hbm.ntm.autocal;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.ror.RttySystem;

import java.util.Locale;

public class ParseMSES1 implements IParse {
    @Override
    public StatementResult eval(ParseContext context, String line) {
        String lower = line.toLowerCase(Locale.US);

        if (line.isEmpty() || lower.startsWith("dest ") || lower.startsWith("# ")) {
            return StatementResult.SKIP;
        }
        if (lower.equals("nop")) return StatementResult.OK;

        if (lower.startsWith("clockspeed ")) {
            if (line.length() <= 11) return StatementResult.PARAMETER_ERROR;
            try {
                int speed = Integer.parseInt(line.substring(11));
                if (speed < 1 || speed > HbmConfig.AUTOCAL_MAX_CLOCK.get()) {
                    return StatementResult.PARAMETER_ERROR;
                }
                context.setClockSpeed(speed);
            } catch (Throwable ignored) {
                return StatementResult.PARAMETER_ERROR;
            }
            return StatementResult.SKIP;
        }

        if (lower.startsWith("jmp ")) {
            return jump(context, substitute(context, line.substring(4), false), true);
        }
        if (lower.startsWith("jmpif ")) {
            if (line.length() <= 6) return StatementResult.PARAMETER_ERROR;
            if (!context.readBuffer().equals("true")) return StatementResult.OK;
            return jump(context, substitute(context, line.substring(6), false), false);
        }
        if (lower.startsWith("jmpnot ")) {
            if (line.length() <= 7) return StatementResult.PARAMETER_ERROR;
            if (context.readBuffer().equals("true")) return StatementResult.OK;
            return jump(context, substitute(context, line.substring(7), false), false);
        }
        if (lower.equals("endtick")) return StatementResult.END_TICK;
        if (lower.equals("shutdown")) return StatementResult.SHUTDOWN;

        if (lower.startsWith("load ")) {
            if (line.length() <= 5) return StatementResult.PARAMETER_ERROR;
            context.writeBuffer(context.variables().getString(line.substring(5)));
            return StatementResult.OK;
        }
        if (lower.startsWith("save ")) {
            if (line.length() <= 5 || context.readBuffer().isEmpty()) {
                return StatementResult.PARAMETER_ERROR;
            }
            context.variables().putString(line.substring(5), context.readBuffer());
            return StatementResult.OK;
        }
        if (lower.startsWith("buffer ")) {
            if (line.length() <= 7) return StatementResult.PARAMETER_ERROR;
            context.writeBuffer(line.substring(7));
            return StatementResult.OK;
        }
        if (lower.startsWith("eval ")) {
            if (line.length() <= 5) return StatementResult.PARAMETER_ERROR;
            return evaluate(context, substitute(context, line.substring(5), true), false);
        }
        if (lower.equals("eval")) {
            if (context.readBuffer().isEmpty()) return StatementResult.PARAMETER_ERROR;
            return evaluate(context, substitute(context, context.readBuffer(), true), false);
        }
        if (lower.startsWith("evalr ")) {
            if (line.length() <= 6) return StatementResult.PARAMETER_ERROR;
            return evaluate(context, substitute(context, line.substring(6), true), true);
        }
        if (lower.equals("evalr")) {
            if (context.readBuffer().isEmpty()) return StatementResult.PARAMETER_ERROR;
            return evaluate(context, substitute(context, context.readBuffer(), true), true);
        }
        if (lower.equals("rounddown") || lower.equals("floor")) {
            return round(context, -1);
        }
        if (lower.equals("roundup") || lower.equals("ceil")) {
            return round(context, 1);
        }
        if (lower.equals("round") || lower.equals("nearest")) {
            return round(context, 0);
        }
        if (lower.startsWith("concat ")) {
            if (line.length() <= 7 || context.readBuffer().isEmpty()) {
                return StatementResult.PARAMETER_ERROR;
            }
            context.writeBuffer(substitute(context, line.substring(7), false));
            return StatementResult.OK;
        }
        if (lower.startsWith("eq ")) {
            if (line.length() <= 3 || context.readBuffer().isEmpty()) {
                return StatementResult.PARAMETER_ERROR;
            }
            context.writeBuffer(context.readBuffer().equals(
                    substitute(context, line.substring(3), false)) ? "true" : "false");
            return StatementResult.OK;
        }
        if (lower.startsWith("gtb ")) return compare(context, line, 4, Comparison.GREATER);
        if (lower.startsWith("ltb ")) return compare(context, line, 4, Comparison.LESS);
        if (lower.startsWith("geb ")) return compare(context, line, 4, Comparison.GREATER_EQUAL);
        if (lower.startsWith("leb ")) return compare(context, line, 4, Comparison.LESS_EQUAL);

        if (lower.startsWith("send ")) {
            if (line.length() <= 5 || context.readBuffer().isEmpty()) {
                return StatementResult.PARAMETER_ERROR;
            }
            RttySystem.broadcast(context.level(), substitute(context, line.substring(5), false),
                    context.readBuffer());
            return StatementResult.OK;
        }
        if (lower.startsWith("listen ")) {
            if (line.length() <= 7) return StatementResult.PARAMETER_ERROR;
            RttySystem.Message message = RttySystem.listen(context.level(),
                    substitute(context, line.substring(7), false));
            if (message != null) context.writeBuffer(message.value());
            return StatementResult.OK;
        }
        return StatementResult.UNRECOGNIZED_COMMAND;
    }

    private StatementResult jump(ParseContext context, String key, boolean validateLength) {
        if (validateLength && key.isEmpty()) return StatementResult.PARAMETER_ERROR;
        Integer destination = context.jumps().get(key);
        if (destination == null) return StatementResult.PARAMETER_ERROR;
        context.setCurrent(destination);
        return StatementResult.OK;
    }

    private StatementResult evaluate(ParseContext context, String statement, boolean rounded) {
        try {
            double result = Calculator.evaluateExpression(statement);
            context.writeBuffer(rounded ? Integer.toString((int) Math.round(result))
                    : Double.toString(result));
            return StatementResult.OK;
        } catch (Throwable ignored) {
            return StatementResult.PARAMETER_ERROR;
        }
    }

    private StatementResult round(ParseContext context, int direction) {
        if (context.readBuffer().isEmpty()) return StatementResult.PARAMETER_ERROR;
        try {
            double value = Double.parseDouble(context.readBuffer());
            long result = direction < 0 ? (int) Math.floor(value)
                    : direction > 0 ? (int) Math.ceil(value) : (int) Math.round(value);
            context.writeBuffer(Long.toString(result));
            return StatementResult.OK;
        } catch (Exception ignored) {
            return StatementResult.PARAMETER_ERROR;
        }
    }

    private StatementResult compare(ParseContext context, String line, int start, Comparison comparison) {
        if (line.length() <= start || context.readBuffer().isEmpty()) {
            return StatementResult.PARAMETER_ERROR;
        }
        try {
            double buffer = Double.parseDouble(context.readBuffer());
            double value = Double.parseDouble(substitute(context, line.substring(start), false));
            boolean result = switch (comparison) {
                case GREATER -> value > buffer;
                case LESS -> value < buffer;
                case GREATER_EQUAL -> value >= buffer;
                case LESS_EQUAL -> value <= buffer;
            };
            context.writeBuffer(result ? "true" : "false");
            return StatementResult.OK;
        } catch (Exception ignored) {
            return StatementResult.PARAMETER_ERROR;
        }
    }

    public String substitute(ParseContext context, String statement, boolean forceNumber) {
        if (!statement.contains("$")) return statement;
        StringBuilder joined = new StringBuilder();
        StringBuilder variableName = new StringBuilder();
        boolean readingVariable = false;
        for (char character : statement.toCharArray()) {
            if (character == '$') {
                if (!readingVariable) {
                    readingVariable = true;
                } else {
                    String name = variableName.toString();
                    String value = name.equals("buffer") ? context.readBuffer()
                            : context.variables().getString(name);
                    if (forceNumber && value.isEmpty()) value = "0";
                    joined.append(value);
                    variableName.setLength(0);
                    readingVariable = false;
                }
            } else if (readingVariable) {
                variableName.append(character);
            } else {
                joined.append(character);
            }
        }
        return joined.toString();
    }

    @Override
    public void generateJumpPoints(ParseContext context, String line, int index) {
        if (line.startsWith("dest ") && line.length() > 5) {
            context.jumps().put(line.substring(5), index);
        }
    }

    private enum Comparison {
        GREATER,
        LESS,
        GREATER_EQUAL,
        LESS_EQUAL
    }
}
