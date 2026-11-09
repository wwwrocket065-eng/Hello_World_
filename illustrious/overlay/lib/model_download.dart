import 'dart:async';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'diagnostics.dart';

const modelName = 'Illustrious-XL-v2.0.safetensors';
const modelHash = 'c2a1a3eaa13d4c107dc7e00c3fe830cab427aa026362740ea094745b3422a331';
Future<Directory> modelsDirectory() async {
  final base = await getApplicationSupportDirectory();
  return Directory('${base.path}/models').create(recursive: true);
}
class ModelDownload extends StatefulWidget {
  final void Function(String) onReady;
  final bool enabled;
  const ModelDownload({super.key, required this.onReady, required this.enabled});
  @override
  State<ModelDownload> createState() => _ModelDownloadState();
}
class _ModelDownloadState extends State<ModelDownload> {
  bool busy = false, paused = false, verified = false;
  int received = 0, total = 0;
  String stage = 'Модель ещё не скачана (около 6,94 ГБ)';
  HttpClient? client;
  final watch = Stopwatch();
  @override
  void initState() { super.initState(); _inspect(); }
  Future<void> _inspect() async {
    try {
      final dir = await modelsDirectory();
      final file = File('${dir.path}/$modelName');
      if (await file.exists()) {
        if (!mounted) return;
        setState(() { busy = true; stage = 'Проверка скачанной модели…'; });
        await _verify(file);
        if(mounted) widget.onReady(file.path);
        if (mounted) setState(() => busy = false);
      } else {
        final part = File('${file.path}.part');
        final n = await part.exists() ? await part.length() : 0;
        if (mounted) setState(() { received = n; if(n > 0) stage = 'Загрузка приостановлена — можно продолжить'; });
      }
    } catch(e) { if(mounted) setState(() { busy=false; stage='Ошибка: $e'; }); }
  }
  Future<void> _verify(File file) async {
    Diagnostics.instance.event('Проверка SHA256 модели');
    final digest = await sha256.bind(file.openRead()).first;
    if (digest.toString() != modelHash) {
      await file.delete();
      throw const FormatException('Контрольная сумма не совпала. Повреждённый файл удалён, скачайте заново.');
    }
    if (!mounted) return;
    setState(() { verified = true; stage = 'Модель проверена и готова'; });
    Diagnostics.instance.event(stage);

  }
  Future<void> download() async {
    if(busy) return;
    setState(() { busy = true; paused = false; stage = 'Подключение…'; });
    watch..reset()..start();
    try {
      final dir = await modelsDirectory();
      final part = File('${dir.path}/$modelName.part');
      int offset = await part.exists() ? await part.length() : 0;
      client = HttpClient()..connectionTimeout = const Duration(seconds:30);
      final request = await client!.getUrl(Uri.parse('https://huggingface.co/OnomaAIResearch/Illustrious-XL-v2.0/resolve/main/$modelName'));
      if(offset > 0) request.headers.set(HttpHeaders.rangeHeader, 'bytes=$offset-');
      final response = await request.close().timeout(const Duration(seconds:60));
      if(response.statusCode != 200 && response.statusCode != 206) {
        throw HttpException('Сервер вернул HTTP ${response.statusCode}');
      }
      if(response.statusCode == 206) {
        final range = response.headers.value(HttpHeaders.contentRangeHeader) ?? '';
        final match = RegExp(r'^bytes (\d+)-(\d+)/(\d+)$').firstMatch(range);
        if(match == null || int.parse(match.group(1)!) != offset) throw const FormatException('Неверный ответ сервера при продолжении загрузки');
        total = int.parse(match.group(3)!);
      } else {
        offset = 0;
        total = response.contentLength > 0 ? response.contentLength : 0;
      }
      received = offset;
      final output = await part.open(mode: offset > 0 ? FileMode.append : FileMode.write);
      var lastUpdate = DateTime.now();
      Diagnostics.instance.event('Скачивание модели: продолжение с $offset байт');
      try {
        await for(final bytes in response.timeout(const Duration(seconds:60))) {
          if(paused) break;
          await output.writeFrom(bytes);
          received += bytes.length;
          final now = DateTime.now();
          if(now.difference(lastUpdate).inMilliseconds > 250 && mounted) {
            setState(() => stage = 'Скачивание модели');
            Diagnostics.instance.stage = 'Скачивание: $received / $total байт, ${watch.elapsed.inSeconds} с';
            lastUpdate = now;
          }
        }
      } finally { await output.close(); }
      if(paused) return;
      if(total > 0 && received != total) throw const HttpException('Загрузка оборвалась; нажмите «Продолжить»');
      if(mounted) setState(() => stage = 'Проверка файла SHA256…');
      await _verify(part);
      await part.rename('${dir.path}/$modelName');
      // Verification happens before rename: provide the final stable path.
      if(mounted) widget.onReady('${dir.path}/$modelName');
    } catch(e) {
      Diagnostics.instance.event('Загрузка: $e');
      if(mounted) setState(() { verified=false; stage = paused ? 'Пауза — можно продолжить' : 'Ошибка: $e'; });
    } finally {
      watch.stop(); client?.close(force:true); client=null;
      if(mounted) setState(() => busy=false);
    }
  }
  @override
  void dispose() { paused=true; client?.close(force:true); super.dispose(); }
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical:12),
    child: Column(crossAxisAlignment:CrossAxisAlignment.start, children:[
      const Text('Illustrious XL 2.0', style:TextStyle(fontWeight:FontWeight.bold)),
      Text(stage),
      if(busy) LinearProgressIndicator(value: total > 0 && stage == 'Скачивание модели' ? received/total : null),
      if(received > 0) Text('${(received/1073741824).toStringAsFixed(2)} / ${total > 0 ? (total/1073741824).toStringAsFixed(2) : "?"} ГиБ${total > 0 ? " · ${(100*received/total).toStringAsFixed(1)}%" : ""}'),
      if(!verified) TextButton.icon(
        icon: Icon(busy ? Icons.pause : Icons.download),
        label:Text(busy ? 'Приостановить' : received > 0 ? 'Продолжить скачивание' : 'Скачать модель'),
        onPressed: !widget.enabled || stage.contains('Проверка') ? null : busy ? () { setState(() { paused=true; stage='Пауза'; }); client?.close(force:true); } : download,
      ),
    ]),
  );
}
