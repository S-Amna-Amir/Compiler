package lexical_analyzer;

public class Token {
    // Token types as constants
    public static final int KEYWORD = 1;
    public static final int IDENTIFIER = 2;
    public static final int INTEGER = 3;
    public static final int DECIMAL = 4;
    public static final int BOOLEAN = 5;
    public static final int CHARACTER = 6;
    public static final int OPERATOR = 7;
    public static final int PUNCTUATOR = 8;
    public static final int EOF = 0;

    private int type;
    private String value;

    public Token(int type, String value) {
        this.type = type;
        this.value = value;
    }



    public int getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Token{type=" + getTypeName(type) + ", value='" + value + "'}";
    }

    private String getTypeName(int type) {
        switch (type) {
            case KEYWORD: return "KEYWORD";
            case IDENTIFIER: return "IDENTIFIER";
            case INTEGER: return "INTEGER";
            case DECIMAL: return "DECIMAL";
            case BOOLEAN: return "BOOLEAN";
            case CHARACTER: return "CHARACTER";
            case OPERATOR: return "OPERATOR";
            case PUNCTUATOR: return "PUNCTUATOR";
            case EOF: return "EOF";
            default: return "UNKNOWN";
        }
    }
}
