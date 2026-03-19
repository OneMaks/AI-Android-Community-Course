# GigaChat CLI

CLI приложение для взаимодействия с языковой моделью GigaChat.

## Описание

GigaChat CLI - это инструмент командной строки для работы с российской языковой моделью GigaChat. Приложение позволяет вести диалог с AI-ассистентом прямо из терминала.

## Возможности

- 💬 Интерактивный чат с GigaChat в режиме REPL
- ⚙️ Настройка параметров модели (температура, max_tokens, system_prompt)
- 📝 Персистентная история диалогов
- 🔄 Поддержка всех моделей: GigaChat, GigaChat-Pro, GigaChat-Max
- 💾 Экспорт диалогов в текстовый формат
- 🔐 Безопасное хранение конфигурации

## Требования

- JDK 21+ (установлен)
- Credentials key из личного кабинета GigaChat

## Сборка

```bash
# Сборка fat JAR с зависимостями
./gradlew shadowJar

# Очистка и пересборка
./gradlew clean shadowJar
```

После сборки JAR-файл будет доступен по пути: `build/libs/gigachat-cli-all.jar`

## Запуск

```bash
# Запуск приложения
java -jar build/libs/gigachat-cli-all.jar

# Создание алиаса для удобства (добавьте в ~/.zshrc)
alias gigachat="java -jar ~/path/to/gigachat-cli-all.jar"
```

При первом запуске приложение запросит `credentials_key` из личного кабинета GigaChat.

## Конфигурация

Конфигурационные файлы хранятся в `~/.config/gigachat-cli/`:
- `config.json` - основные настройки
- `history/` - сохранённые сессии диалогов

### Пример config.json

```json
{
  "credentials_key": "Basic <base64>",
  "model": "GigaChat",
  "temperature": 0.7,
  "max_tokens": 1024,
  "system_prompt": "Ты — полезный ассистент.",
  "scope": "GIGACHAT_API_PERS",
  "verify_ssl": true
}
```

## Команды

### Навигация и утилиты
- `/help` - Показать справку по всем командам
- `/config` - Вывести текущие настройки сессии
- `/history` - Показать список сохранённых сессий
- `/history <N>` - Показать содержимое N-й сессии
- `/save [path]` - Сохранить диалог в текстовый файл
- `/clear` - Очистить историю текущей сессии
- `/exit` - Сохранить сессию и выйти

### Настройки "на лету"
- `/model <name>` - Сменить модель (GigaChat, GigaChat-Pro, GigaChat-Max)
- `/temp <value>` - Установить температуру (0.0-2.0)
- `/tokens <n>` - Установить лимит токенов (1-32768)
- `/system <text>` - Установить системный промпт
- `/system off` - Отключить системный промпт
- `/set save` - Сохранить текущие настройки в config.json
- `/set reset` - Сбросить настройки к значениям из config.json

## Пример использования

```
GigaChat CLI — модель: GigaChat | /help для справки

You> Привет! Как дела?
GigaChat> Здравствуйте! У меня всё отлично, спасибо. Я готов помочь вам с любыми вопросами.

You> /model GigaChat-Pro
[Настройка применена] model = GigaChat-Pro

You> /temp 0.3
[Настройка применена] temperature = 0.3

You> Расскажи интересный факт про космос
GigaChat> [ответ модели...]

You> /save
[Диалог сохранён в /Users/username/gigachat-2026-03-18_11-43-22.txt]

You> /exit
[Сессия сохранена]
До свидания!
```

## Архитектура

Проект следует модульной архитектуре:

- `api/` - HTTP-клиент для работы с GigaChat API
- `config/` - Управление конфигурацией
- `history/` - Персистентность истории диалогов
- `commands/` - Обработка slash-команд
- `Main.kt` - REPL-цикл и точка входа

## Технологии

- **Kotlin 2.0** - основной язык
- **Ktor Client** - HTTP-запросы
- **kotlinx.serialization** - сериализация JSON
- **kotlinx.coroutines** - асинхронность
- **Shadow Plugin** - сборка fat JAR

## Структура проекта

```
gigachat-cli/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── gigachat-cli-tz.md          # Техническое задание
└── src/
    └── main/
        └── kotlin/
            └── com/gigachat/cli/
                ├── Main.kt
                ├── api/
                │   ├── GigaChatClient.kt
                │   └── Models.kt
                ├── config/
                │   ├── AppConfig.kt
                │   └── ConfigManager.kt
                ├── history/
                │   ├── HistoryManager.kt
                │   └── Session.kt
                └── commands/
                    └── CommandHandler.kt
```

## Лицензия

Проект создан для образовательных целей в рамках AI Android Community Course.
