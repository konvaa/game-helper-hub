class FeatureFlags {
  final Map<String, bool> _flags;

  FeatureFlags(this._flags);

  bool isEnabled(String? key) {
    if (key == null || key.isEmpty) return true;
    return _flags[key] ?? false;
  }
}

/// Default flags (zatím gear summons vypnuto)
final defaultFeatureFlags = FeatureFlags({
  'enabledSummonsFromGear': false,
});
