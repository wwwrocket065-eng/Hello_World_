import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Diagnostics {
  static final instance = Diagnostics._();
  final lines = Queue<String>();
  final changed = ValueNotifier<int>(0);
  final generationWatch = Stopwatch();
  String stage = 'Ожидание';
  String metrics = '';
  Timer? timer;
  int? previousTicks;
  DateTime? previousTime;
  int samples = 0;
  Diagnostics._();
  void event(String message) {
    lines.add('${DateTime.now().toIso8601String()} $message');
    while(lines.length > 500) { lines.removeFirst(); }
    changed.value++;
  }
  void start() {
    timer ??= Timer.periodic(const Duration(seconds:2), (_) => sample());
    event('Приложение запущено · CPU по умолчанию');
    sample();
  }
  Future<void> sample() async {
    String cpu = 'недоступна';
    try {
      final stat = await File('/proc/self/stat').readAsString();
      final fields = stat.substring(stat.lastIndexOf(')')+2).split(RegExp(r'\s+'));
      final ticks = int.parse(fields[11])+int.parse(fields[12]);
      final now=DateTime.now();
      if(previousTicks != null && previousTime != null) {
        final seconds=now.difference(previousTime!).inMicroseconds/1e6;
        if(seconds>0) cpu='${((ticks-previousTicks!)/100/seconds*100).toStringAsFixed(1)}% (100% = одно ядро; CLK_TCK=100 Android)';
      }
      previousTicks=ticks; previousTime=now;
    } catch(_) {}
    String available = 'недоступно';
    try {
      final mem = await File('/proc/meminfo').readAsString();
      final match = RegExp(r'MemAvailable:\s+(\d+)').firstMatch(mem);
      if(match != null) available='${(int.parse(match.group(1)!)/1024).toStringAsFixed(0)} МиБ';
    } catch(_) {}
    metrics='Доступно системе: $available\nВремя генерации: ${generationWatch.elapsed.inSeconds} с\nRSS процесса: ${(ProcessInfo.currentRss/1048576).toStringAsFixed(1)} МиБ\nПик RSS: ${(ProcessInfo.maxRss/1048576).toStringAsFixed(1)} МиБ\nCPU процесса: $cpu\nЯдер: ${Platform.numberOfProcessors}\nGPU: загрузка недоступна\nТемпература: недоступна';
    samples++;
    if(samples % 5 == 0) event('Телеметрия · $stage · ${metrics.replaceAll("\n", " · ")}');
    changed.value++;
  }
  String report() => 'Local SDXL · 0.1.2\n${DateTime.now().toIso8601String()}\n${Platform.operatingSystem} ${Platform.operatingSystemVersion}\nЭтап: $stage\n$metrics\nCPU: процесс приложения, не общая нагрузка устройства\nЖурнал (последние 500 событий):\n${lines.join("\n")}';
}
Future<void> showDiagnostics(BuildContext context) async {
  final d=Diagnostics.instance;
  await showDialog<void>(context:context, builder:(context)=>AlertDialog(
    title:const Text('Диагностика'),
    content:SizedBox(width:600, child:ValueListenableBuilder<int>(
      valueListenable:d.changed,
      builder:(context,_,__)=>SingleChildScrollView(child:SelectableText(d.report(), style:const TextStyle(fontSize:12))),
    )),
    actions:[
      TextButton(onPressed:() async {
        await Clipboard.setData(ClipboardData(text:d.report()));
        if(context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Диагностика скопирована')));
      }, child:const Text('Скопировать')),
      TextButton(onPressed:()=>Navigator.pop(context),child:const Text('Закрыть')),
    ],
  ));
}
