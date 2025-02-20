package lexical_analyzer;

import java.io.PrintWriter;
import java.util.*;

public class DFA {
    public static class DFAState {
        public int id;
        public Map<Character, DFAState> transitions = new HashMap<>();
        public boolean isAccepting;
        public int tokenType = -1; // valid if isAccepting is true.
        public Set<ThompsonConstructor.NFAState> nfaStates;

        public DFAState(int id, Set<ThompsonConstructor.NFAState> nfaStates) {
            this.id = id;
            this.nfaStates = nfaStates;
        }
    }

    private DFAState start;
    private List<DFAState> states = new ArrayList<>();

    // Build the DFA from the master NFA.
    public DFA(ThompsonConstructor.NFA masterNFA) {
        start = buildDFA(masterNFA);
    }

    // Private constructor used for the minimized DFA.
    private DFA(DFAState start, List<DFAState> states) {
        this.start = start;
        this.states = states;
    }

    private DFAState buildDFA(ThompsonConstructor.NFA masterNFA) {
        Set<ThompsonConstructor.NFAState> startSet = epsilonClosure(Collections.singleton(masterNFA.start));
        DFAState startDFA = new DFAState(0, startSet);
        setAccepting(startDFA);
        states.add(startDFA);

        Queue<DFAState> queue = new LinkedList<>();
        queue.add(startDFA);
        int dfaIdCounter = 1;
        Map<Set<Integer>, DFAState> dfaStatesMap = new HashMap<>();
        dfaStatesMap.put(getStateIdSet(startSet), startDFA);

        while (!queue.isEmpty()) {
            DFAState current = queue.poll();
            Set<Character> inputSymbols = new HashSet<>();
            for (ThompsonConstructor.NFAState nfaState : current.nfaStates) {
                for (Character c : nfaState.transitions.keySet()) {
                    if (c != null) {
                        inputSymbols.add(c);
                    }
                }
            }
            for (Character symbol : inputSymbols) {
                Set<ThompsonConstructor.NFAState> moveSet = new HashSet<>();
                for (ThompsonConstructor.NFAState nfaState : current.nfaStates) {
                    List<ThompsonConstructor.NFAState> targets = nfaState.transitions.get(symbol);
                    if (targets != null) {
                        moveSet.addAll(targets);
                    }
                }
                Set<ThompsonConstructor.NFAState> targetSet = epsilonClosure(moveSet);
                if (targetSet.isEmpty()) continue;
                Set<Integer> key = getStateIdSet(targetSet);
                DFAState targetDFA = dfaStatesMap.get(key);
                if (targetDFA == null) {
                    targetDFA = new DFAState(dfaIdCounter++, targetSet);
                    setAccepting(targetDFA);
                    dfaStatesMap.put(key, targetDFA);
                    states.add(targetDFA);
                    queue.add(targetDFA);
                }
                current.transitions.put(symbol, targetDFA);
            }
        }
        return startDFA;
    }

    private Set<Integer> getStateIdSet(Set<ThompsonConstructor.NFAState> nfaStates) {
        Set<Integer> ids = new TreeSet<>();
        for (ThompsonConstructor.NFAState s : nfaStates) {
            ids.add(s.id);
        }
        return ids;
    }

    private Set<ThompsonConstructor.NFAState> epsilonClosure(Set<ThompsonConstructor.NFAState> states) {
        Stack<ThompsonConstructor.NFAState> stack = new Stack<>();
        Set<ThompsonConstructor.NFAState> closure = new HashSet<>(states);
        for (ThompsonConstructor.NFAState s : states) {
            stack.push(s);
        }
        while (!stack.isEmpty()) {
            ThompsonConstructor.NFAState s = stack.pop();
            List<ThompsonConstructor.NFAState> epsTransitions = s.transitions.get(null);
            if (epsTransitions != null) {
                for (ThompsonConstructor.NFAState t : epsTransitions) {
                    if (!closure.contains(t)) {
                        closure.add(t);
                        stack.push(t);
                    }
                }
            }
        }
        return closure;
    }

    private void setAccepting(DFAState dfaState) {
        int bestTokenType = Integer.MAX_VALUE;
        boolean accepting = false;
        for (ThompsonConstructor.NFAState s : dfaState.nfaStates) {
            if (s.tokenType != -1) {
                accepting = true;
                if (s.tokenType < bestTokenType) {
                    bestTokenType = s.tokenType;
                }
            }
        }
        dfaState.isAccepting = accepting;
        if (accepting) {
            dfaState.tokenType = bestTokenType;
        }
    }

