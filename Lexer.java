package lexical_analyzer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.*;

public class Lexer {
    private Reader reader;
    private String input;
    private int pos = 0;
    private int tokenCount = 0;
    private DFA masterDFA;
    private ThompsonConstructor.NFA masterNFA;

    // Reserved words set.
    private static final Set<String> reservedWords = new HashSet<>(Arrays.asList(
            "int", "double", "char", "print", "bool"
    ));

    // Token definitions.
    private static final LinkedHashMap<String, Integer> tokenRegexes = new LinkedHashMap<>();
    static {
        tokenRegexes.put("[ \\t\\n\\r]+", -1);                     // whitespace (ignore)
        // Note: identifiers are still defined as only lowercase letters and underscores.
        tokenRegexes.put("[a-z_]+", Token.IDENTIFIER);
        tokenRegexes.put("[0-9]+\\.[0-9]+", Token.DECIMAL);           // decimal number
        tokenRegexes.put("[0-9]+", Token.INTEGER);                    // integer
        tokenRegexes.put("0|1", Token.BOOLEAN);                       // boolean literal
        tokenRegexes.put("[a-z]", Token.CHARACTER);                   // character literal (single letter)
        tokenRegexes.put("=|\\+|-|\\*|/|%|\\^", Token.OPERATOR);       // operators
        tokenRegexes.put("[\\(\\)\\{\\};,]", Token.PUNCTUATOR);        // punctuators
    }

    // Separate symbol tables (for global and local) for existing usage.
    private Map<String, Token> globalSymbolTable = new HashMap<>();
    private Deque<Map<String, Token>> localScopes = new ArrayDeque<>();

    // A combined symbol table for printing.
    // Key: variable name, Value: map with keys "type" and "scope".
    private Map<String, Map<String, String>> variables = new LinkedHashMap<>();

    // A buffer for lookahead tokens.
    private Queue<Token> tokenBuffer = new LinkedList<>();

