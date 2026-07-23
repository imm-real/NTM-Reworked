package com.hbm.ntm.autocal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public interface IParse {
    StatementResult eval(ParseContext context, String line);

    void generateJumpPoints(ParseContext context, String line, int index);

    enum StatementResult {
        OK,
        UNRECOGNIZED_COMMAND,
        PARAMETER_ERROR,
        END_TICK,
        SHUTDOWN,
        SKIP,
        UNDEFINED,
        STACK_EXCEEDED
    }

    final class ParseContext {
        public static final int MAX_BUFFER_LENGTH = 256;
        public static final int MAX_STACK_SIZE = 256;

        private ServerLevel level;
        private CompoundTag variables = new CompoundTag();
        private final Map<String, Integer> jumps = new HashMap<>();
        private String buffer = "";
        private final String[] stack = new String[MAX_STACK_SIZE];
        private int stackSize;
        private String splitString = ";";
        private int clockSpeed = 1;
        private int current;

        public ParseContext(ServerLevel level) {
            this.level = level;
            Arrays.fill(stack, "");
        }

        public ServerLevel level() {
            return level;
        }

        public void setLevel(ServerLevel level) {
            this.level = level;
        }

        public CompoundTag variables() {
            return variables;
        }

        public Map<String, Integer> jumps() {
            return jumps;
        }

        public String readBuffer() {
            return buffer;
        }

        public boolean writeBuffer(String value) {
            if (value.length() > MAX_BUFFER_LENGTH) {
                buffer = value.substring(0, MAX_BUFFER_LENGTH);
                return false;
            }
            buffer = value;
            return true;
        }

        public boolean push(String value) {
            if (stackSize >= MAX_STACK_SIZE) return false;
            if (value.length() > MAX_BUFFER_LENGTH) value = value.substring(0, MAX_BUFFER_LENGTH);
            stack[stackSize++] = value;
            return true;
        }

        public String pop() {
            if (stackSize <= 0) return null;
            if (stackSize > MAX_STACK_SIZE) stackSize = MAX_STACK_SIZE;
            String value = stack[--stackSize];
            stack[stackSize] = "";
            return value;
        }

        public String peek() {
            if (stackSize <= 0) return null;
            if (stackSize > MAX_STACK_SIZE) stackSize = MAX_STACK_SIZE;
            return stack[stackSize - 1];
        }

        public String splitString() {
            return splitString;
        }

        public void setSplitString(String splitString) {
            this.splitString = splitString;
        }

        public int clockSpeed() {
            return clockSpeed;
        }

        public void setClockSpeed(int clockSpeed) {
            this.clockSpeed = clockSpeed;
        }

        public int current() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public void turnOff() {
            clockSpeed = 1;
            current = 0;
            buffer = "";
            if (!variables.isEmpty()) variables = new CompoundTag();
        }

        public void load(CompoundTag tag, String[] script, IParse parser) {
            current = tag.getInt("current");
            clockSpeed = tag.getInt("clockSpeed");
            buffer = tag.getString("buffer");
            splitString = tag.getString("splitString");
            variables = tag.getCompound("variables");
            stackSize = tag.getInt("stackSize");
            for (int i = 0; i < MAX_STACK_SIZE; i++) stack[i] = tag.getString("st" + i);
            for (int i = 0; i < script.length; i++) parser.generateJumpPoints(this, script[i], i);
        }

        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("current", current);
            tag.putInt("clockSpeed", clockSpeed);
            tag.putString("buffer", buffer);
            tag.putString("splitString", splitString);
            tag.put("variables", variables.copy());
            tag.putInt("stackSize", stackSize);
            for (int i = 0; i < MAX_STACK_SIZE; i++) tag.putString("st" + i, stack[i]);
        }
    }
}