    // Simulate the DFA on the input starting from position pos.
    // Returns an array: { tokenType, length of matched lexeme }.
    // If no match, returns { -1, 0 }.
    public int[] simulate(String input, int pos) {
        DFAState current = start;
        int lastAcceptPos = -1;
        int lastTokenType = -1;
        int i = pos;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!current.transitions.containsKey(c)) {
                break;
            }
            current = current.transitions.get(c);
            i++;
            if (current.isAccepting) {
                lastAcceptPos = i;
                lastTokenType = current.tokenType;
            }
        }
        if (lastAcceptPos == -1) {
            return new int[]{-1, 0};
        } else {
            return new int[]{lastTokenType, lastAcceptPos - pos};
        }
    }

    // Print DFA states and transitions.
    public void printDFA(PrintWriter out) {
        for (DFAState state : states) {
            out.print("DFA State " + state.id + (state.isAccepting ? " [Accepting, tokenType=" + state.tokenType + "]" : "") + ": ");
            for (Map.Entry<Character, DFAState> entry : state.transitions.entrySet()) {
                out.print("[" + entry.getKey() + " -> " + entry.getValue().id + "] ");
            }
            out.println();
        }
        out.println("Total DFA states: " + states.size());
    }

    // Print a formatted transition table for the DFA.
    public void printTransitionTable(PrintWriter out) {
        Set<Character> alphabet = new TreeSet<>();
        for (DFAState state : states) {
            alphabet.addAll(state.transitions.keySet());
        }
        out.print(String.format("%-10s", "State"));
        for (Character sym : alphabet) {
            out.print(String.format("%-10s", sym));
        }
        out.println();
        for (DFAState state : states) {
            out.print(String.format("%-10s", state.id));
            for (Character sym : alphabet) {
                DFAState dest = state.transitions.get(sym);
                String cell = (dest != null) ? String.valueOf(dest.id) : "-";
                out.print(String.format("%-10s", cell));
            }
            out.println();
        }
    }

    // DFA minimization using partition refinement.
    public DFA minimize() {
        List<Set<DFAState>> P = new ArrayList<>();
        Map<String, Set<DFAState>> groups = new HashMap<>();
        for (DFAState state : states) {
            String key = state.isAccepting ? ("A" + state.tokenType) : "NA";
            groups.computeIfAbsent(key, k -> new HashSet<>()).add(state);
        }
        P.addAll(groups.values());

        Queue<Set<DFAState>> W = new LinkedList<>(P);
        Set<Character> alphabet = new HashSet<>();
        for (DFAState state : states) {
            alphabet.addAll(state.transitions.keySet());
        }

        while (!W.isEmpty()) {
            Set<DFAState> A = W.poll();
            for (Character c : alphabet) {
                Set<DFAState> X = new HashSet<>();
                for (DFAState s : states) {
                    DFAState target = s.transitions.get(c);
                    if (target != null && A.contains(target)) {
                        X.add(s);
                    }
                }
                List<Set<DFAState>> newPartitions = new ArrayList<>();
                Iterator<Set<DFAState>> it = P.iterator();
                while (it.hasNext()) {
                    Set<DFAState> Y = it.next();
                    Set<DFAState> intersection = new HashSet<>(Y);
                    intersection.retainAll(X);
                    if (!intersection.isEmpty() && intersection.size() < Y.size()) {
                        Set<DFAState> difference = new HashSet<>(Y);
                        difference.removeAll(X);
                        newPartitions.add(intersection);
                        newPartitions.add(difference);
                        it.remove();
                        if (W.contains(Y)) {
                            W.remove(Y);
                            W.add(intersection);
                            W.add(difference);
                        } else {
                            if (intersection.size() <= difference.size()) {
                                W.add(intersection);
                            } else {
                                W.add(difference);
                            }
                        }
                    }
                }
                P.addAll(newPartitions);
            }
        }

        Map<DFAState, DFAState> stateMapping = new HashMap<>();
        List<DFAState> newStates = new ArrayList<>();
        int newId = 0;
        for (Set<DFAState> group : P) {
            DFAState rep = group.iterator().next();
            DFAState newState = new DFAState(newId++, rep.nfaStates);
            newState.isAccepting = rep.isAccepting;
            newState.tokenType = rep.tokenType;
            newStates.add(newState);
            for (DFAState s : group) {
                stateMapping.put(s, newState);
            }
        }
        for (Set<DFAState> group : P) {
            DFAState rep = group.iterator().next();
            DFAState newState = stateMapping.get(rep);
            for (Map.Entry<Character, DFAState> entry : rep.transitions.entrySet()) {
                newState.transitions.put(entry.getKey(), stateMapping.get(entry.getValue()));
            }
        }
        DFAState newStart = stateMapping.get(start);
        return new DFA(newStart, newStates);
    }

    // Print the minimized DFA.
    public void printMinimizedDFA(PrintWriter out) {
        out.println("Minimized DFA States and Transitions:");
        for (DFAState state : states) {
            out.print("State " + state.id + (state.isAccepting ? " [Accepting, tokenType=" + state.tokenType + "]" : "") + ": ");
            for (Map.Entry<Character, DFAState> entry : state.transitions.entrySet()) {
                out.print("[" + entry.getKey() + " -> " + entry.getValue().id + "] ");
            }
            out.println();
        }
        out.println("Total minimized DFA states: " + states.size());
    }
}