    public Lexer(Reader reader) throws IOException {
        this.reader = reader;
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            sb.append((char) c);
        }
        input = sb.toString();
        // Check for matching brackets.
        checkBrackets();
        try {
            buildMasterAutomata();
        } catch (Exception e) {
            throw new IOException("Error building automata: " + e.getMessage(), e);
        }
    }

    // Simple bracket checker for () and {} (ignores brackets inside string literals).
    private void checkBrackets() {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '"') {
                inString = !inString;
            }
            if (inString) continue;
            if (ch == '(' || ch == '{') {
                stack.push(ch);
            } else if (ch == ')') {
                if (stack.isEmpty() || stack.pop() != '(') {
                    System.err.println("Error: Unmatched closing parenthesis at position " + i);
                }
            } else if (ch == '}') {
                if (stack.isEmpty() || stack.pop() != '{') {
                    System.err.println("Error: Unmatched closing brace at position " + i);
                }
            }
        }
        if (!stack.isEmpty()) {
            System.err.println("Error: Unmatched opening bracket(s) detected.");
        }
    }

    // Build master NFA and then the DFA.
    private void buildMasterAutomata() throws Exception {
        List<ThompsonConstructor.NFA> nfaList = new ArrayList<>();
        // For each token definition, build a token NFA.
        for (Map.Entry<String, Integer> entry : tokenRegexes.entrySet()) {
            String regex = entry.getKey();
            int tokenType = entry.getValue();
            ThompsonConstructor.NFA tokenNFA = ThompsonConstructor.buildTokenNFA(regex, tokenType);
            nfaList.add(tokenNFA);
        }
        // Create a master start state.
        ThompsonConstructor.NFAState masterStart = new ThompsonConstructor.NFAState(ThompsonConstructor.getNextStateId());
        for (ThompsonConstructor.NFA nfa : nfaList) {
            masterStart.addTransition(null, nfa.start);
        }
        masterNFA = new ThompsonConstructor.NFA(masterStart, null);
        masterDFA = new DFA(masterNFA);
    }

    // Get next token from DFA simulation.
    private Token getNextToken() {
        // Skip leading whitespace.
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        // Check for unique comment markers.
        if (input.startsWith("^.^", pos)) { // single-line comment marker
            while (pos < input.length() && input.charAt(pos) != '\n') {
                pos++;
            }
            return getNextToken();
        }
        if (input.startsWith("<3", pos)) {  // multi-line comment marker
            pos += 2; // skip starting marker
            while (pos < input.length() && !input.startsWith("<3", pos)) {
                pos++;
            }
            pos += 2; // skip ending marker if found
            return getNextToken();
        }
        if (!tokenBuffer.isEmpty()) {
            return tokenBuffer.poll();
        }
        if (pos >= input.length()) {
            return new Token(Token.EOF, null);
        }
        int[] result = masterDFA.simulate(input, pos);
        int tokenType = result[0];
        int length = result[1];
        if (tokenType == -1 || length == 0) {
            String errorContext = input.substring(pos, Math.min(pos + 10, input.length()));
            throw new RuntimeException("Lexical error at position " + pos +
                    ": invalid token starting with '" + input.charAt(pos) + "'; context: \"" + errorContext + "\"");
        }
        String lexeme = input.substring(pos, pos + length);

        // For identifiers, enforce that only lowercase letters (and underscores) are allowed.
        if (tokenType == Token.IDENTIFIER) {
            if (lexeme.length() < 1) {
                throw new RuntimeException("Lexical error at position " + pos + ": empty identifier.");
            }
            // Since the regex is [a-z_]+, if the full lexeme is not consumed because the next character is uppercase,
            // then the DFA would have returned a token for the lowercase part. We can throw an error if the following character is a letter and not lowercase.
            if (pos + length < input.length()) {
                char nextChar = input.charAt(pos + length);
                if (Character.isLetter(nextChar) && !Character.isLowerCase(nextChar)) {
                    throw new RuntimeException("Lexical error at position " + (pos + length) +
                            ": identifier '" + lexeme + "' must contain only lowercase letters.");
                }
            }
        }

        pos += length;
        return new Token(tokenType, lexeme);
    }

    // Main lexing method.
    public Token yylex() {
        Token t = getNextToken();
        // Skip ignore tokens.
        if (t.getType() == -1) {
            return yylex();
        }
        // Reserved word check.
        if (t.getType() == Token.IDENTIFIER && reservedWords.contains(t.getValue())) {
            t = new Token(Token.KEYWORD, t.getValue());
        }
        // Handle variable declarations.
        if (t.getType() == Token.KEYWORD &&
                (t.getValue().equals("int") || t.getValue().equals("double") ||
                        t.getValue().equals("char") || t.getValue().equals("bool"))) {
            Token next = getNextToken();
            if (next.getType() == Token.IDENTIFIER) {
                String scope = localScopes.isEmpty() ? "global" : "local";
                // Add to combined symbol table.
                Map<String, String> entry = new HashMap<>();
                entry.put("type", t.getValue());
                entry.put("scope", scope);
                variables.put(next.getValue(), entry);
                // Also update separate symbol tables.
                if (localScopes.isEmpty()) {
                    globalSymbolTable.put(next.getValue(), next);
                } else {
                    localScopes.peek().put(next.getValue(), next);
                }
                // Enqueue the identifier token.
                tokenBuffer.add(next);
            } else {
                tokenBuffer.add(next);
            }
        }
        tokenCount++;
        return t;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public Map<String, Token> getGlobalSymbolTable() {
        return globalSymbolTable;
    }

    public Deque<Map<String, Token>> getLocalScopes() {
        return localScopes;
    }

    // Print automata details (NFA, DFA, minimized DFA) as before.
    public void printAutomata(String outputFile) {
        try {
            PrintWriter fileOut = new PrintWriter(outputFile);
            PrintWriter consoleOut = new PrintWriter(System.out, true);

            consoleOut.println("------ Master NFA ------");
            ThompsonConstructor.printNFA(masterNFA, consoleOut);
            consoleOut.println();
            consoleOut.println("------ Master DFA ------");
            masterDFA.printDFA(consoleOut);
            consoleOut.println();
            consoleOut.println("------ DFA Transition Table ------");
            masterDFA.printTransitionTable(consoleOut);
            consoleOut.println();

            DFA minimized = masterDFA.minimize();
            consoleOut.println("------ Minimized DFA ------");
            minimized.printMinimizedDFA(consoleOut);
            consoleOut.println();

            // Write same details to file.
            fileOut.println("------ Master NFA ------");
            ThompsonConstructor.printNFA(masterNFA, fileOut);
            fileOut.println();
            fileOut.println("------ Master DFA ------");
            masterDFA.printDFA(fileOut);
            fileOut.println();
            fileOut.println("------ DFA Transition Table ------");
            masterDFA.printTransitionTable(fileOut);
            fileOut.println();
            fileOut.println("------ Minimized DFA ------");
            minimized.printMinimizedDFA(fileOut);

            // Also print the symbol table.
            printTable(fileOut);

            fileOut.close();
            consoleOut.println("Automata details and symbol table written to " + outputFile);
        } catch (Exception e) {
            System.err.println("Error printing automata: " + e.getMessage());
        }
    }

    // Print the combined symbol table.
    public void printTable(PrintWriter writer) {
        writer.println("\nSymbol Table:");
        writer.printf("%-10s %-10s %-10s\n", "Variable", "Type", "Scope");
        for (Map.Entry<String, Map<String, String>> entry : variables.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue().get("type");
            String scope = entry.getValue().get("scope");
            writer.printf("%-10s %-10s %-10s\n", name, type, scope);
        }
    }
}
