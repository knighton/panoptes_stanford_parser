package edu.stanford.nlp.parser.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.process.Tokenizer;

/**
 * Serves requests to the given parser model on the given port.
 * See processRequest for a description of the query formats that are
 * handled.
 */
public class LexicalizedParserServer2 {
  final int port;
  final String model;

  final ServerSocket serverSocket;

  final LexicalizedParser parser;

  //static final Charset utf8Charset = Charset.forName("utf-8");

  boolean stillRunning = true;

  public LexicalizedParserServer2(int port, String model) 
    throws IOException
  {
    this(port, model, LexicalizedParser.loadModel(model));
  }

  public LexicalizedParserServer2(int port, String model, 
                                 LexicalizedParser parser)
    throws IOException
  {
    this.port = port;
    this.serverSocket = new ServerSocket(port);
    this.model = model;
    this.parser = parser;
  }


  /**
   * Runs in a loop, getting requests from new clients until a client
   * tells us to exit.
   */
  public void listen() 
    throws IOException
  {
    while (stillRunning) {
      Socket clientSocket = null;
      try {
        clientSocket = serverSocket.accept();
        processRequest(clientSocket);
      } catch (IOException e) {
        // accidental multiple closes don't seem to have any bad effect
        clientSocket.close();
        System.err.println(e);
        continue;
      }
    }
    serverSocket.close();
  }



  // TODO: handle multiple requests in one connection?  why not?
  /**
   * Possible commands are of the form: <br>
   * quit <br>
   * parse query: returns a String of the parsed query <br>
   * tree query: returns a serialized Tree of the parsed query <br>
   */
  public void processRequest(Socket clientSocket) 
    throws IOException
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "utf-8"));
    String line = reader.readLine();

    if (line == null)
      return;
    line = line.trim();
    String[] pieces = line.split(" ", 2);
    String command = pieces[0];
    String arg = null;
    if (pieces.length > 1) {
      arg = pieces[1];
    }
    if (command.equals("quit")) {
      handleQuit();
    } else if (command.equals("parse")) {
      handleParse(arg, clientSocket.getOutputStream());
    } else if (command.equals("tree")) {
      handleTree(arg, clientSocket.getOutputStream());
    }

    clientSocket.close();
  }

  /**
   * Tells the server to exit.
   */
  public void handleQuit() {
    stillRunning = false;
  }

  /**
   * Returns the result of applying the parser to arg as a serialized tree.
   */
  public void handleTree(String arg, OutputStream outStream) 
    throws IOException
  {
    if (arg == null) {
      return;
    }
    Tree tree = parser.parse(arg);
    System.err.println(tree);
    if (tree != null) {
      ObjectOutputStream oos = new ObjectOutputStream(outStream);
      oos.writeObject(tree);
      oos.flush();
    }    
  }

  /**
   * Returns the result of applying the parser to arg as a string.
   */
  public void handleParse(String arg, OutputStream outStream) 
    throws IOException
  {
    if (arg == null) {
      return;
    }

    TreebankLanguagePack tlp = parser.getOp().langpack();
    Tokenizer<? extends HasWord> toke =
        tlp.getTokenizerFactory().getTokenizer(new StringReader(arg));
    List<? extends HasWord> sentence = toke.tokenize();


    ParserQuery pq = parser.parserQuery();
    if (!pq.parse(sentence)) {
      return;
    }

    String formatString = "wordsAndTags,typedDependencies";
    String optionsString = "includePunctuationDependencies,collapsedDependencies";

    OutputStreamWriter osw = new OutputStreamWriter(outStream, "utf-8");
    PrintWriter pw = new PrintWriter(new BufferedWriter(osw));
    List<ScoredObject<Tree>> trees = pq.getKBestPCFGParses(30);
    for (int i = 0; i < trees.size(); ++i) {
      //parser.getTreePrint().printTree(trees.get(i).object(), pw);
      new TreePrint(formatString, optionsString, tlp).printTree(trees.get(i).object(), pw);
      pw.print(trees.get(i).score());
      pw.print("\n\n\n");
    }
    pw.print("\0\n");
    osw.flush();
    pw.flush();
  }

  static final int DEFAULT_PORT = 4466;

  public static void main(String[] args) 
    throws IOException
  {
    System.setOut(new PrintStream(System.out, true, "utf-8"));
    System.setErr(new PrintStream(System.err, true, "utf-8"));

    int port = DEFAULT_PORT;
    String model = LexicalizedParser.DEFAULT_PARSER_LOC;

    for (int i = 0; i < args.length; i += 2) {
      if (i + 1 >= args.length) {
        System.err.println("Unspecified argument " + args[i]);
        System.exit(2);
      }
      String arg = args[i];
      if (arg.startsWith("--")) {
        arg = arg.substring(2);
      } else if (arg.startsWith("-")) {
        arg = arg.substring(1);
      }
      if (arg.equalsIgnoreCase("model")) {
        model = args[i + 1];
      } else if (arg.equalsIgnoreCase("port")) {
        port = Integer.valueOf(args[i + 1]);
      }
    }

    // String[] ss = {"-makeCopulaHead", "-maxLength", "80", "-outputFormat", "wordsAndTags,typedDependencies,includePunctuation"};
    String[] ss = {};
    List<String> optionArgs = new ArrayList<String>();
    Options op = new Options();
    int i = 0;
    while (i < ss.length) {
      int old_i = i;
      i = op.setOptionOrWarn(ss, i);
      for (int j = old_i; j < i; ++j) {
        optionArgs.add(ss[j]);
      }
    }

    String[] extraArgs = new String[optionArgs.size()];
    extraArgs = optionArgs.toArray(extraArgs);

    LexicalizedParser lp = LexicalizedParser.loadModel(model, op, extraArgs);

    LexicalizedParserServer2 server =
        new LexicalizedParserServer2(port, model, lp);
    System.err.println("Server ready!");
    server.listen();
  }
}
