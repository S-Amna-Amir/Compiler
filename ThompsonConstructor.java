package lexical_analyzer;

import java.io.*;
import java.util.*;

public class ThompsonConstructor {

    // --- NFA Structures ---
    public static class NFAState {
        public int id;
        public int tokenType = -1; // -1 indicates non-accepting.
        // Transitions: a null key represents an ε (epsilon) transition.
        public Map<Character, List<NFAState>> transitions = new HashMap<>();

        public NFAState(int id) {
            this.id = id;
        }

        public void addTransition(Character c, NFAState next) {
            transitions.computeIfAbsent(c, k -> new ArrayList<>()).add(next);
        }
    }

    public static class NFA {
        public NFAState start;
        // For the master NFA the accept state may be null.
        public NFAState accept;

        public NFA(NFAState start, NFAState accept) {
            this.start = start;
            this.accept = accept;
        }
    }

    // Global counter for unique state IDs.
    private static int stateId = 0;

    // Return the next available state id.
    public static int getNextStateId() {
        return stateId++;
    }

    // --- ALPHABET for negated character classes ---
    // For simplicity, we consider printable ASCII characters (32 to 126) plus tab, newline, and carriage return.
    private static final List<Character> ALPHABET;
    static {
        ALPHABET = new ArrayList<>();
        for (char c = 32; c < 127; c++) {
            ALPHABET.add(c);
        }
        ALPHABET.add('\t');
        ALPHABET.add('\n');
        ALPHABET.add('\r');
    }

    // --- Thompson’s Construction Building Blocks ---
    public static NFA literal(char c) {
        NFAState start = new NFAState(getNextStateId());
        NFAState accept = new NFAState(getNextStateId());
        start.addTransition(c, accept);
        return new NFA(start, accept);
    }

    public static NFA epsilon() {
        NFAState start = new NFAState(getNextStateId());
        NFAState accept = new NFAState(getNextStateId());
        start.addTransition(null, accept);
        return new NFA(start, accept);
    }

    public static NFA concat(NFA first, NFA second) {
        first.accept.addTransition(null, second.start);
        return new NFA(first.start, second.accept);
    }

    public static NFA union(NFA first, NFA second) {
        NFAState start = new NFAState(getNextStateId());
        NFAState accept = new NFAState(getNextStateId());
        start.addTransition(null, first.start);
        start.addTransition(null, second.start);
        first.accept.addTransition(null, accept);
        second.accept.addTransition(null, accept);
        return new NFA(start, accept);
    }

    public static NFA kleeneStar(NFA nfa) {
        NFAState start = new NFAState(getNextStateId());
        NFAState accept = new NFAState(getNextStateId());
        start.addTransition(null, nfa.start);
        start.addTransition(null, accept);
        nfa.accept.addTransition(null, nfa.start);
        nfa.accept.addTransition(null, accept);
        return new NFA(start, accept);
    }

    public static NFA plus(NFA nfa) {
        return concat(nfa, kleeneStar(nfa));
    }

    public static NFA optional(NFA nfa) {
        return union(epsilon(), nfa);
    }

    // --- Recursive Descent Regex Parser ---
    // Grammar:
    //   Expression ::= Term ('|' Term)*
    //   Term       ::= Factor+
    //   Factor     ::= Base ('*' | '+' | '?')*
    //   Base       ::= ( Expression ) | CharacterClass | StringLiteral | Literal
    public static class RegexParser {
        String regex;
        int pos;

        public RegexParser(String regex) {
            this.regex = regex;
            this.pos = 0;
        }

        public NFA parse() throws Exception {
            NFA result = parseExpression();
            if (pos < regex.length()) {
                throw new Exception("Unexpected character at position " + pos);
            }
            return result;
        }

        // Expression ::= Term ('|' Term)*
        private NFA parseExpression() throws Exception {
            NFA term = parseTerm();
            while (match('|')) {
                NFA right = parseTerm();
                term = union(term, right);
            }
            return term;
        }

        // Term ::= Factor+
        private NFA parseTerm() throws Exception {
            NFA result = null;
            while (pos < regex.length() && peek() != '|' && peek() != ')') {
                NFA factor = parseFactor();
                if (result == null) {
                    result = factor;
                } else {
                    result = concat(result, factor);
                }
            }
            if (result == null) {
                return epsilon();
            }
            return result;
        }

