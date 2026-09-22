#!/bin/bash
set -e

echo "================================================"
echo "Lugg AI Mod — автоматическая сборка для Linux/macOS"
echo "================================================"
echo

if ! command -v java &> /dev/null; then
    echo "❌ ОШИБКА: Java не установлена!"
    echo "Установи Java 17 JDK:"
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  macOS (brew): brew install openjdk@17"
    exit 1
fi

echo "✅ Java найдена: $(java -version 2>&1 | head -1)"
echo

mkdir -p gradle/wrapper

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    echo "📥 Скачиваю Gradle Wrapper..."
    if command -v wget &> /dev/null; then
        wget -q https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -O gradle/wrapper/gradle-wrapper.jar
    elif command -v curl &> /dev/null; then
        curl -sL https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
    else
        echo "❌ Нет wget/curl чтобы скачать wrapper. Скачай вручную:"
        echo "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
        echo "и положи в gradle/wrapper/"
        exit 1
    fi
fi

chmod +x gradlew
echo "✅ Gradle Wrapper готов"
echo
echo "🚀 Запускаю сборку..."
./gradlew build

echo
echo "================================================"
echo "✅ СБОРКА УСПЕШНО ЗАВЕРШЕНА!"
echo
echo "Готовый мод: $(pwd)/build/libs/"
echo "================================================"
