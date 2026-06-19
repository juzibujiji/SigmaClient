package com.elfmcys.yesstevemodel.client.animation.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class MolangParser {
    private final String source;
    private int index;

    private MolangParser(String source) {
        this.source = source == null ? "" : source;
    }

    public static MolangExpression parse(String source) throws ParseException {
        MolangParser parser = new MolangParser(source);
        MolangExpression.Node node = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected token");
        }
        return new MolangExpression(source, node);
    }

    private MolangExpression.Node parseExpression() throws ParseException {
        return parseOr();
    }

    private MolangExpression.Node parseOr() throws ParseException {
        MolangExpression.Node node = parseAnd();
        while (match("||")) {
            MolangExpression.Node left = node;
            MolangExpression.Node right = parseAnd();
            node = context -> MolangValue.of(left.evaluate(context).asBoolean() || right.evaluate(context).asBoolean());
        }
        return node;
    }

    private MolangExpression.Node parseAnd() throws ParseException {
        MolangExpression.Node node = parseEquality();
        while (match("&&")) {
            MolangExpression.Node left = node;
            MolangExpression.Node right = parseEquality();
            node = context -> MolangValue.of(left.evaluate(context).asBoolean() && right.evaluate(context).asBoolean());
        }
        return node;
    }

    private MolangExpression.Node parseEquality() throws ParseException {
        MolangExpression.Node node = parseComparison();
        while (true) {
            if (match("==")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseComparison();
                node = context -> MolangValue.of(Math.abs(left.evaluate(context).asDouble() - right.evaluate(context).asDouble()) < 0.000001D);
            } else if (match("!=")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseComparison();
                node = context -> MolangValue.of(Math.abs(left.evaluate(context).asDouble() - right.evaluate(context).asDouble()) >= 0.000001D);
            } else {
                return node;
            }
        }
    }

    private MolangExpression.Node parseComparison() throws ParseException {
        MolangExpression.Node node = parseTerm();
        while (true) {
            if (match(">=")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseTerm();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() >= right.evaluate(context).asDouble());
            } else if (match("<=")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseTerm();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() <= right.evaluate(context).asDouble());
            } else if (match(">")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseTerm();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() > right.evaluate(context).asDouble());
            } else if (match("<")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseTerm();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() < right.evaluate(context).asDouble());
            } else {
                return node;
            }
        }
    }

    private MolangExpression.Node parseTerm() throws ParseException {
        MolangExpression.Node node = parseFactor();
        while (true) {
            if (match("+")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseFactor();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() + right.evaluate(context).asDouble());
            } else if (match("-")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseFactor();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() - right.evaluate(context).asDouble());
            } else {
                return node;
            }
        }
    }

    private MolangExpression.Node parseFactor() throws ParseException {
        MolangExpression.Node node = parseUnary();
        while (true) {
            if (match("*")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseUnary();
                node = context -> MolangValue.of(left.evaluate(context).asDouble() * right.evaluate(context).asDouble());
            } else if (match("/")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseUnary();
                node = context -> {
                    double divisor = right.evaluate(context).asDouble();
                    return MolangValue.of(Math.abs(divisor) < 0.000001D ? 0.0D : left.evaluate(context).asDouble() / divisor);
                };
            } else if (match("%")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseUnary();
                node = context -> {
                    double divisor = right.evaluate(context).asDouble();
                    return MolangValue.of(Math.abs(divisor) < 0.000001D ? 0.0D : left.evaluate(context).asDouble() % divisor);
                };
            } else {
                return node;
            }
        }
    }

    private MolangExpression.Node parseUnary() throws ParseException {
        if (match("!")) {
            MolangExpression.Node node = parseUnary();
            return context -> MolangValue.of(!node.evaluate(context).asBoolean());
        }
        if (match("-")) {
            MolangExpression.Node node = parseUnary();
            return context -> MolangValue.of(-node.evaluate(context).asDouble());
        }
        if (match("+")) {
            return parseUnary();
        }
        return parsePrimary();
    }

    private MolangExpression.Node parsePrimary() throws ParseException {
        skipWhitespace();
        if (match("(")) {
            MolangExpression.Node node = parseExpression();
            expect(")");
            return node;
        }
        if (isDigit(peek()) || (peek() == '.' && isDigit(peekNext()))) {
            return numberNode(readNumber());
        }
        if (isIdentifierStart(peek())) {
            String identifier = readIdentifier();
            String lower = identifier.toLowerCase(Locale.ROOT);
            if ("true".equals(lower)) {
                return context -> MolangValue.ONE;
            }
            if ("false".equals(lower)) {
                return context -> MolangValue.ZERO;
            }
            if (match("(")) {
                List<MolangExpression.Node> args = new ArrayList<>();
                if (!check(")")) {
                    do {
                        args.add(parseExpression());
                    } while (match(","));
                }
                expect(")");
                return functionNode(lower, args);
            }
            return context -> context.resolveIdentifier(lower);
        }
        throw error("Expected expression");
    }

    private MolangExpression.Node numberNode(double value) {
        return context -> MolangValue.of(value);
    }

    private MolangExpression.Node functionNode(String name, List<MolangExpression.Node> args) throws ParseException {
        return switch (name) {
            case "math.sin" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.sin(args.get(0).evaluate(context).asDouble())));
            case "math.cos" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.cos(args.get(0).evaluate(context).asDouble())));
            case "math.abs" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.abs(args.get(0).evaluate(context).asDouble())));
            case "math.sqrt" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.sqrt(Math.max(0.0D, args.get(0).evaluate(context).asDouble()))));
            case "math.floor" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.floor(args.get(0).evaluate(context).asDouble())));
            case "math.ceil" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.ceil(args.get(0).evaluate(context).asDouble())));
            case "math.round" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.round(args.get(0).evaluate(context).asDouble())));
            case "math.trunc" -> requireArgs(name, args, 1, context -> MolangValue.of((long) args.get(0).evaluate(context).asDouble()));
            case "math.min" -> requireArgs(name, args, 2, context -> MolangValue.of(Math.min(args.get(0).evaluate(context).asDouble(), args.get(1).evaluate(context).asDouble())));
            case "math.max" -> requireArgs(name, args, 2, context -> MolangValue.of(Math.max(args.get(0).evaluate(context).asDouble(), args.get(1).evaluate(context).asDouble())));
            case "math.pow" -> requireArgs(name, args, 2, context -> MolangValue.of(Math.pow(args.get(0).evaluate(context).asDouble(), args.get(1).evaluate(context).asDouble())));
            case "math.mod" -> requireArgs(name, args, 2, context -> {
                double divisor = args.get(1).evaluate(context).asDouble();
                return MolangValue.of(Math.abs(divisor) < 0.000001D ? 0.0D : args.get(0).evaluate(context).asDouble() % divisor);
            });
            case "math.clamp" -> requireArgs(name, args, 3, context -> {
                double value = args.get(0).evaluate(context).asDouble();
                double min = args.get(1).evaluate(context).asDouble();
                double max = args.get(2).evaluate(context).asDouble();
                return MolangValue.of(Math.max(min, Math.min(max, value)));
            });
            case "math.lerp" -> requireArgs(name, args, 3, context -> {
                double start = args.get(0).evaluate(context).asDouble();
                double end = args.get(1).evaluate(context).asDouble();
                double delta = args.get(2).evaluate(context).asDouble();
                return MolangValue.of(start + (end - start) * delta);
            });
            case "math.random" -> requireArgs(name, args, 2, context -> {
                double min = args.get(0).evaluate(context).asDouble();
                double max = args.get(1).evaluate(context).asDouble();
                if (max < min) {
                    double swap = min;
                    min = max;
                    max = swap;
                }
                return MolangValue.of(ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max)));
            });
            case "math.random_integer" -> requireArgs(name, args, 2, context -> {
                int min = (int) Math.floor(args.get(0).evaluate(context).asDouble());
                int max = (int) Math.floor(args.get(1).evaluate(context).asDouble());
                if (max < min) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
                return MolangValue.of(ThreadLocalRandom.current().nextInt(min, max + 1));
            });
            case "math.min_angle" -> requireArgs(name, args, 1, context -> MolangValue.of(wrapDegrees(args.get(0).evaluate(context).asDouble())));
            default -> throw error("Unsupported function: " + name);
        };
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private MolangExpression.Node requireArgs(String name, List<MolangExpression.Node> args, int count,
                                              MolangExpression.Node node) throws ParseException {
        if (args.size() != count) {
            throw error("Function " + name + " expects " + count + " args");
        }
        return node;
    }

    private double readNumber() throws ParseException {
        int start = this.index;
        while (isDigit(peek())) {
            this.index++;
        }
        if (peek() == '.') {
            this.index++;
            while (isDigit(peek())) {
                this.index++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            this.index++;
            if (peek() == '+' || peek() == '-') {
                this.index++;
            }
            while (isDigit(peek())) {
                this.index++;
            }
        }
        try {
            return Double.parseDouble(this.source.substring(start, this.index));
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private String readIdentifier() {
        int start = this.index;
        this.index++;
        while (isIdentifierPart(peek())) {
            this.index++;
        }
        return this.source.substring(start, this.index);
    }

    private boolean match(String text) {
        skipWhitespace();
        if (!this.source.startsWith(text, this.index)) {
            return false;
        }
        this.index += text.length();
        return true;
    }

    private void expect(String text) throws ParseException {
        if (!match(text)) {
            throw error("Expected '" + text + "'");
        }
    }

    private boolean check(String text) {
        skipWhitespace();
        return this.source.startsWith(text, this.index);
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(this.source.charAt(this.index))) {
            this.index++;
        }
    }

    private boolean isAtEnd() {
        return this.index >= this.source.length();
    }

    private char peek() {
        return isAtEnd() ? '\0' : this.source.charAt(this.index);
    }

    private char peekNext() {
        return this.index + 1 >= this.source.length() ? '\0' : this.source.charAt(this.index + 1);
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isIdentifierStart(char value) {
        return value == '_' || value == '.' || (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierStart(value) || isDigit(value);
    }

    private ParseException error(String message) {
        return new ParseException(message + " at " + this.index + " in '" + this.source + "'");
    }

    public static final class ParseException extends Exception {
        private ParseException(String message) {
            super(message);
        }
    }
}