        // Factor ::= Base ('*' | '+' | '?')*
        private NFA parseFactor() throws Exception {
            NFA base = parseBase();
            while (pos < regex.length()) {
                char c = peek();
                if (c == '*') {
                    consume();
                    base = kleeneStar(base);
                } else if (c == '+') {
                    consume();
                    base = plus(base);
                } else if (c == '?') {
                    consume();
                    base = optional(base);
                } else {
                    break;
                }
            }
            return base;
        }

        // Base ::= ( Expression ) | CharacterClass | StringLiteral | Literal
        private NFA parseBase() throws Exception {
            char c = peek();
            if (c == '(') {
                consume(); // consume '('
                NFA expr = parseExpression();
                if (!match(')')) {
                    throw new Exception("Expected ')' at position " + pos);
                }
                return expr;
            } else if (c == '[') {
                return parseCharacterClass();
            } else if (c == '"') {
                return parseStringLiteral();
            } else if (c == '\\') {
                consume(); // consume '\'
                if (pos >= regex.length())
                    throw new Exception("Escape character at end of regex");
                char escaped = consume();
                return literal(escaped);
            } else {
                return literal(consume());
            }
        }

        // --- Modified parseCharacterClass to support negation ---
        private NFA parseCharacterClass() throws Exception {
            if (!match('[')) {
                throw new Exception("Expected '[' at position " + pos);
            }
            Set<Character> allowedChars = new HashSet<>();
            boolean negate = false;
            if (peek() == '^') {
                negate = true;
                consume();
            }
            while (pos < regex.length() && peek() != ']') {
                char startChar = consume();
                if (peek() == '-' && (pos + 1 < regex.length() && regex.charAt(pos + 1) != ']')) {
                    consume(); // consume '-'
                    char endChar = consume();
                    for (char ch = startChar; ch <= endChar; ch++) {
                        allowedChars.add(ch);
                    }
                } else {
                    allowedChars.add(startChar);
                }
            }
            if (!match(']')) {
                throw new Exception("Expected ']' at position " + pos);
            }
            if (negate) {
                // Compute the complement from the ALPHABET.
                Set<Character> complement = new HashSet<>(ALPHABET);
                complement.removeAll(allowedChars);
                allowedChars = complement;
            }
            NFA result = null;
            for (char ch : allowedChars) {
                NFA lit = literal(ch);
                if (result == null) {
                    result = lit;
                } else {
                    result = union(result, lit);
                }
            }
            if (result == null) {
                throw new Exception("Empty character class at position " + pos);
            }
            return result;
        }

        // Parse a string literal enclosed in double quotes.
        private NFA parseStringLiteral() throws Exception {
            if (!match('"')) {
                throw new Exception("Expected '\"' at position " + pos);
            }
            NFA result = null;
            while (pos < regex.length() && peek() != '"') {
                char c = peek();
                if (c == '\\') {
                    consume();
                    if (pos >= regex.length())
                        throw new Exception("Escape at end of string literal");
                    c = consume();
                } else {
                    consume();
                }
                NFA lit = literal(c);
                if (result == null) {
                    result = lit;
                } else {
                    result = concat(result, lit);
                }
            }
            if (!match('"')) {
                throw new Exception("Expected closing '\"' at position " + pos);
            }
            if (result == null) {
                result = epsilon();
            }
            return result;
        }

        private char peek() {
            return regex.charAt(pos);
        }

        private char consume() {
            return regex.charAt(pos++);
        }

        private boolean match(char expected) {
            if (pos < regex.length() && regex.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }
    } // End RegexParser

    // Build a token NFA from a regex and annotate its accept state with tokenType.
    public static NFA buildTokenNFA(String regex, int tokenType) throws Exception {
        RegexParser parser = new RegexParser(regex);
        NFA nfa = parser.parse();
        nfa.accept.tokenType = tokenType;
        return nfa;
    }

    // Print the NFA by traversing it and printing each state's transitions.
    // Also prints the total number of unique (reachable) NFA states.
    public static void printNFA(NFA nfa, PrintWriter out) {
        Set<NFAState> visited = new HashSet<>();
        Queue<NFAState> queue = new LinkedList<>();
        queue.add(nfa.start);
        visited.add(nfa.start);
        while (!queue.isEmpty()) {
            NFAState state = queue.poll();
            out.print("State " + state.id + ": ");
            for (Map.Entry<Character, List<NFAState>> entry : state.transitions.entrySet()) {
                Character transChar = entry.getKey();
                String label = (transChar == null) ? "ε" : transChar.toString();
                out.print("[" + label + " -> ");
                for (NFAState next : entry.getValue()) {
                    out.print(next.id + " ");
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
                out.print("] ");
            }
            out.println();
        }
        out.println("Total unique NFA states: " + visited.size());
    }
}
