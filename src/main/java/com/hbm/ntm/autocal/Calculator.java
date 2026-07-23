package com.hbm.ntm.autocal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Stack;

public final class Calculator {
    private static int factorialCurrentN;

    private Calculator() {
    }

    public static double evaluateExpression(String input) {
        if (input.contains("^")) input = preEvaluatePower(input);

        char[] tokens = input.toCharArray();
        Stack<Double> values = new Stack<>();
        Stack<String> operators = new Stack<>();

        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i] == ' ') continue;

            if (tokens[i] >= '0' && tokens[i] <= '9' || tokens[i] == '.'
                    || tokens[i] == '-' && (i == 0 || "+-*/^(".contains(String.valueOf(tokens[i - 1])))) {
                StringBuilder buffer = new StringBuilder();
                if (tokens[i] == '-') {
                    buffer.append('-');
                    i++;
                }
                while (i < tokens.length && (tokens[i] >= '0' && tokens[i] <= '9' || tokens[i] == '.')) {
                    buffer.append(tokens[i++]);
                }
                values.push(Double.parseDouble(buffer.toString()));
                i--;
            } else if (tokens[i] == '(') {
                operators.push(Character.toString(tokens[i]));
            } else if (tokens[i] == ')') {
                while (!operators.isEmpty() && operators.peek().charAt(0) != '(') {
                    values.push(evaluateOperator(operators.pop().charAt(0), values.pop(), values.pop()));
                }
                operators.pop();
                if (!operators.isEmpty() && operators.peek().length() > 1) {
                    values.push(evaluateFunction(operators.pop(), values.pop()));
                }
            } else if ("+-*/^".indexOf(tokens[i]) >= 0) {
                while (!operators.isEmpty()
                        && hasPrecedence(String.valueOf(tokens[i]), operators.peek())) {
                    values.push(evaluateOperator(operators.pop().charAt(0), values.pop(), values.pop()));
                }
                operators.push(Character.toString(tokens[i]));
            } else if (tokens[i] == '!') {
                values.push((double) factorial((int) Math.round(values.pop())));
            } else if (tokens[i] >= 'A' && tokens[i] <= 'Z'
                    || tokens[i] >= 'a' && tokens[i] <= 'z') {
                StringBuilder word = new StringBuilder();
                while (i < tokens.length && (tokens[i] >= 'A' && tokens[i] <= 'Z'
                        || tokens[i] >= 'a' && tokens[i] <= 'z')) {
                    word.append(tokens[i++]);
                }
                String function = word.toString();
                if (function.equalsIgnoreCase("pi")) values.push(Math.PI);
                else if (function.equalsIgnoreCase("e")) values.push(Math.E);
                else operators.push(function.toLowerCase(Locale.ROOT));
                i--;
            }
        }

        while (!operators.empty()) {
            values.push(evaluateOperator(operators.pop().charAt(0), values.pop(), values.pop()));
        }
        return values.pop();
    }

    private static double evaluateOperator(char operator, double x, double y) {
        return switch (operator) {
            case '+' -> y + x;
            case '-' -> y - x;
            case '*' -> y * x;
            case '/' -> y / x;
            case '^' -> Math.pow(y, x);
            default -> 0;
        };
    }

    private static double evaluateFunction(String function, double x) {
        return switch (function) {
            case "sqrt" -> Math.sqrt(x);
            case "sin" -> Math.sin(x);
            case "cos" -> Math.cos(x);
            case "tan" -> Math.tan(x);
            case "asin" -> Math.asin(x);
            case "acos" -> Math.acos(x);
            case "atan" -> Math.atan(x);
            case "log" -> Math.log10(x);
            case "ln" -> Math.log(x);
            case "ceil" -> Math.ceil(x);
            case "floor" -> Math.floor(x);
            case "round" -> Math.round(x);
            default -> 0;
        };
    }

    private static boolean hasPrecedence(String first, String second) {
        if (second.length() > 1) return false;
        char firstChar = first.charAt(0);
        char secondChar = second.charAt(0);
        if (secondChar == '(' || secondChar == ')') return false;
        return firstChar != '*' && firstChar != '/' && firstChar != '^'
                || secondChar != '+' && secondChar != '-';
    }

    private static String preEvaluatePower(String input) {
        do {
            int power = input.lastIndexOf('^');

            boolean previousIsParentheses = input.charAt(power - 1) == ')';
            int depth = previousIsParentheses ? 1 : 0;
            int baseStart = previousIsParentheses ? power - 2 : power - 1;
            baseLoop:
            for (; baseStart >= 0; baseStart--) {
                switch (input.charAt(baseStart)) {
                    case ')' -> {
                        if (previousIsParentheses) depth++;
                        else break baseLoop;
                    }
                    case '(' -> {
                        if (previousIsParentheses && depth > 0) depth--;
                        else break baseLoop;
                    }
                    case '+', '-', '*', '/', '^' -> {
                        if (depth == 0) break baseLoop;
                    }
                    default -> {
                    }
                }
            }
            baseStart++;
            if (depth > 0) throw new IllegalArgumentException("Incomplete parentheses");

            boolean nextIsParentheses = input.charAt(power + 1) == '(';
            depth = nextIsParentheses ? 1 : 0;
            int exponentEnd = nextIsParentheses ? power + 2 : power + 1;
            exponentLoop:
            for (; exponentEnd < input.length(); exponentEnd++) {
                switch (input.charAt(exponentEnd)) {
                    case '(' -> {
                        if (nextIsParentheses) depth++;
                        else break exponentLoop;
                    }
                    case ')' -> {
                        if (nextIsParentheses && depth > 0) depth--;
                        else break exponentLoop;
                    }
                    case '+', '-', '*', '/', '^' -> {
                        if (depth == 0) break exponentLoop;
                    }
                    default -> {
                    }
                }
            }
            if (depth > 0) throw new IllegalArgumentException("Incomplete parentheses");

            double base = evaluateExpression(input.substring(baseStart, power));
            double exponent = evaluateExpression(input.substring(power + 1, exponentEnd));
            String result = new BigDecimal(Math.pow(base, exponent), MathContext.DECIMAL64).toPlainString();
            input = input.substring(0, baseStart) + result + input.substring(exponentEnd);
        } while (input.contains("^"));
        return input;
    }

    private static int factorial(int value) {
        if (value < 0) throw new IllegalArgumentException("Factorial needs n >= 0");
        if (value < 2) return 1;
        int product = 1;
        int result = 1;
        factorialCurrentN = 1;
        int half = 0;
        int shift = 0;
        int high = 1;
        int log = log2(value);
        while (half != value) {
            shift += half;
            half = value >> log--;
            int length = high;
            high = half - 1 | 1;
            length = (high - length) / 2;
            if (length > 0) {
                product *= factorialProduct(length);
                result *= product;
            }
        }
        return result << shift;
    }

    private static int factorialProduct(int value) {
        int middle = value / 2;
        if (middle == 0) return factorialCurrentN += 2;
        if (value == 2) return (factorialCurrentN += 2) * (factorialCurrentN += 2);
        return factorialProduct(value - middle) * factorialProduct(middle);
    }

    private static int log2(int value) {
        int log = 0;
        if ((value & 0xffff0000) != 0) {
            value >>>= 16;
            log = 16;
        }
        if (value >= 256) {
            value >>>= 8;
            log += 8;
        }
        if (value >= 16) {
            value >>>= 4;
            log += 4;
        }
        if (value >= 4) {
            value >>>= 2;
            log += 2;
        }
        return log + (value >>> 1);
    }
}
