#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <map>
#include <set>
#include <iomanip> 

using namespace std;


/* Function for creating tokens*/
vector<string> split(const string &s, char delimiter) 
{
    vector<string> tokens;
    istringstream iss(s);
    string token;
    while (getline(iss, token, delimiter)) 
    {
        // Find first and last non-whitespace characters.
        size_t start = token.find_first_not_of(" \t");
        size_t end = token.find_last_not_of(" \t");
        if (start != string::npos && end != string::npos)
            tokens.push_back(token.substr(start, end - start + 1));
    }
    return tokens;
}


/* function for returning longest common prefix*/
vector<string> commonPrefix(const vector<string>& a, const vector<string>& b) 
{
    vector<string> prefix;
    size_t i = 0;
    // Continue while tokens are equal and within bounds.
    while (i < a.size() && i < b.size() && a[i] == b[i]) 
    {
        prefix.push_back(a[i]);
        ++i;
    }
    return prefix;
}

/* Performs left factoring on productions for a single non-terminal.
 If multiple productions share a common prefix, the function factors it out by introducing a new non-terminal.
*/
void leftFactor(const string &nonTerminal, vector<string>& prods, map<string, vector<string>>& newProds,
                              int &newNTcount) 
    {
    // If only one production exists, no left factoring is needed.
    if (prods.size() < 2) 
    {
        newProds[nonTerminal] = prods;
        return;
    }
    
    // Tokenize each production by splitting on spaces.
    vector< vector<string> > tokenized;
    for (auto &prod : prods) 
    {
        tokenized.push_back(split(prod, ' '));
    }
    
    // Compute the common prefix among all productions.
    vector<string> common = tokenized[0];
    for (size_t i = 1; i < tokenized.size(); i++) 
    {
        common = commonPrefix(common, tokenized[i]);
        if (common.empty())
            break;
    }
    
    // If there is a non-trivial common prefix, perform factoring.
    if (!common.empty()) 
    {
        // Create a new non-terminal name (e.g., E' etc.).
        string newNT = nonTerminal + "'";
        if(newNTcount > 1)
            newNT += std::to_string(newNTcount);
        newNTcount++;
        
        // Create a new production for the original non-terminal:
        // A -> common newNT
        ostringstream factoredProd;
        for (auto &token : common)
            factoredProd << token << " ";
        factoredProd << newNT;
        newProds[nonTerminal].push_back(factoredProd.str());
        
        // Create productions for the new non-terminal with the suffixes.
        vector<string> newNTProds;
        for (auto &tokens : tokenized) 
        {
            // Remove the common prefix.
            vector<string> suffix(tokens.begin() + common.size(), tokens.end());
            // If no suffix remains, denote epsilon ("ε").
            if (suffix.empty()) 
            {
                newNTProds.push_back("ε");
            } 
            else 
            {
                std::ostringstream oss;
                for (auto &s : suffix)
                    oss << s << " ";
                newNTProds.push_back(oss.str());
            }
        }
        newProds[newNT] = newNTProds;
    } 
    else 
    {
        // No common prefix found; simply copy the original productions.
        newProds[nonTerminal] = prods;
    }
}

// Removes immediate left recursion for a single non-terminal.
// For productions of the form A -> Aα | β, transforms them into:
//    A  -> β A'
//    A' -> α A' | ε
void leftRecursion(const string &nonTerminal, vector<string>& prods, map<string, vector<string>>& newGrammar) 
    {
    vector<string> recursiveProds;    // Productions where left recursion occurs.
    vector<string> nonRecursiveProds; // Productions without left recursion.
    
    // Partition productions into recursive and non-recursive.
    for (auto &prod : prods) 
    {
        vector<string> tokens = split(prod, ' ');
        if (!tokens.empty() && tokens[0] == nonTerminal) 
        {
            // Production of the form A -> A α, remove the leading non-terminal.
            std::ostringstream oss;
            for (size_t i = 1; i < tokens.size(); i++) {
                oss << tokens[i] << " ";
            }
            recursiveProds.push_back(oss.str());
        } else {
            nonRecursiveProds.push_back(prod);
        }
    }
    
    // If no left recursion exists, simply copy the productions.
    if (recursiveProds.empty()) {
        newGrammar[nonTerminal] = prods;
        return;
    }
    
    // Create a new non-terminal (e.g., A').
    string newNT = nonTerminal + "'";
    vector<string> newNonRecursive;
    // Append the new non-terminal to each non-recursive production.
    for (auto &beta : nonRecursiveProds) 
    {
        std::ostringstream oss;
        oss << beta << " " << newNT;
        newNonRecursive.push_back(oss.str());
    }
    newGrammar[nonTerminal] = newNonRecursive;
    
    // For each recursive production, remove the left recursion and add new non-terminal.
    vector<string> newRecursive;
    for (auto &alpha : recursiveProds) 
    {
        ostringstream oss;
        oss << alpha << " " << newNT;
        newRecursive.push_back(oss.str());
    }
    // Add an epsilon production for the new non-terminal.
    newRecursive.push_back("ε");
    newGrammar[newNT] = newRecursive;
}

