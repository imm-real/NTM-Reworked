package com.hbm.ntm.autocal;

import com.hbm.ntm.ror.RttySystem;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ParseMSES1Ext1 extends ParseMSES1 {
    @Override
    public StatementResult eval(ParseContext context, String line) {
        String lower = line.toLowerCase(Locale.US);

        if (lower.startsWith("splitter ")) {
            if (line.length() <= 9) return StatementResult.PARAMETER_ERROR;
            context.setSplitString(substitute(context, line.substring(9), false));
            return StatementResult.OK;
        }
        if (lower.startsWith("split ")) {
            if (line.length() <= 6) return StatementResult.PARAMETER_ERROR;
            try {
                int index = Integer.parseInt(line.substring(6));
                if (index < 1) return StatementResult.PARAMETER_ERROR;
                String[] fragments = context.readBuffer().split(Pattern.quote(context.splitString()));
                if (index > fragments.length) return StatementResult.PARAMETER_ERROR;
                context.writeBuffer(fragments[index - 1]);
                return StatementResult.OK;
            } catch (Throwable ignored) {
                return StatementResult.PARAMETER_ERROR;
            }
        }
        if (lower.equals("splitcount")) {
            if (context.readBuffer().isEmpty()) return StatementResult.PARAMETER_ERROR;
            String[] fragments = context.readBuffer().split(Pattern.quote(context.splitString()));
            context.writeBuffer(Integer.toString(fragments.length));
            return StatementResult.OK;
        }
        if (lower.equals("push")) {
            if (context.readBuffer().isEmpty()) return StatementResult.PARAMETER_ERROR;
            return context.push(context.readBuffer()) ? StatementResult.OK : StatementResult.STACK_EXCEEDED;
        }
        if (lower.startsWith("push ")) {
            if (line.length() <= 5) return StatementResult.PARAMETER_ERROR;
            return context.push(substitute(context, line.substring(5), false))
                    ? StatementResult.OK : StatementResult.STACK_EXCEEDED;
        }
        if (lower.equals("pop")) {
            String value = context.pop();
            if (value == null) return StatementResult.UNDEFINED;
            context.writeBuffer(value);
            return StatementResult.OK;
        }
        if (lower.equals("peek")) {
            String value = context.peek();
            if (value == null) return StatementResult.UNDEFINED;
            context.writeBuffer(value);
            return StatementResult.OK;
        }
        if (lower.equals("length")) {
            context.writeBuffer(Integer.toString(context.readBuffer().length()));
            return StatementResult.OK;
        }
        if (lower.startsWith("first ")) {
            if (line.length() <= 6) return StatementResult.PARAMETER_ERROR;
            try {
                int length = Integer.parseInt(line.substring(6));
                int maximum = context.readBuffer().length();
                if (length > maximum) length = maximum;
                context.writeBuffer(context.readBuffer().substring(0, length));
                return StatementResult.OK;
            } catch (Exception ignored) {
                return StatementResult.PARAMETER_ERROR;
            }
        }
        if (lower.startsWith("last ")) {
            if (line.length() <= 5) return StatementResult.PARAMETER_ERROR;
            try {
                int length = Integer.parseInt(line.substring(5));
                int maximum = context.readBuffer().length();
                if (length > maximum) length = maximum;
                context.writeBuffer(context.readBuffer().substring(maximum - length, maximum));
                return StatementResult.OK;
            } catch (Exception ignored) {
                return StatementResult.PARAMETER_ERROR;
            }
        }
        if (lower.startsWith("poll ")) {
            if (line.length() <= 5) return StatementResult.PARAMETER_ERROR;
            RttySystem.Message message = RttySystem.listen(context.level(),
                    substitute(context, line.substring(5), false));
            if (message != null && message.tick() >= context.level().getGameTime() - 1L) {
                context.writeBuffer(message.value());
            }
            return StatementResult.OK;
        }
        if (lower.equals("worldtime")) {
            context.writeBuffer(Long.toString(context.level().getGameTime()));
            return StatementResult.OK;
        }
        return super.eval(context, line);
    }
}
