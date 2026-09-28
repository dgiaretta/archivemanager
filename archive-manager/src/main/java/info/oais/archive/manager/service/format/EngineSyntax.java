package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.Expression.FieldRef;
import info.oais.infomodel.structure.description.Expression.Op;
import info.oais.infomodel.structure.description.Scope;

/**
 * How each engine writes a description's {@link Expression}s. A field
 * reference becomes a path from the element that uses it, worked out by
 * {@link Scope}; the operators are each language's own. Every expression is
 * rendered fully parenthesised, and only ever from a parsed expression tree,
 * never from text a user typed.
 */
final class EngineSyntax {

    private EngineSyntax() {
    }

    /** DFDL expressions (DPath): {@code ../tns:n}, {@code eq}, {@code idiv}. */
    static Expression.Syntax dfdl(Scope scope, String fromId, int extraLevels) {
        return new Base(scope, fromId) {
            @Override
            String path(Scope.Resolved r) {
                return "../".repeat(r.nodesUp() + extraLevels) + "tns:" + r.target().name();
            }

            @Override
            public String stringLiteral(String value) {
                return "'" + value.replace("'", "''") + "'";
            }

            @Override
            public String binary(Op op) {
                return switch (op) {
                    case OR -> "or";
                    case AND -> "and";
                    case EQ -> "eq";
                    case NE -> "ne";
                    case LT -> "lt";
                    case LE -> "le";
                    case GT -> "gt";
                    case GE -> "ge";
                    case ADD -> "+";
                    case SUB -> "-";
                    case MUL -> "*";
                    case DIV -> "idiv";
                    case MOD -> "mod";
                    default -> throw new IllegalArgumentException(op.toString());
                };
            }

            @Override
            public String unary(Op op, String operand) {
                return op == Op.NOT ? "fn:not(" + operand + ")" : "(-" + operand + ")";
            }
        };
    }

    /** DRB SDF queries (XQuery): {@code ../n}, {@code =}, {@code idiv}. */
    static Expression.Syntax drb(Scope scope, String fromId, int extraLevels) {
        return new Base(scope, fromId) {
            @Override
            String path(Scope.Resolved r) {
                return "../".repeat(r.nodesUp() + extraLevels) + r.target().name();
            }

            @Override
            public String stringLiteral(String value) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }

            @Override
            public String binary(Op op) {
                return switch (op) {
                    case OR -> "or";
                    case AND -> "and";
                    case EQ -> "=";
                    case NE -> "!=";
                    case LT -> "<";
                    case LE -> "<=";
                    case GT -> ">";
                    case GE -> ">=";
                    case ADD -> "+";
                    case SUB -> "-";
                    case MUL -> "*";
                    case DIV -> "idiv";
                    case MOD -> "mod";
                    default -> throw new IllegalArgumentException(op.toString());
                };
            }

            @Override
            public String unary(Op op, String operand) {
                return op == Op.NOT ? "not(" + operand + ")" : "(-" + operand + ")";
            }
        };
    }

    /** Kaitai Struct expressions: {@code _parent.n}, {@code ==}. */
    static Expression.Syntax kaitai(Scope scope, String fromId) {
        return new Base(scope, fromId) {
            @Override
            String path(Scope.Resolved r) {
                return "_parent.".repeat(r.typesUp()) + r.target().name();
            }

            @Override
            public String stringLiteral(String value) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }

            @Override
            public String binary(Op op) {
                return switch (op) {
                    case OR -> "or";
                    case AND -> "and";
                    case EQ -> "==";
                    case NE -> "!=";
                    case LT -> "<";
                    case LE -> "<=";
                    case GT -> ">";
                    case GE -> ">=";
                    case ADD -> "+";
                    case SUB -> "-";
                    case MUL -> "*";
                    case DIV -> "/";
                    case MOD -> "%";
                    default -> throw new IllegalArgumentException(op.toString());
                };
            }

            @Override
            public String unary(Op op, String operand) {
                return op == Op.NOT ? "not " + operand : "(-" + operand + ")";
            }
        };
    }

    private abstract static class Base implements Expression.Syntax {
        private final Scope scope;
        private final String fromId;

        Base(Scope scope, String fromId) {
            this.scope = scope;
            this.fromId = fromId;
        }

        abstract String path(Scope.Resolved resolved);

        @Override
        public String intLiteral(long value) {
            return value < 0 ? "(" + value + ")" : Long.toString(value);
        }

        @Override
        public String fieldRef(FieldRef ref) {
            return path(scope.resolve(fromId, ref.name()).orElseThrow(() -> new IllegalStateException(
                    "'" + ref.name() + "' doesn't resolve from " + fromId + " - validate the description first")));
        }
    }
}
