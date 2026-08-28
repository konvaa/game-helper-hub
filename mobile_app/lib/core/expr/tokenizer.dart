class Tok {
  final String type; // num, ident, op, qmark, colon, lpar, rpar, eof
  final String text;

  Tok(this.type, this.text);

  @override
  String toString() => 'Tok($type:$text)';
}

class Tokenizer {
  final String src;
  int i = 0;

  Tokenizer(this.src);

  bool get _eof => i >= src.length;

  List<Tok> tokenize() {
    final out = <Tok>[];
    while (!_eof) {
      final c = src[i];

      if (_isWs(c)) {
        i++;
        continue;
      }

      if (_isDigit(c) || (c == '.' && i + 1 < src.length && _isDigit(src[i + 1]))) {
        out.add(_readNumber());
        continue;
      }

      if (_isAlpha(c) || c == '_') {
        out.add(_readIdent());
        continue;
      }

      if (c == '(') {
        out.add(Tok('lpar', c));
        i++;
        continue;
      }
      if (c == ')') {
        out.add(Tok('rpar', c));
        i++;
        continue;
      }
      if (c == '?') {
        out.add(Tok('qmark', c));
        i++;
        continue;
      }
      if (c == ':') {
        out.add(Tok('colon', c));
        i++;
        continue;
      }

      final two = (i + 1 < src.length) ? src.substring(i, i + 2) : '';

      if (two == '>=' || two == '<=' || two == '==' || two == '!=' || two == '&&' || two == '||') {
        out.add(Tok('op', two));
        i += 2;
        continue;
      }

      if ('+-*/%><!'.contains(c)) {
        out.add(Tok('op', c));
        i++;
        continue;
      }

      throw FormatException('Unexpected char "$c" at $i in "$src"');
    }

    out.add(Tok('eof', ''));
    return out;
  }

  Tok _readNumber() {
    final start = i;
    bool seenDot = false;
    while (!_eof) {
      final c = src[i];
      if (_isDigit(c)) {
        i++;
        continue;
      }
      if (c == '.' && !seenDot) {
        seenDot = true;
        i++;
        continue;
      }
      break;
    }
    return Tok('num', src.substring(start, i));
  }

  Tok _readIdent() {
    final start = i;
    while (!_eof) {
      final c = src[i];
      if (_isAlphaNum(c) || c == '_') {
        i++;
        continue;
      }
      break;
    }
    return Tok('ident', src.substring(start, i));
  }

  bool _isWs(String c) => c == ' ' || c == '\t' || c == '\n' || c == '\r';
  bool _isDigit(String c) => c.codeUnitAt(0) >= 48 && c.codeUnitAt(0) <= 57;
  bool _isAlpha(String c) {
    final u = c.codeUnitAt(0);
    return (u >= 65 && u <= 90) || (u >= 97 && u <= 122);
  }

  bool _isAlphaNum(String c) => _isAlpha(c) || _isDigit(c);
}
