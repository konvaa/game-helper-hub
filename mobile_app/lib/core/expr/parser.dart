import 'ast.dart';
import 'tokenizer.dart';

class Parser {
  final List<Tok> toks;
  int p = 0;

  Parser(this.toks);

  Tok get _cur => toks[p];
  Tok _eat() => toks[p++];

  bool _match(String type, [String? text]) {
    if (_cur.type != type) return false;
    if (text != null && _cur.text != text) return false;
    return true;
  }

  Tok _expect(String type, [String? text]) {
    if (!_match(type, text)) {
      throw FormatException('Expected $type ${text ?? ""} but got $_cur');
    }
    return _eat();
  }

  ExprNode parse() {
    final e = _parseTernary();
    _expect('eof');
    return e;
  }

  ExprNode _parseTernary() {
    final cond = _parseOr();
    if (_match('qmark')) {
      _eat();
      final thenExpr = _parseTernary();
      _expect('colon');
      final elseExpr = _parseTernary();
      return TernaryNode(cond, thenExpr, elseExpr);
    }
    return cond;
  }

  ExprNode _parseOr() {
    var left = _parseAnd();
    while (_match('op', '||')) {
      final op = _eat().text;
      final right = _parseAnd();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseAnd() {
    var left = _parseEquality();
    while (_match('op', '&&')) {
      final op = _eat().text;
      final right = _parseEquality();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseEquality() {
    var left = _parseRel();
    while (_match('op', '==') || _match('op', '!=')) {
      final op = _eat().text;
      final right = _parseRel();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseRel() {
    var left = _parseAdd();
    while (_match('op', '>') ||
        _match('op', '<') ||
        _match('op', '>=') ||
        _match('op', '<=')) {
      final op = _eat().text;
      final right = _parseAdd();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseAdd() {
    var left = _parseMul();
    while (_match('op', '+') || _match('op', '-')) {
      final op = _eat().text;
      final right = _parseMul();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseMul() {
    var left = _parseUnary();
    while (_match('op', '*') || _match('op', '/') || _match('op', '%')) {
      final op = _eat().text;
      final right = _parseUnary();
      left = BinaryNode(op, left, right);
    }
    return left;
  }

  ExprNode _parseUnary() {
    if (_match('op', '!') || _match('op', '-')) {
      final op = _eat().text;
      final expr = _parseUnary();
      return UnaryNode(op, expr);
    }
    return _parsePrimary();
  }

  ExprNode _parsePrimary() {
    if (_match('num')) {
      final t = _eat().text;
      return NumNode(double.parse(t));
    }
    if (_match('ident')) {
      final name = _eat().text;
      return VarNode(name);
    }
    if (_match('lpar')) {
      _eat();
      final e = _parseTernary();
      _expect('rpar');
      return e;
    }
    throw FormatException('Unexpected token $_cur');
  }
}
