import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ShakeLightApp());
}

class ShakeLightApp extends StatelessWidget {
  const ShakeLightApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ShakeLight',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.amber),
        useMaterial3: true,
      ),
      home: const ShakeLightHomePage(),
    );
  }
}

class ShakeServiceController {
  static const MethodChannel _channel = MethodChannel('shakelight/service');

  static Future<bool> startService() async {
    final result = await _channel.invokeMethod<bool>('startService');
    return result ?? false;
  }

  static Future<bool> stopService() async {
    final result = await _channel.invokeMethod<bool>('stopService');
    return result ?? false;
  }

  static Future<bool> isRunning() async {
    final result = await _channel.invokeMethod<bool>('isRunning');
    return result ?? false;
  }

  static Future<void> updateSettings({
    required double threshold,
    required int cooldownMs,
    required bool startOnBoot,
  }) {
    return _channel.invokeMethod('updateSettings', {
      'threshold': threshold,
      'cooldownMs': cooldownMs,
      'startOnBoot': startOnBoot,
    });
  }

  static Future<void> requestPermissions() {
    return _channel.invokeMethod('requestPermissions');
  }

  static Future<void> requestIgnoreBatteryOptimizations() {
    return _channel.invokeMethod('requestIgnoreBatteryOptimizations');
  }
}

class ShakeLightHomePage extends StatefulWidget {
  const ShakeLightHomePage({super.key});

  @override
  State<ShakeLightHomePage> createState() => _ShakeLightHomePageState();
}

class _ShakeLightHomePageState extends State<ShakeLightHomePage> {
  bool _enabled = false;
  bool _running = false;
  bool _startOnBoot = false;

  double _threshold = 11.5;
  double _cooldownMs = 1200;

  String _statusText = 'Service is stopped';

  @override
  void initState() {
    super.initState();
    _refreshRunningStatus();
  }

  Future<void> _refreshRunningStatus() async {
    final running = await ShakeServiceController.isRunning();
    if (!mounted) return;

    setState(() {
      _running = running;
      _enabled = running;
      _statusText = running ? 'Running (lock screen supported)' : 'Stopped';
    });
  }

  Future<void> _onMasterToggle(bool value) async {
    setState(() {
      _enabled = value;
    });

    await ShakeServiceController.updateSettings(
      threshold: _threshold,
      cooldownMs: _cooldownMs.round(),
      startOnBoot: _startOnBoot,
    );

    if (value) {
      await ShakeServiceController.startService();
    } else {
      await ShakeServiceController.stopService();
    }

    await _refreshRunningStatus();
  }

  Future<void> _saveSettings() async {
    await ShakeServiceController.updateSettings(
      threshold: _threshold,
      cooldownMs: _cooldownMs.round(),
      startOnBoot: _startOnBoot,
    );
    await _refreshRunningStatus();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ShakeLight Settings'),
        actions: [
          IconButton(
            onPressed: _refreshRunningStatus,
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh service status',
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: SwitchListTile(
              value: _enabled,
              title: const Text('Enable ShakeLight'),
              subtitle: const Text('Runs a foreground service for lock-screen shake detection.'),
              onChanged: _onMasterToggle,
            ),
          ),
          const SizedBox(height: 8),
          ListTile(
            leading: Icon(_running ? Icons.check_circle : Icons.pause_circle),
            title: Text(_running ? 'Running' : 'Stopped'),
            subtitle: Text(_statusText),
          ),
          const Divider(),
          ListTile(
            title: const Text('Sensitivity (threshold)'),
            subtitle: Text(_threshold.toStringAsFixed(1)),
          ),
          Slider(
            min: 6,
            max: 20,
            divisions: 28,
            value: _threshold,
            label: _threshold.toStringAsFixed(1),
            onChanged: (value) => setState(() => _threshold = value),
            onChangeEnd: (_) => _saveSettings(),
          ),
          ListTile(
            title: const Text('Cooldown (milliseconds)'),
            subtitle: Text('${_cooldownMs.round()} ms'),
          ),
          Slider(
            min: 400,
            max: 3000,
            divisions: 26,
            value: _cooldownMs,
            label: '${_cooldownMs.round()} ms',
            onChanged: (value) => setState(() => _cooldownMs = value),
            onChangeEnd: (_) => _saveSettings(),
          ),
          SwitchListTile(
            value: _startOnBoot,
            title: const Text('Start on boot'),
            subtitle: const Text('Automatically start ShakeLight after reboot.'),
            onChanged: (value) async {
              setState(() => _startOnBoot = value);
              await _saveSettings();
            },
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: () async {
              await ShakeServiceController.requestPermissions();
              await _refreshRunningStatus();
            },
            icon: const Icon(Icons.security),
            label: const Text('Request Camera / Notification Permissions'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: ShakeServiceController.requestIgnoreBatteryOptimizations,
            icon: const Icon(Icons.battery_alert),
            label: const Text('Ignore battery optimizations'),
          ),
          const SizedBox(height: 16),
          const Card(
            child: Padding(
              padding: EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Help', style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text('• Keep ShakeLight enabled to detect shake while the screen is off.'),
                  Text('• If detection stops in your pocket, disable battery optimization for this app.'),
                  Text('• Some OEMs kill background services aggressively; whitelist this app in vendor settings.'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
