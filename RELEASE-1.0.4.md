# pnMarket 1.0.4

Крупное обновление pnMarket: надёжные уведомления и автопокупка, постоянные
доставки, общий маршрутизатор хранилищ pnLibrary, локализация предметов RU/EN,
звуковая конфигурация и переработанные GUI-переходы через обязательный ProtocolLib.

## Скриншоты

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Каталог уведомлений</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-catalog.png" alt="Каталог предметов" width="100%">
    </td>
    <td align="center" width="50%">
      <strong>Категории уведомлений</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-categories.png" alt="Категории уведомлений" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-menu-lore.png" alt="Меню уведомлений" height="210">
    </td>
    <td align="center" width="50%">
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-category-lore.png" alt="Описание категории" height="210">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Профили уведомлений</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-profile-lore.png" alt="Профили уведомлений" width="100%">
    </td>
    <td align="center" width="50%">
      <strong>Звуковая конфигурация</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/sounds-config.png" alt="Конфигурация звуков" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <strong>Редактор отдельного профиля</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/notification-profile-editor.png" alt="Редактор профиля уведомления" width="510">
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <strong>Доставки автопокупки</strong><br><br>
      <img src="https://raw.githubusercontent.com/Dy6HiLa/pnMarket/main/docs/screenshots/auto-buy-deliveries.png" alt="Доставки автопокупки" width="345">
    </td>
  </tr>
</table>

## Добавлено

### Уведомления, избранное и автопокупка

- Уведомления `/ah notify` больше не записываются в YAML. Избранное, pending-сообщения и доставки живут в выбранной БД.
- При входе игрок получает только лоты, появившиеся во время его отсутствия и всё ещё доступные: купленные, снятые и истёкшие лоты не присылаются.
- Уведомления группируются по профилю и показывают время появления, количество, цену за единицу и общую цену.
- Нажатие на сообщение открывает конкретный активный лот для покупки.
- Добавлена автопокупка: `Shift+ЛКМ` включает её по текущей цене, `Shift+ПКМ` задаёт собственный предел цены.
- Автопокупка работает для онлайн- и офлайн-игроков. Деньги и количество лота резервируются до выдачи предмета.
- Если инвентарь занят или игрок офлайн, предмет сохраняется в постоянной доставке. Забор — автоматически при входе либо через `/ah delivery` и `/dah delivery`.
- Убраны искусственные лимиты избранного и pending-сообщений.
- Один материал поддерживает независимый профиль без чар и несколько профилей с разными наборами зачарований; профили больше не склеиваются в один.
- После сохранения профиля редактор остаётся открытым: можно сразу собрать следующий вариант того же предмета, не возвращаясь в каталог.

### Хранилище и pnLibrary

- В pnLibrary добавлен публичный `DatabaseRouter` для SQLite, MySQL, MongoDB и Redis.
- Один плагин создаёт один router на весь жизненный цикл: один HikariCP-пул для JDBC, один MongoClient или один JedisPooled.
- JDBC-соединение берётся только на время операции и возвращается сразу после неё. SQLite с пулом `1` больше не блокируется из-за удерживаемых репозиторием connection.
- В pnMarket теперь один контракт `MarketStorage` и по одной реализации на backend: JDBC, MongoDB или Redis. Удалены мелкие классы по отдельности для delivery, favorite и pending.
- Redis хранит лоты, избранное, pending и delivery. Резервирование количества сделано атомарным Lua-скриптом, поэтому один лот не может быть продан одновременно нескольким покупателям.
- Добавлены индексы по игроку, типу аукциона, статусу, времени лота и очереди сообщений.
- Старый `favorites.yml` переносится в выбранную БД один раз и дальше не используется.

### Комиссии, продажа и валюты

- Комиссия за выставление применяется к обычным лотам, наборам и перевыставлению; при ошибке сохранения она возвращается игроку.
- Комиссия за продажу вычитается из выплаты продавцу и корректно показывается в сообщении продавцу.
- Проценты можно задавать отдельно по группам и отдельно для выставления/продажи.
- Для PlayerPoints комиссия округляется вверх до целого значения, для остальных валют — до двух знаков.
- Валюты `/ah` и `/dah` настраиваются независимо: Vault, PlayerPoints или ExcellentEconomy.
- `excellent.id` используется только при `type: excellent`; для Vault и PlayerPoints параметр не нужен и удалён из дефолтного конфига.

### Интерфейс, звуки и локализация

- Все звуки вынесены в `sounds.yml`: у каждого действия доступны `type`, `volume` и `pitch`; `type: NONE` отключает звук.
- Локализация предметов перенесена в публичный API pnLibrary. Доступны `ru_ru` и `en_us` для материалов, блоков, чар и зелий.
- Язык предметов переключается через `localization.locale` и применяется командой `/pnmarket reload`.
- Переключатели обычного и донат-аукциона заменены тематическими Base64-головами.
- Добавлены контекстные GUI-переходы: `left_to_right` идёт из левого верхнего угла в правый нижний, `right_to_left` — обратно.
- Скорость переходов вычисляется библиотекой динамически; технические параметры анимаций не вынесены в конфиг pnMarket.
- ProtocolLib обязателен для GUI-пакетов и динамических заголовков.

## Исправлено

- Исправлена доставка устаревших уведомлений, когда лот успевал исчезнуть до входа игрока.
- Исправлена гонка покупателей одного лота: SQL использует условное атомарное уменьшение, MongoDB — `findOneAndUpdate`, Redis — Lua.
- Исправлен риск операций по визуально пустому слоту во время GUI-анимации: клики блокируются до завершения перехода.
- Исправлен `OPEN_SCREEN` на Paper 1.21.4: registry-объект типа меню не deep-clone'ится.
- `SET_SLOT` использует настоящий server `stateId`; после динамического заголовка GUI синхронизируется повторно.
- Уведомления переведены с ярко-зелёной палитры на бело-серо-оранжевую палитру интерфейса.
- SQLite-драйвер включён в итоговый JAR и не требует отдельной установки на сервер.

## Конфигурация

```yml
currency:
  default:
    enabled: true
    type: vault # vault, playerpoints или excellent
    format: "&a{amount}⛃"
  donate:
    enabled: true
    type: playerpoints
    format: "&d{amount} PP"

# Только при type: excellent:
# currency:
#   default:
#     excellent:
#       id: coins
```

```yml
commission:
  enabled: true
  groups:
    default: { listing: 2.0, sale: 5.0 }
    vip: { listing: 1.0, sale: 3.0 }
    premium: { listing: 0.0, sale: 1.0 }
```

## Обновление

1. Замените JAR на `pnMarket-1.0.4.jar`.
2. Добавьте `sounds.yml`, `localization.locale`, блок `storage` и актуальные строки из `messages.yml`, либо позвольте плагину создать новые значения по умолчанию.
3. Если используете ExcellentEconomy, рекомендуется перенести ID валюты из старого `excellent-id` в `excellent.id`; старый ключ пока совместим.
4. Сохраните `favorites.yml` до первого успешного запуска: данные будут перенесены автоматически.
5. Установите ProtocolLib и выполните полный перезапуск сервера. PlugMan не поддерживается.
