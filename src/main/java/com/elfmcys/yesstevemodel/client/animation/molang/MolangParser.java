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
        MolangExpression.Node node = parser.parseProgram();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected token");
        }
        return new MolangExpression(source, node);
    }

    /**
     * A program is one or more ';'-separated statements; the value of the last statement is
     * returned. YSM models drive variables from dummy bones with scripts like
     * {@code v.bv=0;v.by=0;} so trailing separators are permitted.
     */
    private MolangExpression.Node parseProgram() throws ParseException {
        List<MolangExpression.Node> statements = new ArrayList<>();
        statements.add(parseStatement());
        while (match(";")) {
            skipWhitespace();
            if (isAtEnd()) {
                break;
            }
            statements.add(parseStatement());
        }
        if (statements.size() == 1) {
            return statements.get(0);
        }
        List<MolangExpression.Node> sequence = List.copyOf(statements);
        return context -> {
            MolangValue last = MolangValue.ZERO;
            for (MolangExpression.Node statement : sequence) {
                last = statement.evaluate(context);
            }
            return last;
        };
    }

    /** A statement is either a variable/temp assignment ({@code v.x = expr}) or an expression. */
    private MolangExpression.Node parseStatement() throws ParseException {
        skipWhitespace();
        int savedIndex = this.index;
        if (isIdentifierStart(peek())) {
            String identifier = readIdentifier();
            String lower = identifier.toLowerCase(Locale.ROOT);
            String variableName = null;
            boolean temp = false;
            if (lower.startsWith("variable.")) {
                variableName = lower.substring("variable.".length());
            } else if (lower.startsWith("v.")) {
                variableName = lower.substring("v.".length());
            } else if (lower.startsWith("temp.")) {
                variableName = lower.substring("temp.".length());
                temp = true;
            } else if (lower.startsWith("t.")) {
                variableName = lower.substring("t.".length());
                temp = true;
            }
            if (variableName != null && !variableName.isEmpty()) {
                skipWhitespace();
                if (peek() == '=' && peekNext() != '=') {
                    this.index++;
                    MolangExpression.Node value = parseExpression();
                    String name = variableName;
                    boolean isTemp = temp;
                    return context -> {
                        MolangValue result = value.evaluate(context);
                        context.setVariable(isTemp, name, result.asDouble());
                        return result;
                    };
                }
            }
            this.index = savedIndex;
        }
        return parseExpression();
    }

    private MolangExpression.Node parseExpression() throws ParseException {
        return parseConditional();
    }

    private MolangExpression.Node parseConditional() throws ParseException {
        MolangExpression.Node condition = parseOr();
        if (match("?")) {
            MolangExpression.Node ifTrue = parseExpression();
            // Molang's binary conditional form "cond ? value" evaluates to 0 when false.
            MolangExpression.Node ifFalse = match(":") ? parseConditional() : context -> MolangValue.ZERO;
            return context -> condition.evaluate(context).asBoolean()
                    ? ifTrue.evaluate(context)
                    : ifFalse.evaluate(context);
        }
        return condition;
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
                node = context -> MolangValue.of(equalsValue(left.evaluate(context), right.evaluate(context)));
            } else if (match("!=")) {
                MolangExpression.Node left = node;
                MolangExpression.Node right = parseComparison();
                node = context -> MolangValue.of(!equalsValue(left.evaluate(context), right.evaluate(context)));
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
        MolangExpression.Node node = parseAtom();
        while (match(".")) {
            String property = readSimpleIdentifier().toLowerCase(Locale.ROOT);
            MolangExpression.Node target = node;
            node = context -> target.evaluate(context).property(property);
        }
        return node;
    }

    private MolangExpression.Node parseAtom() throws ParseException {
        skipWhitespace();
        if (match("(")) {
            MolangExpression.Node node = parseExpression();
            expect(")");
            return node;
        }
        if (isDigit(peek()) || (peek() == '.' && isDigit(peekNext()))) {
            return numberNode(readNumber());
        }
        if (peek() == '\'' || peek() == '"') {
            return stringNode(readString());
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
            if ("math.pi".equals(lower)) {
                return context -> MolangValue.of(Math.PI);
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
            // Bedrock molang trigonometry is DEGREE-based (walk/hair expressions use anim_time*360).
            case "math.sin" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.sin(Math.toRadians(args.get(0).evaluate(context).asDouble()))));
            case "math.cos" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.cos(Math.toRadians(args.get(0).evaluate(context).asDouble()))));
            case "math.asin" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, args.get(0).evaluate(context).asDouble()))))));
            case "math.acos" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, args.get(0).evaluate(context).asDouble()))))));
            case "math.atan" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.toDegrees(Math.atan(args.get(0).evaluate(context).asDouble()))));
            case "math.atan2" -> requireArgs(name, args, 2, context -> MolangValue.of(Math.toDegrees(Math.atan2(args.get(0).evaluate(context).asDouble(), args.get(1).evaluate(context).asDouble()))));
            case "math.exp" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.exp(args.get(0).evaluate(context).asDouble())));
            case "math.ln" -> requireArgs(name, args, 1, context -> {
                double value = args.get(0).evaluate(context).asDouble();
                return MolangValue.of(value <= 0.0D ? 0.0D : Math.log(value));
            });
            case "math.abs" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.abs(args.get(0).evaluate(context).asDouble())));
            case "math.sqrt" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.sqrt(Math.max(0.0D, args.get(0).evaluate(context).asDouble()))));
            case "math.floor" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.floor(args.get(0).evaluate(context).asDouble())));
            case "math.ceil" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.ceil(args.get(0).evaluate(context).asDouble())));
            case "math.round" -> requireArgs(name, args, 1, context -> MolangValue.of(Math.round(args.get(0).evaluate(context).asDouble())));
            case "math.trunc" -> requireArgs(name, args, 1, context -> MolangValue.of((long) args.get(0).evaluate(context).asDouble()));
            case "math.hermite_blend" -> requireArgs(name, args, 1, context -> {
                double t = args.get(0).evaluate(context).asDouble();
                return MolangValue.of(3.0D * t * t - 2.0D * t * t * t);
            });
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
            case "math.lerprotate" -> requireArgs(name, args, 3, context -> {
                double start = args.get(0).evaluate(context).asDouble();
                double end = args.get(1).evaluate(context).asDouble();
                double delta = args.get(2).evaluate(context).asDouble();
                return MolangValue.of(start + wrapDegrees(end - start) * delta);
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
            case "query.position", "q.position",
                    "query.position_delta", "q.position_delta",
                    "query.rotation_to_camera", "q.rotation_to_camera" -> requireArgs(name, args, 1, context -> context.callFunction(name,
                            List.of(args.get(0).evaluate(context))));
            case "query.is_item_name_any", "q.is_item_name_any" -> requireArgs(name, args, 2, context -> context.callFunction(name,
                    List.of(args.get(0).evaluate(context), args.get(1).evaluate(context))));
            case "ysm.bone_rot", "ysm.bone_pos", "ysm.bone_scale", "ysm.bone_pivot_abs" -> requireArgs(name, args, 1,
                    context -> context.callFunction(name, List.of(args.get(0).evaluate(context))));
            case "ysm.first_order" -> requireMinArgs(name, args, 2, context -> context.callFunction(name,
                    evaluatedArgs(args, context)));
            case "ysm.second_order" -> requireMinArgs(name, args, 2, context -> context.callFunction(name,
                    evaluatedArgs(args, context)));
            default -> throw error("Unsupported function: " + name);
        };
    }

    private static boolean equalsValue(MolangValue left, MolangValue right) {
        if (left.isString() || right.isString()) {
            return left.asString().equals(right.asString());
        }
        return Math.abs(left.asDouble() - right.asDouble()) < 0.000001D;
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

    private MolangExpression.Node requireMinArgs(String name, List<MolangExpression.Node> args, int minCount,
                                                 MolangExpression.Node node) throws ParseException {
        if (args.size() < minCount) {
            throw error("Function " + name + " expects at least " + minCount + " args");
        }
        return node;
    }

    private static List<MolangValue> evaluatedArgs(List<MolangExpression.Node> args, MolangContext context) {
        List<MolangValue> values = new ArrayList<>(args.size());
        for (MolangExpression.Node arg : args) {
            values.add(arg.evaluate(context));
        }
        return values;
    }

    private MolangExpression.Node stringNode(String value) {
        return context -> MolangValue.of(value);
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

    private String readString() throws ParseException {
        char quote = peek();
        this.index++;
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd()) {
            char value = this.source.charAt(this.index++);
            if (value == quote) {
                return builder.toString();
            }
            if (value == '\\' && !isAtEnd()) {
                char escaped = this.source.charAt(this.index++);
                builder.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '\'' -> '\'';
                    case '"' -> '"';
                    default -> escaped;
                });
            } else {
                builder.append(value);
            }
        }
        throw error("Unterminated string");
    }

    private String readIdentifier() {
        int start = this.index;
        this.index++;
        while (isIdentifierPart(peek())) {
            this.index++;
        }
        return this.source.substring(start, this.index);
    }

    private String readSimpleIdentifier() throws ParseException {
        skipWhitespace();
        if (!isSimpleIdentifierStart(peek())) {
            throw error("Expected property name");
        }
        int start = this.index;
        this.index++;
        while (isSimpleIdentifierPart(peek())) {
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

    private static boolean isSimpleIdentifierStart(char value) {
        return value == '_' || (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private static boolean isSimpleIdentifierPart(char value) {
        return isSimpleIdentifierStart(value) || isDigit(value);
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
