# Обновление из upstream (v2rayng)

## Текущая ситуация
- **Upstream:** https://github.com/2dust/v2rayng
- **Статус:** Сейчас наш форк и upstream на одном уровне (v2.1.3)
- **Наши изменения:** Переименование проекта в proxu (1 коммит поверх upstream)

## Ручной процесс обновления (рекомендуется)

### 1. Получить свежие коммиты из оригинала
```bash
git fetch upstream
```

### 2. Посмотреть что нового
```bash
# Список новых коммитов
git log --oneline HEAD..upstream/master

# Статистика изменений
git diff --stat HEAD..upstream/master

# Подробности конкретного коммита
git show <hash>

# Изменения в конкретном файле
git diff HEAD..upstream/master -- <filepath>
```

### 3. Выборочное применение (cherry-pick)
```bash
# Один коммит
git cherry-pick <hash>

# Несколько коммитов
git cherry-pick <hash1> <hash2>

# Диапазон коммитов
git cherry-pick <hash_start>^..<hash_end>

# Применить без автокоммита (чтобы проверить)
git cherry-pick -n <hash>
```

### 4. При конфликте
```bash
# Посмотреть конфликтующие файлы
git status

# После исправления
git add -A
git cherry-pick --continue

# Или отменить
git cherry-pick --abort
```

## Что будет конфликтовать

Скорее всего конфликты будут в:
- **Файлах, где меняли package** (Java/Kotlin импорты, package declarations)
- **strings.xml** (где заменили "v2rayNG" на "proxu")
- **AndroidManifest.xml** (где поменяли applicationId)
- **build.gradle.kts** (где поменяли outputFileName)

**Файлы без переименования** (логика, сетевой код, UI) обычно применяются без конфликтов.

## Альтернатива: git merge (все сразу)

Если хотите применить ВСЕ изменения upstream разом:
```bash
git merge upstream/master
```

Но будете решать конфликты массово, что сложнее.

## Полезные алиасы

Добавьте в `~/.gitconfig`:
```ini
[alias]
    up-log = log --oneline HEAD..upstream/master
    up-diff = diff HEAD..upstream/master
    up-stat = diff --stat HEAD..upstream/master
```

Тогда можно использовать:
```bash
git up-log    # новые коммиты
git up-diff   # полный diff
git up-stat   # статистика файлов
```

## Пример workflow

```bash
# Раз в неделю
git fetch upstream

# Смотрим что нового
git log --oneline HEAD..upstream/master
# Вывод:
# a1b2c3d Fix memory leak in VPN service
# e4f5g6h Update translations
# i7j8k9l Bump version to 2.1.4

# Применяем только багфикс
git cherry-pick a1b2c3d

# Переводы пропускаем (у нас свои strings.xml)
# Версию применяем отдельно, адаптируя под proxu
git cherry-pick i7j8k9l
# [конфликт в build.gradle.kts - решаем вручную]
```

## Важно

- **Не делайте push в upstream** (у вас нет прав)
- **Не удаляйте ветку master** - это ваша основа
- **Сохраняйте оригинальные commit messages** - так легче отслеживать что откуда
