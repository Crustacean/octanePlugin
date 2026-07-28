package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CriteriaExpression implements Serializable {
  private static final long serialVersionUID = 1L;
  static final int MAX_EXPRESSION_LENGTH = 8_192;
  static final int MAX_TOKENS = 1_024;
  static final int MAX_NESTING_DEPTH = 64;

  private final Node root;
  private final Set<String> metricReferences;

  private CriteriaExpression(Node root, Set<String> metricReferences) {
    this.root = root;
    this.metricReferences = Set.copyOf(metricReferences);
  }

  public static CriteriaExpression parse(String expression) {
    List<Token> tokens = tokenize(expression);
    Parser parser = new Parser(tokens);
    CriteriaExpression parsed =
        new CriteriaExpression(parser.parseExpression(), metricReferences(tokens));
    parser.expect(TokenType.END);
    return parsed;
  }

  public boolean evaluate(MetricsContext context) {
    return evaluateDetailed(context).isPassed();
  }

  public CriteriaEvaluation evaluateDetailed(MetricsContext context) {
    return evaluateDetailed(context, Set.of());
  }

  public CriteriaEvaluation evaluateDetailed(
      MetricsContext context, boolean regressionEvaluationEnabled) {
    return evaluateDetailed(
        context, regressionEvaluationEnabled ? Set.of() : Set.of("regressions"));
  }

  public CriteriaEvaluation evaluateDetailed(
      MetricsContext context, Set<String> disabledMetricNamespaces) {
    Set<String> disabledNamespaces = normalizeNamespaces(disabledMetricNamespaces);
    List<CriteriaComparisonEvaluation> comparisons = new ArrayList<>();
    NodeEvaluation evaluation = root.evaluate(context, comparisons, disabledNamespaces);
    return CriteriaEvaluation.available(!evaluation.applicable || evaluation.passed, comparisons);
  }

  public CriteriaEvaluation evaluateAppliedDetailed(
      MetricsContext context, boolean regressionEvaluationEnabled) {
    return evaluateAppliedDetailed(
        context, regressionEvaluationEnabled ? Set.of() : Set.of("regressions"));
  }

  public CriteriaEvaluation evaluateAppliedDetailed(
      MetricsContext context, Set<String> disabledMetricNamespaces) {
    Set<String> disabledNamespaces = normalizeNamespaces(disabledMetricNamespaces);
    Node effectiveRoot = root.prune(disabledNamespaces);
    if (effectiveRoot == null) {
      return CriteriaEvaluation.available(true, List.of());
    }
    String appliedExpression = renderAppliedExpression(effectiveRoot, context);
    return CriteriaExpression.parse(appliedExpression).evaluateDetailed(context);
  }

  public String effectiveExpression(MetricsContext context, boolean regressionEvaluationEnabled) {
    return effectiveExpression(
        context, regressionEvaluationEnabled ? Set.of() : Set.of("regressions"));
  }

  public String effectiveExpression(MetricsContext context, Set<String> disabledMetricNamespaces) {
    Node effectiveRoot = root.prune(normalizeNamespaces(disabledMetricNamespaces));
    if (effectiveRoot == null) {
      return "No applicable criteria.";
    }
    return renderAppliedExpression(effectiveRoot, context);
  }

  private static Set<String> normalizeNamespaces(Set<String> namespaces) {
    if (namespaces == null || namespaces.isEmpty()) {
      return Set.of();
    }
    Set<String> values = new LinkedHashSet<>();
    for (String namespace : namespaces) {
      String normalized = Util.trimToEmpty(namespace).toLowerCase(Locale.ROOT);
      if ("regression".equals(normalized)) {
        normalized = "regressions";
      }
      if (!normalized.isEmpty()) {
        values.add(normalized);
      }
    }
    return Set.copyOf(values);
  }

  private String renderAppliedExpression(Node effectiveRoot, MetricsContext context) {
    List<Node> terms = new ArrayList<>();
    List<TokenType> operators = new ArrayList<>();
    flattenTopLevel(effectiveRoot, terms, operators);

    List<AppliedTerm> appliedTerms = new ArrayList<>();
    for (int index = 0; index < terms.size(); index++) {
      Node node = terms.get(index);
      TokenType connector = index == 0 ? null : operators.get(index - 1);
      AppliedTerm current = AppliedTerm.from(node, connector, context);
      if (!appliedTerms.isEmpty() && appliedTerms.get(appliedTerms.size() - 1).canMerge(current)) {
        appliedTerms.get(appliedTerms.size() - 1).merge(current);
      } else {
        appliedTerms.add(current);
      }
    }

    StringBuilder value = new StringBuilder();
    for (AppliedTerm term : appliedTerms) {
      if (value.length() > 0) {
        value.append(' ').append(term.connector.name()).append(' ');
      }
      value.append(term.grouped ? "(" + term.text + ")" : term.text);
    }
    return value.toString();
  }

  private void flattenTopLevel(Node node, List<Node> terms, List<TokenType> operators) {
    if (node instanceof LogicalNode logicalNode) {
      flattenTopLevel(logicalNode.left, terms, operators);
      operators.add(logicalNode.operator);
      flattenTopLevel(logicalNode.right, terms, operators);
      return;
    }
    terms.add(node);
  }

  private static Set<String> namespaces(Node node) {
    Set<String> values = new LinkedHashSet<>();
    collectNamespaces(node, values);
    return values;
  }

  private static void collectNamespaces(Node node, Set<String> values) {
    if (node instanceof ComparisonNode comparisonNode) {
      values.add(comparisonNode.namespace());
      return;
    }
    if (node instanceof GroupNode groupNode) {
      collectNamespaces(groupNode.child, values);
      return;
    }
    if (node instanceof LogicalNode logicalNode) {
      collectNamespaces(logicalNode.left, values);
      collectNamespaces(logicalNode.right, values);
    }
  }

  public boolean usesMetricNamespace(String namespace) {
    String prefix = Util.trimToEmpty(namespace).toLowerCase(Locale.ROOT) + ".";
    if (metricReferences == null) {
      return false;
    }
    return metricReferences.stream()
        .map(reference -> reference.toLowerCase(Locale.ROOT))
        .anyMatch(reference -> reference.startsWith(prefix));
  }

  private static Set<String> metricReferences(List<Token> tokens) {
    Set<String> references = new LinkedHashSet<>();
    for (Token token : tokens) {
      if (token.type == TokenType.IDENTIFIER) {
        references.add(token.text);
      }
    }
    return references;
  }

  private static List<Token> tokenize(String expression) {
    return new Tokenizer(expression).tokenize();
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

  private static final class Tokenizer {
    private final String value;
    private final List<Token> tokens = new ArrayList<>();
    private int index;

    private Tokenizer(String expression) {
      value = Util.trimToEmpty(expression);
      if (value.isEmpty()) {
        throw new CriteriaException("Criteria expression is required.");
      }
      if (value.length() > MAX_EXPRESSION_LENGTH) {
        throw new CriteriaException(
            "Criteria expression exceeds the " + MAX_EXPRESSION_LENGTH + " character limit.");
      }
    }

    private List<Token> tokenize() {
      while (index < value.length()) {
        scanToken();
      }
      tokens.add(new Token(TokenType.END, "", 0.0));
      return tokens;
    }

    private void scanToken() {
      char character = value.charAt(index);
      if (Character.isWhitespace(character)) {
        index++;
        return;
      }
      if (character == '(' || character == ')') {
        addToken(
            character == '(' ? TokenType.LEFT_PAREN : TokenType.RIGHT_PAREN,
            String.valueOf(character),
            0.0);
        index++;
        return;
      }
      if (isOperatorStart(character)) {
        scanOperator(character);
        return;
      }
      if (Character.isDigit(character)) {
        scanNumber();
        return;
      }
      if (isIdentifierStart(character)) {
        scanIdentifier();
        return;
      }
      throw new CriteriaException("Unexpected character in criteria: " + character);
    }

    private void scanOperator(char character) {
      int next = index + 1;
      if (next < value.length() && value.charAt(next) == '=') {
        addToken(TokenType.OPERATOR, value.substring(index, next + 1), 0.0);
        index = next + 1;
        return;
      }
      if (character == '<' || character == '>') {
        addToken(TokenType.OPERATOR, String.valueOf(character), 0.0);
        index++;
        return;
      }
      throw new CriteriaException("Unexpected operator near: " + value.substring(index));
    }

    private void scanNumber() {
      int start = index++;
      while (index < value.length()
          && (Character.isDigit(value.charAt(index)) || value.charAt(index) == '.')) {
        index++;
      }
      String number = value.substring(start, index);
      boolean percentage = false;
      if (index < value.length() && value.charAt(index) == '%') {
        percentage = true;
        index++;
      }
      addToken(TokenType.NUMBER, percentage ? number + "%" : number, parseNumber(number));
    }

    private void scanIdentifier() {
      int start = index++;
      while (index < value.length() && isIdentifierPart(value.charAt(index))) {
        index++;
      }
      String word = value.substring(start, index);
      TokenType type =
          "AND".equalsIgnoreCase(word)
              ? TokenType.AND
              : "OR".equalsIgnoreCase(word) ? TokenType.OR : TokenType.IDENTIFIER;
      addToken(type, word, 0.0);
    }

    private void addToken(TokenType type, String text, double number) {
      tokens.add(new Token(type, text, number));
      if (tokens.size() > MAX_TOKENS) {
        throw new CriteriaException(
            "Criteria expression exceeds the " + MAX_TOKENS + " token limit.");
      }
    }
  }

  private interface Node extends Serializable {
    NodeEvaluation evaluate(
        MetricsContext context,
        List<CriteriaComparisonEvaluation> comparisonEvaluations,
        Set<String> disabledMetricNamespaces);

    RenderedExpression render(MetricsContext context, Set<String> disabledMetricNamespaces);

    Node prune(Set<String> disabledMetricNamespaces);
  }

  private static final class NodeEvaluation {
    private static final NodeEvaluation SKIPPED = new NodeEvaluation(false, true);

    private final boolean applicable;
    private final boolean passed;

    private NodeEvaluation(boolean applicable, boolean passed) {
      this.applicable = applicable;
      this.passed = passed;
    }

    private static NodeEvaluation applicable(boolean passed) {
      return new NodeEvaluation(true, passed);
    }
  }

  private static final class RenderedExpression {
    private final String text;
    private final int precedence;

    private RenderedExpression(String text, int precedence) {
      this.text = text;
      this.precedence = precedence;
    }
  }

  private static final class AppliedTerm {
    private String text;
    private int precedence;
    private final boolean grouped;
    private boolean filtered;
    private final Set<String> namespaces;
    private final TokenType connector;

    private AppliedTerm(
        String text,
        int precedence,
        boolean grouped,
        boolean filtered,
        Set<String> namespaces,
        TokenType connector) {
      this.text = text;
      this.precedence = precedence;
      this.grouped = grouped;
      this.filtered = filtered;
      this.namespaces = namespaces;
      this.connector = connector;
    }

    private static AppliedTerm from(Node node, TokenType connector, MetricsContext context) {
      boolean grouped = node instanceof GroupNode;
      boolean filtered = grouped && ((GroupNode) node).filtered;
      Node renderedNode = grouped ? ((GroupNode) node).child : node;
      RenderedExpression rendered = renderedNode.render(context, Set.of());
      return new AppliedTerm(
          rendered.text,
          rendered.precedence,
          grouped,
          filtered,
          namespaces(renderedNode),
          connector);
    }

    private boolean canMerge(AppliedTerm next) {
      return grouped
          && next.grouped
          && (filtered || next.filtered)
          && namespaces.size() == 1
          && namespaces.equals(next.namespaces);
    }

    private void merge(AppliedTerm next) {
      int connectorPrecedence = next.connector == TokenType.AND ? 2 : 1;
      String leftText = precedence < connectorPrecedence ? "(" + text + ")" : text;
      String rightText = next.precedence < connectorPrecedence ? "(" + next.text + ")" : next.text;
      text = leftText + " " + next.connector.name() + " " + rightText;
      precedence = connectorPrecedence;
      filtered = true;
    }
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
    public NodeEvaluation evaluate(
        MetricsContext context,
        List<CriteriaComparisonEvaluation> comparisonEvaluations,
        Set<String> disabledMetricNamespaces) {
      NodeEvaluation leftEvaluation =
          left.evaluate(context, comparisonEvaluations, disabledMetricNamespaces);
      NodeEvaluation rightEvaluation =
          right.evaluate(context, comparisonEvaluations, disabledMetricNamespaces);
      if (!leftEvaluation.applicable) {
        return rightEvaluation;
      }
      if (!rightEvaluation.applicable) {
        return leftEvaluation;
      }
      boolean passed =
          operator == TokenType.AND
              ? leftEvaluation.passed && rightEvaluation.passed
              : leftEvaluation.passed || rightEvaluation.passed;
      return NodeEvaluation.applicable(passed);
    }

    @Override
    public RenderedExpression render(MetricsContext context, Set<String> disabledMetricNamespaces) {
      RenderedExpression leftExpression = left.render(context, disabledMetricNamespaces);
      RenderedExpression rightExpression = right.render(context, disabledMetricNamespaces);
      if (leftExpression == null) {
        return rightExpression;
      }
      if (rightExpression == null) {
        return leftExpression;
      }
      int precedence = operator == TokenType.AND ? 2 : 1;
      String leftText = parenthesizeWhenRequired(leftExpression, precedence);
      String rightText = parenthesizeWhenRequired(rightExpression, precedence);
      return new RenderedExpression(leftText + " " + operator.name() + " " + rightText, precedence);
    }

    @Override
    public Node prune(Set<String> disabledMetricNamespaces) {
      Node effectiveLeft = left.prune(disabledMetricNamespaces);
      Node effectiveRight = right.prune(disabledMetricNamespaces);
      if (effectiveLeft == null) {
        return effectiveRight;
      }
      if (effectiveRight == null) {
        return effectiveLeft;
      }
      if (effectiveLeft == left && effectiveRight == right) {
        return this;
      }
      return new LogicalNode(operator, effectiveLeft, effectiveRight);
    }

    private String parenthesizeWhenRequired(RenderedExpression expression, int precedence) {
      return expression.precedence < precedence ? "(" + expression.text + ")" : expression.text;
    }
  }

  private static class GroupNode implements Node {
    private static final long serialVersionUID = 1L;

    private final Node child;
    private final boolean filtered;

    GroupNode(Node child) {
      this(child, false);
    }

    private GroupNode(Node child, boolean filtered) {
      this.child = child;
      this.filtered = filtered;
    }

    @Override
    public NodeEvaluation evaluate(
        MetricsContext context,
        List<CriteriaComparisonEvaluation> comparisonEvaluations,
        Set<String> disabledMetricNamespaces) {
      return child.evaluate(context, comparisonEvaluations, disabledMetricNamespaces);
    }

    @Override
    public RenderedExpression render(MetricsContext context, Set<String> disabledMetricNamespaces) {
      RenderedExpression rendered = child.render(context, disabledMetricNamespaces);
      return rendered == null ? null : new RenderedExpression("(" + rendered.text + ")", 3);
    }

    @Override
    public Node prune(Set<String> disabledMetricNamespaces) {
      Node effectiveChild = child.prune(disabledMetricNamespaces);
      if (effectiveChild == null) {
        return null;
      }
      if (effectiveChild == child) {
        return this;
      }
      return new GroupNode(effectiveChild, true);
    }
  }

  private static class ComparisonNode implements Node {
    private static final long serialVersionUID = 1L;

    private final String metricName;
    private final String operator;
    private final double expectedValue;
    private final String expectedLabel;

    ComparisonNode(String metricName, String operator, double expectedValue, String expectedLabel) {
      this.metricName = metricName;
      this.operator = operator;
      this.expectedValue = expectedValue;
      this.expectedLabel = expectedLabel;
    }

    @Override
    public NodeEvaluation evaluate(
        MetricsContext context,
        List<CriteriaComparisonEvaluation> comparisonEvaluations,
        Set<String> disabledMetricNamespaces) {
      if (disabledMetricNamespaces.contains(namespace())) {
        return NodeEvaluation.SKIPPED;
      }
      double actualValue = context.value(metricName);
      boolean passed = compare(actualValue);
      comparisonEvaluations.add(
          new CriteriaComparisonEvaluation(
              metricName,
              operator,
              expectedValue,
              actualValue,
              context.isPercentageMetric(metricName),
              passed));
      return NodeEvaluation.applicable(passed);
    }

    @Override
    public RenderedExpression render(MetricsContext context, Set<String> disabledMetricNamespaces) {
      if (disabledMetricNamespaces.contains(namespace())) {
        return null;
      }
      String appliedLabel = expectedLabel;
      if (appliedLabel == null) {
        CriteriaComparisonEvaluation comparison =
            new CriteriaComparisonEvaluation(
                metricName,
                operator,
                expectedValue,
                0.0,
                context.isPercentageMetric(metricName),
                true);
        return new RenderedExpression(comparison.getCriterionLabel(), 3);
      }
      return new RenderedExpression(metricName + " " + operator + " " + appliedLabel, 3);
    }

    @Override
    public Node prune(Set<String> disabledMetricNamespaces) {
      return disabledMetricNamespaces.contains(namespace()) ? null : this;
    }

    private String namespace() {
      String normalized = Util.trimToEmpty(metricName).toLowerCase(Locale.ROOT);
      int dot = normalized.indexOf('.');
      if (dot < 0) {
        return "regressions";
      }
      String namespace = normalized.substring(0, dot);
      return "regression".equals(namespace) ? "regressions" : namespace;
    }

    private boolean compare(double actualValue) {
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
    private int nestingDepth;

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
        nestingDepth++;
        if (nestingDepth > MAX_NESTING_DEPTH) {
          throw new CriteriaException(
              "Criteria expression exceeds the " + MAX_NESTING_DEPTH + " level nesting limit.");
        }
        try {
          Node node = parseExpression();
          expect(TokenType.RIGHT_PAREN);
          return new GroupNode(node);
        } finally {
          nestingDepth--;
        }
      }
      return parseComparison();
    }

    private Node parseComparison() {
      Token first = advance();
      if (first.type == TokenType.NUMBER) {
        Token metric = expect(TokenType.IDENTIFIER);
        return new ComparisonNode(metric.text, ">=", first.number, first.text);
      }
      if (first.type != TokenType.IDENTIFIER) {
        throw new CriteriaException("Expected metric or threshold near: " + first.text);
      }

      Token operator = expect(TokenType.OPERATOR);
      Token number = expect(TokenType.NUMBER);
      return new ComparisonNode(first.text, operator.text, number.number, number.text);
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