// Computes FIRST sets for all non-terminals in the grammar.
// Terminals are considered symbols that do not appear as keys in the grammar map.
    map<string, set<string>> firstSet(const map<string, vector<string>>& grammar) 
    {
    
    map<string, set<string>> first;
    // Initialize FIRST set for each non-terminal.
    for (const auto &rule : grammar) 
    {
        first[rule.first] = set<string>();
    }
    
    bool changed = true;
    while (changed) 
    {
        changed = false;
        // For each non-terminal X.
        for (const auto &rule : grammar) 
        {
            const string &X = rule.first;
            // Process each production for X.
            for (const auto &prod : rule.second) 
            {
                vector<string> tokens = split(prod, ' ');
                if (tokens.empty())
                    continue;
                
                bool addEpsilon = true;
                // Process each symbol in the production.
                for (const auto &token : tokens) 
                {
                    // If token is epsilon.
                    if (token == "ε") 
                    {
                        if (first[X].insert("ε").second)
                            changed = true;
                        addEpsilon = false;
                        break;
                    }
                    // If token is terminal (not found as a key in grammar).
                    if (grammar.find(token) == grammar.end()) 
                    {
                        if (first[X].insert(token).second)
                            changed = true;
                        addEpsilon = false;
                        break;
                    }
                    // Token is a non-terminal: add its FIRST set (excluding ε).
                    for (const auto &sym : first[token]) 
                    {
                        if (sym != "ε" && first[X].insert(sym).second)
                            changed = true;
                    }
                    // If token's FIRST set does not include ε, stop.
                    if (first[token].find("ε") == first[token].end()) 
                    {
                        addEpsilon = false;
                        break;
                    }
                }
                // If all symbols can derive ε, add ε to FIRST(X).
                if (addEpsilon) 
                {
                    if (first[X].insert("ε").second)
                        changed = true;
                }
            }
        }
    }
    
    return first;
}

// Computes FOLLOW sets for all non-terminals using the previously computed FIRST sets.
// FOLLOW set contains terminals that can immediately follow a non-terminal in some production.
map<string, set<string>> computeFollowSets(
    const map<string, vector<string>>& grammar, const map<string, set<string>>& first, const string& startSymbol) 
    {
    
    map<string, set<string>> follow;
    // Initialize FOLLOW sets.
    for (const auto &rule : grammar) 
    {
        follow[rule.first] = set<string>();
    }
    // Start symbol always includes the end-of-input marker.
    follow[startSymbol].insert("$");
    
    bool changed = true;
    while (changed) {
        changed = false;
        // For every production A -> α.
        for (const auto &rule : grammar) 
        {
            const string &A = rule.first;
            for (const auto &prod : rule.second) 
            {
                vector<string> tokens = split(prod, ' ');
                // Iterate over the symbols in the production.
                for (size_t i = 0; i < tokens.size(); i++) 
                {
                    const string &B = tokens[i];
                    // Process only if B is a non-terminal.
                    if (grammar.find(B) != grammar.end()) 
                    {
                        size_t j = i + 1;
                        bool addFollowA = false;
                        // Examine the subsequent symbols.
                        while (true) 
                        {
                            if (j < tokens.size()) 
                            {
                                const string &beta = tokens[j];
                                // If beta is a terminal, add it directly to FOLLOW(B).
                                if (grammar.find(beta) == grammar.end()) 
                                {
                                    if (follow[B].insert(beta).second)
                                        changed = true;
                                    break;
                                }
                                // If beta is a non-terminal, add FIRST(beta) excluding ε.
                                for (const auto &sym : first.at(beta)) 
                                {
                                    if (sym != "ε" && follow[B].insert(sym).second)
                                        changed = true;
                                }
                                // If FIRST(beta) does not contain ε, break out.
                                if (first.at(beta).find("ε") == first.at(beta).end()) 
                                {
                                    break;
                                }
                                // Otherwise, beta can derive ε; check next symbol.
                                j++;
                                if (j > tokens.size() - 1)
                                    addFollowA = true;
                            } 
                            else 
                            {
                                // No symbol after B; set flag to add FOLLOW(A) to FOLLOW(B).
                                addFollowA = true;
                                break;
                            }
                        }
                        // If needed, add FOLLOW(A) to FOLLOW(B).
                        if (addFollowA) 
                        {
                            for (const auto &sym : follow[A]) 
                            {
                                if (follow[B].insert(sym).second)
                                    changed = true;
                            }
                        }
                    }
                }
            }
        }
    }
    
    return follow;
}

