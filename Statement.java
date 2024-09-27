package org.project.javateste;

public class Statement {
  private int number;
  private int startLine;
  private String content;

  public Statement(int number, int startLine, String content) {
    this.number = number;
    this.startLine = startLine;
    this.content = content;
  }

  // Getters and setters
  public int getNumber() { return number; }
  public int getStartLine() { return startLine; }
  public String getContent() { return content; }

  @Override
  public String toString() {
    return "---------------\nStatement #" + number + " (starts at line " + startLine + "):\n" + content;
  }
}
