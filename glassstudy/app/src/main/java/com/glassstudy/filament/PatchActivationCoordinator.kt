package com.glassstudy.filament

import android.util.Log

/**
 * Coordinates preview, activation and recovery of signed asset patches.
 * Running on the UI thread is intentional: model replacement and View updates
 * must finish (or roll back) before the next frame can be submitted.
 */
class PatchActivationCoordinator(
    private val updater:PatchManager,
    private val host:Host
) {
    interface Host {
        fun validate(candidate:PatchManager.Candidate)
        fun previewModel(candidate:PatchManager.Candidate):Boolean
        fun previewUi(candidate:PatchManager.Candidate):Boolean
        /** Always try both UI and model restoration from active signed resources. */
        fun restoreActive():Boolean
    }
    enum class Phase(val label:String) {
        VALIDATION("проверка конфигурации"),
        MODEL("загрузка модели"),
        UI("применение интерфейса"),
        ACTIVATION("сохранение версии"),
        COMPLETE("установлен, ожидает подтверждения кадром")
    }
    data class Outcome(
        val installed:Boolean,
        val phase:Phase,
        val message:String,
        val restored:Boolean
    )

    fun rejectUnavailable(candidate:PatchManager.Candidate) {
        updater.reject(candidate)
    }

    fun apply(candidate:PatchManager.Candidate):Outcome {
        var phase=Phase.VALIDATION
        var touched=false
        try {
            host.validate(candidate)
            phase=Phase.MODEL
            touched=true
            check(host.previewModel(candidate)) { "Модель или материалы не прошли проверку" }
            phase=Phase.UI
            check(host.previewUi(candidate)) { "Интерфейс или настройки не применились" }
            phase=Phase.ACTIVATION
            check(updater.activate(candidate)) { "Не удалось сохранить версию" }
            return Outcome(true,Phase.COMPLETE,"",true)
        } catch(e:Exception) {
            Log.e("GlassStudy","Signed patch "+candidate.version+" failed in "+phase.name,e)
            updater.reject(candidate)
            val restored=if(touched) {
                try { host.restoreActive() }
                catch(restoreError:Exception) {
                    Log.e("GlassStudy","Unable to restore the previous scene",restoreError)
                    false
                }
            } else true
            val why=e.message?.replace('\n',' ')?.replace('\r',' ')?.take(160) ?: "неизвестная ошибка"
            val message=phase.label+": "+why+
                if(restored) "; предыдущая версия восстановлена"
                else "; ВНИМАНИЕ: откат неполный, перезапустите приложение"
            updater.reportActivationFailure(candidate.version,message)
            return Outcome(false,phase,message,restored)
        }
    }
}