// Helper function: Computes FIRST set for a sequence of tokens (right-hand side of a production).
set<string> computeFirstOfString(const vector<string>& tokens, const map<string, set<string>>& first, const map<string, vector<string>>& grammar) 
{
    
    set<string> result;
    bool allEpsilon = true;
    for (const auto &token : tokens) 
    {
        // If token is terminal.
        if (grammar.find(token) == grammar.end()) 
        {
            result.insert(token);
            allEpsilon = false;
            break;
        } 
        else 
        {
            // Token is non-terminal: add its FIRST set except ε.
            for (const auto &sym : first.at(token)) 
            {
                if (sym != "ε")
                    result.insert(sym);
            }
            // If token cannot derive ε, stop.
            if (first.at(token).find("ε") == first.at(token).end()) 
            {
                allEpsilon = false;
                break;
            }
        }
    }
    if (allEpsilon)
        result.insert("ε");
    return result;
}

int main() 
{
    // Open the input file containing the grammar.
    ifstream infile("grammar.txt");
    if (!infile) {
        std::cerr << "Error: Unable to open grammar file.\n";
        return 1;
    }
    
    // Read the CFG from the file. Each line is expected to be in the format:
    // NonTerminal -> production1 | production2 | ...
    map<string, vector<string>> grammar;
    string line;
    string startSymbol;
    while (getline(infile, line)) 
    {
        if (line.empty())
            continue;
        // Find the position of the arrow "->".
        size_t arrowPos = line.find("->");
        if (arrowPos == string::npos)
            continue;
        // Extract and trim the non-terminal.
        string nonTerminal = line.substr(0, arrowPos);
        size_t start = nonTerminal.find_first_not_of(" \t");
        size_t end = nonTerminal.find_last_not_of(" \t");
        if(start != string::npos && end != string::npos)
            nonTerminal = nonTerminal.substr(start, end - start + 1);

        if(startSymbol.empty())
        {
            startSymbol = nonTerminal;
        }
            
        // Extract the right-hand side productions.
        string rhs = line.substr(arrowPos + 2);
        vector<string> prods = split(rhs, '|');
        grammar[nonTerminal] = prods;
    }
    infile.close();
    
    // --- Phase 1: Left Factoring ---
    map<string, vector<string>> factoredGrammar;
    int newNTcount = 1;  // Counter to generate unique new non-terminals.
    for (auto &prod : grammar) 
    {
        leftFactor(prod.first, prod.second, factoredGrammar, newNTcount);
    }
    
    // --- Phase 2: Left Recursion Removal ---
    map<string, vector<string>> finalGrammar;
    for (auto &prod : factoredGrammar) 
    {
        leftRecursion(prod.first, prod.second, finalGrammar);
    }
    
    // --- Phase 3: FIRST Set Computation ---
    map<string, set<string>> firstSets = firstSet(finalGrammar);
    
    // --- Phase 4: FOLLOW Set Computation ---
    
    
    map<string, set<string>> followSets = computeFollowSets(finalGrammar, firstSets, startSymbol);
    
    // --- Phase 5: LL(1) Parsing Table Construction ---
    // The parsing table is stored as a mapping from non-terminal to a mapping of terminal to production.
    map<string, map<string, string>> parsingTable;
    for (const auto &rule : finalGrammar) 
    {
        const string &nonTerminal = rule.first;
        // Process each production for the non-terminal.
        for (const auto &prod : rule.second) 
        {
            // Tokenize the production.
            vector<string> tokens = split(prod, ' ');
            // Compute FIRST set for the production (alpha).
            set<string> firstAlpha = computeFirstOfString(tokens, firstSets, finalGrammar);
            // For every terminal in FIRST(alpha) except ε, add the production to the table.
            for (const auto &terminal : firstAlpha) 
            {
                if (terminal != "ε") 
                {
                    parsingTable[nonTerminal][terminal] = prod;
                }
            }
            // If ε is in FIRST(alpha), then add the production for each terminal in FOLLOW(nonTerminal).
            if (firstAlpha.find("ε") != firstAlpha.end()) 
            {
                for (const auto &terminal : followSets[nonTerminal]) 
                {
                    parsingTable[nonTerminal][terminal] = prod;
                }
            }
        }
    }
    
    // --- Output all results to output.txt ---
    std::ofstream outfile("output.txt");
    if (!outfile) {
        std::cerr << "Error: Unable to open output file for writing.\n";
        return 1;
    }
    
    // Output grammar after left factoring.
    outfile << "Grammar after Left Factoring:\n";
    for (auto &nt : factoredGrammar) 
    {
        outfile << nt.first << " -> ";
        for (size_t i = 0; i < nt.second.size(); i++) 
        {
            outfile << nt.second[i];
            if (i != nt.second.size() - 1)
                outfile << " | ";
        }
        outfile << "\n";
    }
    
    // Output grammar after left recursion removal.
    outfile << "\nGrammar after Left Recursion Removal:\n";
    for (auto &nt : finalGrammar) 
    {
        outfile << nt.first << " -> ";
        for (size_t i = 0; i < nt.second.size(); i++) 
        {
            outfile << nt.second[i];
            if (i != nt.second.size() - 1)
                outfile << " | ";
        }
        outfile << "\n";
    }
    
    // Output FIRST sets.
    outfile << "\nFIRST Sets:\n";
    for (auto &entry : firstSets) 
    {
        outfile << "FIRST(" << entry.first << ") = { ";
        bool firstElem = true;
        for (const auto &sym : entry.second) 
        {
            if (!firstElem)
                outfile << ", ";
            outfile << sym;
            firstElem = false;
        }
        outfile << " }\n";
    }
    
    // Output FOLLOW sets.
    outfile << "\nFOLLOW Sets:\n";
    for (auto &entry : followSets) 
    {
        outfile << "FOLLOW(" << entry.first << ") = { ";
        bool firstElem = true;
        for (const auto &sym : entry.second) 
        {
            if (!firstElem)
                outfile << ", ";
            outfile << sym;
            firstElem = false;
        }
        outfile << " }\n";
    }
    
    // Output LL(1) Parsing Table.
    outfile << "\nLL(1) Parsing Table:\n\n";
    // Set column width for table formatting.
    const int colWidth = 20;
    
    // Gather all terminals that appear in the parsing table.
    set<string> terminals;
    for (const auto &row : parsingTable) 
    {
        for (const auto &entry : row.second)
            terminals.insert(entry.first);
    }
    
    // Print header row with fixed width columns.
    outfile << std::setw(colWidth) << "Non-Terminal";
    for (const auto &t : terminals) {
        outfile << std::setw(colWidth) << t;
    }
    outfile << "\n";
    
    // Print separator line.
    outfile << string(colWidth * (terminals.size() + 1), '-') << "\n";
    
    // Print each row of the parsing table.
    for (const auto &row : parsingTable) {
        outfile << std::setw(colWidth) << row.first;
        for (const auto &t : terminals) {
            // Print production if it exists; otherwise, print an empty column.
            if (row.second.find(t) != row.second.end())
                outfile << std::setw(colWidth) << row.second.at(t);
            else
                outfile << std::setw(colWidth) << "";
        }
        outfile << "\n";
    }
    
    outfile.close();
    cout << "Processing complete. Check output.txt for results.\n";
    return 0;
}
