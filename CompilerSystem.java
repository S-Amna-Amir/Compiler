import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompilerSystem {

    /* ===========================
       MAIN: Integrated Compiler Pipeline (File I/O)
       =========================== */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter("output.txt"));
        } catch (IOException ioe) {
            System.out.println("Error creating output file: " + ioe.getMessage());
            return;
        }

        // Ask user for source code file path (must have .rmd extension)
        dualPrint("Enter source code file path (.rmd):", writer);
        String sourceFilePath = scanner.nextLine().trim();
        if (!sourceFilePath.endsWith(".rmd")) {
            dualPrint("Error: Source code file must have extension .rmd", writer);
            writer.close();
            return;
        }
        String sourceCode = readFile(sourceFilePath);
        if (sourceCode == null) {
            dualPrint("Error reading source code file.", writer);
            writer.close();
            return;
        }

        // Ask user for regular expression file path
        dualPrint("Enter regular expression file path:", writer);
        String regexFilePath = scanner.nextLine().trim();
        String regex = readFile(regexFilePath);
        if (regex == null) {
            dualPrint("Error reading regular expression file.", writer);
            writer.close();
            return;
        }
        regex = regex.trim();

        dualPrint("\n=== Compiler System Output ===", writer);

        // ---------- Phase 1: Lexical Analysis ----------
        dualPrint("\n--- Lexical Analysis Phase ---", writer);
        LexicalAnalyzer lex = new LexicalAnalyzer(sourceCode);
        lex.process();
        dualPrint("Token Count: " + lex.getTokenCount(), writer);
        lex.printTokens(writer);
        dualPrint("\nPre-processed Source Code:", writer);
        dualPrint(lex.getPreprocessedSource(), writer);

        // Build the symbol table (using the keywords "global" and "local")
        SymbolTable symbolTable = new SymbolTable();
        // We scan the tokens: if a token "global" or "local" appears, the following identifier
        // (if any) is recorded accordingly. Otherwise, an identifier is assumed local.
        for (int i = 0; i < lex.tokens.size(); i++) {
            Token t = lex.tokens.get(i);
            if (t.text.equals("global") && i + 1 < lex.tokens.size()) {
                Token next = lex.tokens.get(i + 1);
                if (next.text.matches("[a-z]"))
                    symbolTable.addGlobal(next.text, "Identifier");
                i++; // skip next token
            } else if (t.text.equals("local") && i + 1 < lex.tokens.size()) {
                Token next = lex.tokens.get(i + 1);
                if (next.text.matches("[a-z]"))
                    symbolTable.addLocal(next.text, "Identifier");
                i++;
            } else if (t.text.matches("[a-z]")) {
                // If not explicitly declared global/local, default to local.
                symbolTable.addLocal(t.text, "Identifier");
            }
        }
        symbolTable.printTable(writer);

        // Error handling (for demo, we simply show that no errors were detected)
        ErrorHandler errorHandler = new ErrorHandler();
        errorHandler.printErrors(writer);

        // ---------- Phase 2: Regex Processing (RE -> NFA -> DFA) ----------
        dualPrint("\n--- Regex Processing Phase ---", writer);
        dualPrint("Using regular expression: " + regex, writer);

        // Convert the regex to an NFA using Thompson's algorithm.
        RegexToNFA.NFAFragment nfa = RegexToNFA.convert(regex);
        dualPrint("\n[NFA Conversion]", writer);
        dualPrint("Total NFA States: " + RegexToNFA.getTotalStateCount(), writer);
        dualPrint("Unique NFA States: " + RegexToNFA.getUniqueStateIds(), writer);
        dualPrint("NFA Transition List:", writer);
        RegexToNFA.printNFATransitions(nfa.start, writer);

        // Convert the NFA to a DFA.
        DFA dfa = new DFA(nfa);
        dualPrint("\n[DFA Conversion]", writer);
        dualPrint("Total DFA States: " + dfa.getTotalStates(), writer);
        dualPrint("Unique DFA States (as sets of NFA state IDs):", writer);
        dfa.printUniqueStates(writer);
        dualPrint("\nDFA Transition State Table:", writer);
        dfa.printTransitionTable(writer);

        writer.close();
        scanner.close();
        System.out.println("Processing complete. Output written to output.txt");
    }

    // Helper method to print a message to both console and file.
    static void dualPrint(String s, PrintWriter writer) {
        System.out.println(s);
        writer.println(s);
    }

    // Helper method to read file contents into a String.
    static String readFile(String filePath) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException ioe) {
            System.out.println("Error reading file " + filePath + ": " + ioe.getMessage());
            return null;
        }
        return sb.toString();
    }

    /* ======================
       LEXICAL ANALYZER
       ====================== */
    static class LexicalAnalyzer {
        String source;
        List<Token> tokens = new ArrayList<>();
        int tokenCount = 0;
        String preprocessedSource;

        // Token pattern supports multi-line and single-line comments,
        // strings, numbers, booleans, the keywords "global" and "local",
        // single-letter identifiers (only a-z), and arithmetic/punctuation.
        private static final Pattern tokenPattern = Pattern.compile(
                "(?s)/\\*.*?\\*/" +                      // multiline comments
                        "|//.*" +                                // single-line comments
                        "|\"(\\\\.|[^\"\\\\])*\"" +              // strings in double quotes
                        "|\\b(global|local)\\b" +                // global and local keywords
                        "|\\b(true|false)\\b" +                  // booleans
                        "|\\d+(\\.\\d+)?" +                      // numbers (integer/decimal)
                        "|\\b[a-z]\\b" +                         // identifiers: single lowercase letter
                        "|[+\\-*/%^()=;]"                        // operators and punctuation
        );

        LexicalAnalyzer(String source) {
            this.source = source;
            // Pre-process: remove extra spaces/newlines.
            this.preprocessedSource = source.replaceAll("\\s+", " ").trim();
        }

        void process() {
            Matcher matcher = tokenPattern.matcher(source);
            while (matcher.find()) {
                String tokenText = matcher.group().trim();
                if (tokenText.isEmpty()) continue;
                // Ignore comments.
                if (tokenText.startsWith("//") || tokenText.startsWith("/*")) continue;
                Token token = new Token(tokenText);
                tokens.add(token);
                tokenCount++;
            }
        }

        int getTokenCount() {
            return tokenCount;
        }

        String getPreprocessedSource() {
            return preprocessedSource;
        }

        void printTokens(PrintWriter writer) {
            writer.println("Tokens:");
            for (Token t : tokens) {
                writer.println("  " + t);
            }
        }
    }

    static class Token {
        String text;
        Token(String text) {
            this.text = text;
        }
        public String toString() {
            return text;
        }
    }

    /* ======================
       SYMBOL TABLE (with Global and Local Support)
       ====================== */
    static class SymbolTable {
        Map<String, String> globalVariables = new HashMap<>();
        Map<String, String> localVariables = new HashMap<>();

        void addGlobal(String name, String type) {
            globalVariables.put(name, type);
        }

        void addLocal(String name, String type) {
            localVariables.put(name, type);
        }

        void printTable(PrintWriter writer) {
            writer.println("\nSymbol Table:");
            writer.println("Global Variables:");
            if (globalVariables.isEmpty()) {
                writer.println("  None");
            } else {
                for (String name : globalVariables.keySet()) {
                    writer.println("  " + name + " : " + globalVariables.get(name));
                }
            }
            writer.println("Local Variables:");
            if (localVariables.isEmpty()) {
                writer.println("  None");
            } else {
                for (String name : localVariables.keySet()) {
                    writer.println("  " + name + " : " + localVariables.get(name));
                }
            }
        }
    }

    /* ======================
       ERROR HANDLER
       ====================== */
    static class ErrorHandler {
        List<String> errors = new ArrayList<>();

        void addError(int line, String message) {
            errors.add("Line " + line + ": " + message);
        }

        void printErrors(PrintWriter writer) {
            if (errors.isEmpty()) {
                writer.println("\nNo errors detected.");
            } else {
                writer.println("\nErrors:");
                for (String err : errors) {
                    writer.println("  " + err);
                }
            }
        }
    }

    /* ======================
       REGULAR EXPRESSION TO NFA CONVERSION (Thompson's Algorithm)
       ====================== */
    static class RegexToNFA {
        private static int stateIdCounter = 0;
        static Set<State> allStates = new HashSet<>();

        static class State {
            int id;
            char transition; // literal or '\0' for epsilon
            State out1, out2;

            State(char transition, State out1, State out2) {
                this.id = stateIdCounter++;
                this.transition = transition;
                this.out1 = out1;
                this.out2 = out2;
                allStates.add(this);
            }
        }

        static class NFAFragment {
            State start;
            State accept;
            NFAFragment(State start, State accept) {
                this.start = start;
                this.accept = accept;
            }
        }

        private static void reset() {
            stateIdCounter = 0;
            allStates.clear();
        }

        public static NFAFragment convert(String regex) {
            reset();
            String postfix = infixToPostfix(regex);
            NFAFragment nfa = postfixToNFA(postfix);
            return nfa;
        }

        public static int getTotalStateCount() {
            return allStates.size();
        }

        public static Set<Integer> getUniqueStateIds() {
            Set<Integer> ids = new HashSet<>();
            for (State s : allStates) {
                ids.add(s.id);
            }
            return ids;
        }

        // Convert infix regex to postfix (inserting explicit concatenation '.')
        private static String infixToPostfix(String regex) {
            StringBuilder output = new StringBuilder();
            Stack<Character> stack = new Stack<>();
            StringBuilder regexWithConcat = new StringBuilder();
            for (int i = 0; i < regex.length(); i++) {
                char c = regex.charAt(i);
                if (i > 0) {
                    char prev = regex.charAt(i - 1);
                    if ((isLiteral(prev) || prev == '*' || prev == ')') &&
                            (isLiteral(c) || c == '(')) {
                        regexWithConcat.append('.');
                    }
                }
                regexWithConcat.append(c);
            }
            String modified = regexWithConcat.toString();
            for (int i = 0; i < modified.length(); i++) {
                char c = modified.charAt(i);
                switch (c) {
                    case '(':
                        stack.push(c);
                        break;
                    case ')':
                        while (!stack.isEmpty() && stack.peek() != '(') {
                            output.append(stack.pop());
                        }
                        if (!stack.isEmpty())
                            stack.pop();
                        break;
                    case '*':
                        output.append(c);
                        break;
                    case '.':
                    case '|':
                        while (!stack.isEmpty() && precedence(stack.peek()) >= precedence(c)) {
                            output.append(stack.pop());
                        }
                        stack.push(c);
                        break;
                    default:
                        output.append(c);
                        break;
                }
            }
            while (!stack.isEmpty()) {
                output.append(stack.pop());
            }
            return output.toString();
        }

        private static boolean isLiteral(char c) {
            return Character.isLetterOrDigit(c);
        }

        private static int precedence(char c) {
            switch (c) {
                case '*': return 3;
                case '.': return 2;
                case '|': return 1;
                default: return 0;
            }
        }

        // Convert postfix regex to NFA via Thompson's construction.
        private static NFAFragment postfixToNFA(String postfix) {
            Stack<NFAFragment> stack = new Stack<>();
            for (int i = 0; i < postfix.length(); i++) {
                char c = postfix.charAt(i);
                switch (c) {
                    case '*':
                        NFAFragment frag = stack.pop();
                        stack.push(kleeneStar(frag));
                        break;
                    case '.': {
                        NFAFragment frag2 = stack.pop();
                        NFAFragment frag1 = stack.pop();
                        stack.push(concatenate(frag1, frag2));
                        break;
                    }
                    case '|': {
                        NFAFragment frag2 = stack.pop();
                        NFAFragment frag1 = stack.pop();
                        stack.push(union(frag1, frag2));
                        break;
                    }
                    default:
                        stack.push(operandFragment(c));
                        break;
                }
            }
            return stack.pop();
        }

        private static NFAFragment operandFragment(char c) {
            State accept = new State('\0', null, null);
            State start = new State(c, accept, null);
            return new NFAFragment(start, accept);
        }

        private static NFAFragment concatenate(NFAFragment frag1, NFAFragment frag2) {
            frag1.accept.transition = '\0'; // epsilon
            frag1.accept.out1 = frag2.start;
            return new NFAFragment(frag1.start, frag2.accept);
        }

        private static NFAFragment union(NFAFragment frag1, NFAFragment frag2) {
            State start = new State('\0', frag1.start, frag2.start);
            State accept = new State('\0', null, null);
            frag1.accept.transition = '\0';
            frag1.accept.out1 = accept;
            frag2.accept.transition = '\0';
            frag2.accept.out1 = accept;
            return new NFAFragment(start, accept);
        }

        private static NFAFragment kleeneStar(NFAFragment frag) {
            State start = new State('\0', frag.start, null);
            State accept = new State('\0', null, null);
            frag.accept.transition = '\0';
            frag.accept.out1 = frag.start;
            frag.accept.out2 = accept;
            start.out2 = accept;
            return new NFAFragment(start, accept);
        }

        // Print NFA transitions via DFS.
        public static void printNFATransitions(State start, PrintWriter writer) {
            Set<Integer> visited = new HashSet<>();
            Stack<State> stack = new Stack<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                State s = stack.pop();
                if (visited.contains(s.id)) continue;
                visited.add(s.id);
                String label = (s.transition == '\0') ? "ε" : String.valueOf(s.transition);
                writer.println("State " + s.id + " [" + label + "]");
                if (s.out1 != null) {
                    writer.println("  -> State " + s.out1.id);
                    stack.push(s.out1);
                }
                if (s.out2 != null) {
                    writer.println("  -> State " + s.out2.id);
                    stack.push(s.out2);
                }
            }
        }
    }

    /* ======================
       DFA CONVERSION (Subset Construction)
       ====================== */
    static class DFA {
        class DFAState {
            Set<RegexToNFA.State> nfaStates;
            int id;
            Map<Character, DFAState> transitions = new HashMap<>();

            DFAState(Set<RegexToNFA.State> nfaStates, int id) {
                this.nfaStates = nfaStates;
                this.id = id;
            }

            public String toString() {
                List<Integer> ids = new ArrayList<>();
                for (RegexToNFA.State s : nfaStates)
                    ids.add(s.id);
                Collections.sort(ids);
                return "DFAState " + id + " " + ids;
            }
        }

        List<DFAState> dfaStates = new ArrayList<>();
        DFAState startState;
        int dfaStateIdCounter = 0;
        Set<Character> inputSymbols = new HashSet<>();

        DFA(RegexToNFA.NFAFragment nfaFrag) {
            for (RegexToNFA.State s : RegexToNFA.allStates) {
                if (s.transition != '\0')
                    inputSymbols.add(s.transition);
            }
            startState = newDFAState(epsilonClosure(Collections.singleton(nfaFrag.start)));
            Queue<DFAState> queue = new LinkedList<>();
            queue.add(startState);
            while (!queue.isEmpty()) {
                DFAState current = queue.poll();
                for (char symbol : inputSymbols) {
                    Set<RegexToNFA.State> moveResult = move(current.nfaStates, symbol);
                    if (moveResult.isEmpty()) continue;
                    Set<RegexToNFA.State> closure = epsilonClosure(moveResult);
                    DFAState target = findStateByNFASet(closure);
                    if (target == null) {
                        target = newDFAState(closure);
                        queue.add(target);
                    }
                    current.transitions.put(symbol, target);
                }
            }
        }

        int getTotalStates() {
            return dfaStates.size();
        }

        void printUniqueStates(PrintWriter writer) {
            for (DFAState s : dfaStates) {
                writer.println(s);
            }
        }

        void printTransitionTable(PrintWriter writer) {
            writer.printf("%-10s %-15s %-15s\n", "State", "Input", "Next State");
            for (DFAState s : dfaStates) {
                for (Map.Entry<Character, DFAState> entry : s.transitions.entrySet()) {
                    writer.printf("%-10s %-15s %-15s\n", "D" + s.id, entry.getKey(), "D" + entry.getValue().id);
                }
            }
        }

        private DFAState newDFAState(Set<RegexToNFA.State> nfaSet) {
            DFAState dfaState = new DFAState(nfaSet, dfaStateIdCounter++);
            dfaStates.add(dfaState);
            return dfaState;
        }

        private DFAState findStateByNFASet(Set<RegexToNFA.State> nfaSet) {
            for (DFAState s : dfaStates) {
                if (s.nfaStates.equals(nfaSet))
                    return s;
            }
            return null;
        }

        private Set<RegexToNFA.State> epsilonClosure(Set<RegexToNFA.State> states) {
            Set<RegexToNFA.State> closure = new HashSet<>(states);
            Stack<RegexToNFA.State> stack = new Stack<>();
            stack.addAll(states);
            while (!stack.isEmpty()) {
                RegexToNFA.State s = stack.pop();
                if (s.transition == '\0') {
                    if (s.out1 != null && !closure.contains(s.out1)) {
                        closure.add(s.out1);
                        stack.push(s.out1);
                    }
                    if (s.out2 != null && !closure.contains(s.out2)) {
                        closure.add(s.out2);
                        stack.push(s.out2);
                    }
                }
            }
            return closure;
        }

        private Set<RegexToNFA.State> move(Set<RegexToNFA.State> states, char symbol) {
            Set<RegexToNFA.State> result = new HashSet<>();
            for (RegexToNFA.State s : states) {
                if (s.transition == symbol && s.out1 != null)
                    result.add(s.out1);
            }
            return result;
        }
    }
}
