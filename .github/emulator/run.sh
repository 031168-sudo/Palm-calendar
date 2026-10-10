#!/bin/bash
# Прогон DateBook на эмуляторе: настоящий календарь Android, нажатия, поворот, складывание.
# $1 — устройство (tablet / fold). Скриншоты — в emu-shots/$1.
set -euo pipefail
DEV="$1"
OUT="emu-shots/$DEV"
mkdir -p "$OUT"
UI="python3 .github/emulator/ui.py"
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "Скриншот: $1"; }

# Эмулятор только загрузился — даём ему успокоиться, системные окна «не отвечает» не показываем
sleep 20
adb shell settings put secure anr_show_background 0 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Тестовые события в календаре
adb shell am instrument -w -e class ru.palmdate.app.SeedCalendar ru.palmdate.app.test/androidx.test.runner.AndroidJUnitRunner | tee seed.log
grep -q "OK (1 test)" seed.log

adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell am start -W -n ru.palmdate.app/.MainActivity
sleep 3
$UI check "Новое"   # заодно закрывает окно «не отвечает», если оно вылезло
shot 1_start

$UI tap "Повестка";             shot 2_agenda
$UI tap "Звонок: Иван Петров";  shot 3_details
$UI check "Закрыть"

# Набираем заметку и поворачиваем экран — окно и текст должны остаться
$UI tap "добавить"
sleep 1
adb shell input text "check123"
shot 4_note_typed
adb shell settings put system user_rotation 1
shot 5_rotated
$UI check "check123"
$UI tap "Сохранить"
shot 6_note_saved
$UI check "Закрыть"
adb shell settings put system user_rotation 0
shot 7_rotated_back
$UI check "Закрыть"

if [ "$DEV" = "fold" ]; then
  # Сложить и разложить на ходу. Без окна эмулятор не умеет менять экран при складывании,
  # поэтому делаем то же, что видит приложение: экран становится вдвое уже, потом снова широким.
  SIZE=$(adb shell wm size | grep -o "[0-9]*x[0-9]*" | head -1)
  W=${SIZE%x*}; H=${SIZE#*x}
  adb shell wm size $((W / 2))x$H
  shot 8_folded
  $UI check "Закрыть"
  $UI check "check123"
  adb shell wm size reset
  shot 9_unfolded
  $UI check "Закрыть"
  # «Ноутбук»: полусложить и положить боком (сгиб горизонтально) — для пробы, не обязательная проверка
  adb shell cmd device_state state 2 || true
  adb shell settings put system user_rotation 1
  shot 9b_tabletop
  adb shell cmd device_state state reset || true
  adb shell settings put system user_rotation 0
  sleep 2
fi

# Новое событие: окно открывается, «Готово» наверху
$UI tap "Закрыть"
$UI tap "Новое";   shot 10_new
$UI tap "Звонок"
shot 11_new_who
echo "Прогон на $DEV прошёл"
