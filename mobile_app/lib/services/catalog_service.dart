import 'dart:convert';
import 'package:flutter/services.dart';
import '../models/catalog_models.dart';

class CatalogService {
  static Future<Catalog> loadFromAssets(String assetPath) async {
    final raw = await rootBundle.loadString(assetPath);
    final jsonMap = json.decode(raw) as Map<String, dynamic>;
    return Catalog.fromJson(jsonMap);
  }
}
