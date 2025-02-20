package lexical_analyzer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class Main {
    public static void main(String[] args) {
        try {
            Reader reader;
            // If a file is provided on the command line and ends with .rmd, use it;
            // otherwise default to "input.rmd"
            if (args.length > 0 && args[0].endsWith(".rmd")) {
                reader = new FileReader(args[0]);
            } else {
                reader = new FileReader("input.rmd");
            }
            Lexer lexer = new Lexer(reader);

            // Print automata details (NFA, DFA, minimized DFA) to console and output.txt
            //lexer.printAutomata("output.txt");

            Token token;
            while ((token = lexer.yylex()).getType() != Token.EOF) {
                System.out.println(token);
            }
            System.out.println("Token Count: " + lexer.getTokenCount());
            System.out.println("Global Symbol Table: " + lexer.getGlobalSymbolTable());
            System.out.println("Local Symbol Tables: " + lexer.getLocalScopes());

        }  catch (RuntimeException e) {
            System.err.println("Lexical error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);


    } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
