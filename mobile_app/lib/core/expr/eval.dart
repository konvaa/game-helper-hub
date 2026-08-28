import 'ast.dart';

class EvalContext {
  final int lvl;
  final Map<String, num> vars; // par1..par8 etc.
  EvalContext({required this.lvl, required this.vars});
}

double evalNode(ExprNode n, EvalContext ctx) {
  if (n is NumNode) return n.value;

  if (n is VarNode) {
    final name = n.name;
    if (name == 'lvl') return ctx.lvl.toDouble();

    final v = ctx.vars[name];
    if (v == null) return 0.0;
    return v.toDouble();
  }

  if (n is UnaryNode) {
    final v = evalNode(n.expr, ctx);
    switch (n.op) {
      case '-':
        return -v;
      case '!':
        return _truth(v) ? 0.0 : 1.0;
      default:
        throw StateError('Unknown unary op ${n.op}');
    }
  }

  if (n is BinaryNode) {
    final a = evalNode(n.left, ctx);
    final b = evalNode(n.right, ctx);

    switch (n.op) {
      case '+':
        return a + b;
      case '-':
        return a - b;
      case '*':
        return a * b;
      case '/':
        return b == 0 ? 0.0 : a / b;
      case '%':
        return b == 0 ? 0.0 : a % b;

      case '>':
        return _truthNum(a > b);
      case '<':
        return _truthNum(a < b);
      case '>=':
        return _truthNum(a >= b);
      case '<=':
        return _truthNum(a <= b);
      case '==':
        return _truthNum(a == b);
      case '!=':
        return _truthNum(a != b);

      case '&&':
        return _truthNum(_truth(a) && _truth(b));
      case '||':
        return _truthNum(_truth(a) || _truth(b));

      default:
        throw StateError('Unknown binary op ${n.op}');
    }
  }

  if (n is TernaryNode) {
    final c = evalNode(n.cond, ctx);
    return _truth(c) ? evalNode(n.thenExpr, ctx) : evalNode(n.elseExpr, ctx);
  }

  throw StateError('Unknown node type: ${n.runtimeType}');
}

bool _truth(double v) => v != 0.0;
double _truthNum(bool b) => b ? 1.0 : 0.0;
