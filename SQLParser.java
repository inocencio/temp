package org.project.javateste;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLParser {
  private static final Pattern SQL_KEYWORDS = Pattern.compile("^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|MERGE)\\b", Pattern.CASE_INSENSITIVE);

  public static List<Statement> parseFile(String filePath) throws IOException {
    List<Statement> statements = new ArrayList<>();
    StringBuilder stm = new StringBuilder();
    int stmNum = 1;
    int startLine = 0;
    int currentLine = 0;
    boolean inMultiLineComment = false;

    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String line;
      while ((line = reader.readLine()) != null) {
        currentLine++;
        String ln = line;

        // Handle multi-line comments
        if (inMultiLineComment) {
          int endCommentIndex = ln.indexOf("*/");
          if (endCommentIndex != -1) {
            ln = ln.substring(endCommentIndex + 2);
            inMultiLineComment = false;
          } else {
            continue;
          }
        }

        // Remove single-line comments
        ln = ln.replaceAll("--.*$", "");

        // Handle start of multi-line comments
        int startCommentIndex = ln.indexOf("/*");
        if (startCommentIndex != -1) {
          int endCommentIndex = ln.indexOf("*/", startCommentIndex);
          if (endCommentIndex != -1) {
            ln = ln.substring(0, startCommentIndex) + ln.substring(endCommentIndex + 2);
          } else {
            ln = ln.substring(0, startCommentIndex);
            inMultiLineComment = true;
          }
        }

        // Check if the current line contains a SQL keyword
        Matcher matcher = SQL_KEYWORDS.matcher(ln.trim());
        if (matcher.find()) {
          // If we already have a statement, add it to the list
          String statementContent = stm.toString().trim();
          if (!statementContent.isEmpty()) {
            statements.add(new Statement(stmNum++, startLine, statementContent));
            stm = new StringBuilder();
          }
          startLine = currentLine;
        }

        // Append the processed line if it's not empty
        if (!ln.trim().isEmpty()) {
          stm.append(ln).append("\n");
        }
      }
    }

    // Add the last statement
    String lastStatement = stm.toString().trim();
    if (!lastStatement.isEmpty()) {
      statements.add(new Statement(stmNum, startLine, lastStatement));
    }

    return statements;
  }

  public static void main(String[] args) {
    try {
      List<Statement> statements = parseFile("sql_statements.txt");
      for (Statement stmt : statements) {
        System.out.println(stmt);
        System.out.println();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
