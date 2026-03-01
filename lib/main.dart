import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:sensors_plus/sensors_plus.dart';
import 'package:torch_light/torch_light.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ShakeLightApp());
}

class ShakeLightApp extends StatelessWidget {
  const ShakeLightApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'ShakeLight',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.amber),
        useMaterial3: true,
      ),
      home: const ShakeLightHomePage(),
    );
  }
}

class ShakeLightHomePage extends StatefulWidget {
  const ShakeLightHomePage({super.key});

  @override
  State<ShakeLightHomePage> createState() => _ShakeLightHomePageState();
}

class _ShakeLightHomePageState extends State<ShakeLightHomePage> {
  static const double _shakeThresholdRadPerSec = 6.5;
  static const Duration _toggleCooldown = Duration(milliseconds: 900);

  StreamSubscription<GyroscopeEvent>? _gyroSubscription;
  bool _flashOn = false;
  bool _torchSupported = true;
  String _statusText = 'Shake your phone to toggle the flashlight';
  DateTime _lastToggle = DateTime.fromMillisecondsSinceEpoch(0);

  @override
  void initState() {
    super.initState();
    _initializeTorchAndSensors();
  }

  Future<void> _initializeTorchAndSensors() async {
    final supported = await _isTorchSupported();
    if (!mounted) {
      return;
    }

    setState(() {
      _torchSupported = supported;
      _statusText = supported
          ? 'Ready. Shake your phone to turn flashlight on/off.'
          : 'Torch not available on this device.';
    });

    if (supported) {
      _listenToGyroscope();
    }
  }

  Future<bool> _isTorchSupported() async {
    try {
      return await TorchLight.isTorchAvailable();
    } on Exception {
      return false;
    }
  }

  void _listenToGyroscope() {
    _gyroSubscription?.cancel();
    _gyroSubscription = gyroscopeEventStream().listen((event) {
      final rotationMagnitude = sqrt(
        (event.x * event.x) + (event.y * event.y) + (event.z * event.z),
      );

      final now = DateTime.now();
      final canToggle = now.difference(_lastToggle) >= _toggleCooldown;

      if (rotationMagnitude >= _shakeThresholdRadPerSec && canToggle) {
        _lastToggle = now;
        _toggleFlashlight();
      }
    });
  }

  Future<void> _toggleFlashlight() async {
    try {
      if (_flashOn) {
        await TorchLight.disableTorch();
      } else {
        await TorchLight.enableTorch();
      }

      if (!mounted) {
        return;
      }

      setState(() {
        _flashOn = !_flashOn;
        _statusText = _flashOn
            ? 'Flashlight is ON. Shake again to turn it OFF.'
            : 'Flashlight is OFF. Shake again to turn it ON.';
      });
    } on Exception {
      if (!mounted) {
        return;
      }

      setState(() {
        _statusText =
            'Could not change flashlight state. Check permissions/device support.';
      });
    }
  }

  @override
  void dispose() {
    _gyroSubscription?.cancel();
    if (_flashOn) {
      TorchLight.disableTorch();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ShakeLight')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                _flashOn ? Icons.flash_on : Icons.flash_off,
                size: 120,
                color: _flashOn ? Colors.amber.shade700 : Colors.grey,
              ),
              const SizedBox(height: 20),
              Text(
                _statusText,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 24),
              const Text(
                'Detection uses gyroscope magnitude with a short cooldown\n'
                'to avoid accidental rapid toggles.',
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
