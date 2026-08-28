abstract class ExprNode {}

class NumNode extends ExprNode {
  final double value;
  NumNode(this.value);
}

class VarNode extends ExprNode {
  final String name;
  VarNode(this.name);
}

class UnaryNode extends ExprNode {
  final String op; // '!' or '-'
  final ExprNode expr;
  UnaryNode(this.op, this.expr);
}

class BinaryNode extends ExprNode {
  final String op; // + - * / % > < >= <= == != && ||
  final ExprNode left;
  final ExprNode right;
  BinaryNode(this.op, this.left, this.right);
}

class TernaryNode extends ExprNode {
  final ExprNode cond;
  final ExprNode thenExpr;
  final ExprNode elseExpr;
  TernaryNode(this.cond, this.thenExpr, this.elseExpr);
}
