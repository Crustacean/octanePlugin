package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CriteriaExpression implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Node root;

  private CriteriaExpression(Node root) {
    this.root = root;
  }

  public static CriteriaExpression parse(String expression) {
    Parser parser = new Parser(tokenize(expression));
    CriteriaExpression parsed = new CriteriaExpression(parser.parseExpression());
    parser.expect(TokenType.END);
    return parsed;
  }

  public boolean evaluate(MetricsContext context) {
    return root.evaluate(context);
  }

  private static List<Token> tokenize(String expression) {
    String value = Util.trimToEmpty(expression);
    if (value.isEmpty()) {
      throw new CriteriaException("Criteria expression is required.");
    }

    List<Token> tokens = new ArrayList<>();
    int index = 0;
    while (index < value.length()) {
      char character = value.charAt(index);
      if (Character.isWhitespace(character)) {
        index++;
      } else if (character == '(') {
        tokens.add(new Token(TokenType.LEFT_PAREN, "(", 0.0));
        index++;
      } else if (character == ')') {
        tokens.add(new Token(TokenType.RIGHT_PAREN, ")", 0.0));
        index++;
      } else if (isOperatorStart(character)) {
        int next = index + 1;
        if (next < value.length() && value.charAt(next) == '=') {
          tokens.add(new Token(TokenType.OPERATOR, value.substring(index, next + 1), 0.0));
          index = next + 1;
        } else if (character == '<' || character == '>') {
          tokens.add(new Token(TokenType.OPERATOR, String.valueOf(character), 0.0));
          index++;
        } else {
          throw new CriteriaException("Unexpected operator near: " + value.substring(index));
        }
      } else if (Character.isDigit(character)) {
        int start = index;
        index++;
        while (index < value.length()
            && (Character.isDigit(value.charAt(index)) || value.charAt(index) == '.')) {
          index++;
        }
        String number = value.substring(start, index);
        if (index < value.length() && value.charAt(index) == '%') {
          index++;
        }
        tokens.add(new Token(TokenType.NUMBER, number, parseNumber(number)));
      } else if (isIdentifierStart(character)) {
        int start = index;
        index++;
        while (index < value.length() && isIdentifierPart(value.charAt(index))) {
          index++;
        }
        String word = value.substring(start, index);
        if ("AND".equalsIgnoreCase(word)) {
          tokens.add(new Token(TokenType.AND, word, 0.0));
        } else if ("OR".equalsIgnoreCase(word)) {
          tokens.add(new Token(TokenType.OR, word, 0.0));
        } else {
          tokens.add(new Token(TokenType.IDENTIFIER, word, 0.0));
        }
      } else {
        throw new CriteriaException("Unexpected character in criteria: " + character);
      }
    }
    tokens.add(new Token(TokenType.END, "", 0.0));
    return tokens;
  }

  private static boolean isOperatorStart(char character) {
    return character == '=' || character == '!' || character == '<' || character == '>';
  }

  private static boolean isIdentifierStart(char character) {
    return Character.isLetter(character) || character == '_';
  }

  private static boolean isIdentifierPart(char character) {
    return Character.isLetterOrDigit(character)
        || character == '_'
        || character == '-'
        || character == '.';
  }

  private static double parseNumber(String number) {
    try {
      return Double.parseDouble(number);
    } catch (NumberFormatException e) {
      throw new CriteriaException("Invalid number: " + number);
    }
  }

  private interface Node extends Serializable {
    boolean evaluate(MetricsContext context);
  }

  private static class LogicalNode implements Node {
    private static final long serialVersionUID = 1L;

    private final TokenType operator;
    private final Node left;
    private final Node right;

    LogicalNode(TokenType operator, Node left, Node right) {
      this.operator = operator;
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean evaluate(MetricsContext context) {
      if (operator == TokenType.AND) {
        return left.evaluate(context) && right.evaluate(context);
      }
      return left.evaluate(context) || right.evaluate(context);
    }
  }

  private static class ComparisonNode implements Node {
    private static final long serialVersionUID = 1L;

    private final String metricName;
    private final String operator;
    private final double expectedValue;

    ComparisonNode(String metricName, String operator, double expectedValue) {
      this.metricName = metricName;
      this.operator = operator;
      this.expectedValue = expectedValue;
    }

    @Override
    public boolean evaluate(MetricsContext context) {
      double actualValue = context.value(metricName);
      switch (operator) {
        case "==":
          return nearlyEqual(actualValue, expectedValue);
        case "!=":
          return !nearlyEqual(actualValue, expectedValue);
        case ">":
          return actualValue > expectedValue;
        case ">=":
          return actualValue >= expectedValue || nearlyEqual(actualValue, expectedValue);
        case "<":
          return actualValue < expectedValue;
        case "<=":
          return actualValue <= expectedValue || nearlyEqual(actualValue, expectedValue);
        default:
          throw new CriteriaException("Unsupported operator: " + operator);
      }
    }

    private boolean nearlyEqual(double actualValue, double expectedValue) {
      return Math.abs(actualValue - expectedValue) < 0.000001;
    }
  }

  private static class Parser {
    private final List<Token> tokens;
    private int position;

    Parser(List<Token> tokens) {
      this.tokens = tokens;
    }

    Node parseExpression() {
      Node node = parseAndExpression();
      while (peek().type == TokenType.OR) {
        Token operator = advance();
        node = new LogicalNode(operator.type, node, parseAndExpression());
      }
      return node;
    }

    private Node parseAndExpression() {
      Node node = parseFactor();
      while (peek().type == TokenType.AND) {
        Token operator = advance();
        node = new LogicalNode(operator.type, node, parseFactor());
      }
      return node;
    }

    private Node parseFactor() {
      if (peek().type == TokenType.LEFT_PAREN) {
        advance();
        Node node = parseExpression();
        expect(TokenType.RIGHT_PAREN);
        return node;
      }
      return parseComparison();
    }

    private Node parseComparison() {
      Token first = advance();
      if (first.type == TokenType.NUMBER) {
        Token metric = expect(TokenType.IDENTIFIER);
        return new ComparisonNode(metric.text, ">=", first.number);
      }
      if (first.type != TokenType.IDENTIFIER) {
        throw new CriteriaException("Expected metric or threshold near: " + first.text);
      }

      Token operator = expect(TokenType.OPERATOR);
      Token number = expect(TokenType.NUMBER);
      return new ComparisonNode(first.text, operator.text, number.number);
    }

    private Token expect(TokenType expectedType) {
      Token token = advance();
      if (token.type != expectedType) {
        throw new CriteriaException(
            "Expected " + expectedType.name().toLowerCase() + " near: " + token.text);
      }
      return token;
    }

    private Token advance() {
      Token token = peek();
      position++;
      return token;
    }

    private Token peek() {
      return tokens.get(position);
    }
  }

  private static class Token {
    private final TokenType type;
    private final String text;
    private final double number;

    Token(TokenType type, String text, double number) {
      this.type = type;
      this.text = text;
      this.number = number;
    }
  }

  private enum TokenType {
    IDENTIFIER,
    NUMBER,
    OPERATOR,
    AND,
    OR,
    LEFT_PAREN,
    RIGHT_PAREN,
    END
  }
}
