# pnMarket

<p align="center">
  Современный GUI-аукцион для Minecraft-серверов Paper.<br>
  Несколько валют, уведомления о товарах, комиссии, наборы и полностью настраиваемый интерфейс.
</p>

<p align="center">
  <a href="https://github.com/pnFolder/pnMarket/releases/latest"><img src="https://img.shields.io/badge/Скачать-v1.2.0-68FB3C?style=for-the-badge&labelColor=17241F" alt="Скачать pnMarket 1.2.0"></a>
  <a href="https://discord.gg/SZxPP9surw"><img src="https://img.shields.io/badge/Discord-Поддержка-5865F2?style=for-the-badge&labelColor=17241F" alt="Discord"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-17241F?style=for-the-badge" alt="MIT"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.16.5--1.21.x-5A8DEE?style=flat-square" alt="Paper 1.16.5–1.21.x">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square" alt="Java 17+">
  <img src="https://img.shields.io/badge/Storage-SQLite%20%7C%20MySQL%20%7C%20MongoDB%20%7C%20Redis-429F91?style=flat-square" alt="SQLite, MySQL, MongoDB и Redis">
  <img src="https://img.shields.io/badge/Economy-Vault%20%7C%20PlayerPoints%20%7C%20ExcellentEconomy-D66CFF?style=flat-square" alt="Поддерживаемые экономики">
</p>

<p align="center">
  <a href="#возможности">Возможности</a> ·
  <a href="#скриншоты">Скриншоты</a> ·
  <a href="#команды">Команды</a> ·
  <a href="#настройка">Настройка</a> ·
  <a href="#установка">Установка</a>
</p>

---

## Возможности

| Раздел | Возможности |
| --- | --- |
| Аукцион | Страницы, категории, сортировка, поиск, просмотр продавца и частичная покупка стака. |
| Продажа | Обычные лоты, наборы предметов, автоматическая оценка `/ah sell auto` и перевыставление истёкших товаров. |
| Уведомления | GUI-каталог всех предметов и интерактивные сообщения о подходящих активных лотах, найденных в том числе во время отсутствия игрока. |
| Зачарования | Один профиль может требовать несколько совместимых чар нужного уровня на одном предмете. |
| Зелья | Обычные, усиленные, длительные, взрывные и туманные зелья, а также стрелы со всеми эффектами. |
| Валюты | Независимая настройка `/ah` и `/dah` через Vault, PlayerPoints или ExcellentEconomy. |
| Цены | Полный и сокращённый формат чисел: `K`, `M`, `B`, `T`, `Q`. |
| Комиссии | Отдельный процент за выставление и продажу, включая скидки для групп привилегий. |
| Интерфейс | Контекстные переходы GUI, локализация предметов RU/EN, настраиваемые Base64-текстуры, lore и расположение слотов. |
| Хранилище | SQLite без отдельного сервера, MySQL, MongoDB и Redis через общий router pnLibrary. |

## Скриншоты

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Каталог предметов</strong><br><br>
      <img src="docs/screenshots/notification-catalog.png" alt="Каталог предметов pnMarket" width="100%">
    </td>
    <td align="center" width="50%">
      <strong>Выбор категории</strong><br><br>
      <img src="docs/screenshots/notification-categories.png" alt="Категории уведомлений pnMarket" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Управление уведомлениями</strong><br><br>
      <img src="docs/screenshots/notification-menu-lore.png" alt="Описание меню уведомлений pnMarket" height="210">
    </td>
    <td align="center" width="50%">
      <strong>Описание категории</strong><br><br>
      <img src="docs/screenshots/notification-category-lore.png" alt="Описание категории pnMarket" height="210">
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <strong>Редактор разметки</strong><br><br>
      <img src="docs/screenshots/layout-editor.png" alt="Редактор разметки pnMarket" width="345">
    </td>
  </tr>
</table>

## Каталог уведомлений

- Откройте каталог командой `/ah notify` или кнопкой «Избранное и уведомления».
- Нажмите ЛКМ по предмету, чтобы получать сообщения обо всех новых лотах этого типа.
- Нажмите ПКМ, чтобы выбрать обязательные зачарования и минимальные уровни.
- Один материал может иметь отдельный профиль без требований и несколько независимых профилей с разными комбинациями зачарований.
- В редакторе чар настройте условия и нажмите по предмету в верхней части, чтобы сохранить новый профиль.
- Все выбранные чары должны одновременно находиться на одном продаваемом предмете.
- Несовместимые зачарования нельзя объединить в один профиль.
- Активные профили можно просмотреть и удалить в отдельном меню.
- После входа плагин показывает только ещё активные лоты, появившиеся во время отсутствия. Наведите на сообщение для просмотра количества и цены; нажмите, чтобы открыть конкретный лот.
- `Shift+ЛКМ` по предмету включает автопокупку одной единицы не дороже цены, показанной в каталоге. Подтверждение не требуется; для набора приобретается весь набор.
- Если текущей цены нет, плагин попросит ввести предел в чат. `Shift+ПКМ` позволяет задать собственную максимальную цену вручную.
- Собственные лоты намеренно не участвуют в уведомлениях и автопокупке; проверять покупку нужно со второго игрока.
- Автопокупка работает и для офлайн-игроков. Купленные предметы сохраняются в выбранной БД и не выбрасываются в мир.
- При входе плагин автоматически переносит в инвентарь всё, что помещается. Остаток доступен через `/ah delivery` или `/dah delivery`.
- Существующий `favorites.yml` при первом запуске новой версии переносится в выбранное хранилище и далее больше не используется.

### Звуки

```yml
gui:
  open: { type: BLOCK_CHEST_OPEN, volume: 0.2, pitch: 1.1 }
  click: { type: UI_BUTTON_CLICK, volume: 0.2, pitch: 1.0 }

action:
  favorite-found: { type: ENTITY_EXPERIENCE_ORB_PICKUP, volume: 0.8, pitch: 1.0 }
  purchase: { type: ENTITY_PLAYER_LEVELUP, volume: 0.2, pitch: 1.25 }
```

Все звуки интерфейса, действий, ошибок и Machine находятся в `sounds.yml`. `type` — имя значения `Sound` из Bukkit/Paper; `volume` и `pitch` принимают значения от `0` до `2`. Чтобы отключить отдельный звук, укажите `type: NONE`. Команда `/pnmarket reload` перечитывает файл.

### Декорации GUI

Декоративные предметы и их слоты можно полностью настроить для каждого окна, включая аукцион,
покупку, наборы, избранное, уведомления и доставку. Поддерживаются обычные материалы, головы
`base64`, `custom-model-data`, свечение, имя и lore. Подробная схема и примеры находятся в
[`docs/gui-decorations.md`](docs/gui-decorations.md).

### Локализация предметов

```yml
localization:
  locale: ru_ru # ru_ru или en_us
```

Названия материалов, блоков, зачарований и зелий предоставляет встроенная pnLibrary.
Обе локали входят в JAR, отдельные файлы скачивать на сервер не требуется.
Изменение применяется командой `/pnmarket reload`.

## Команды

| Команда | Описание |
| --- | --- |
| `/ah` | Открыть обычный аукцион. |
| `/dah` | Открыть донат-аукцион. |
| `/ah sell <цена>` | Выставить предмет из основной руки. |
| `/ah sell auto` | Рассчитать цену по похожим активным лотам. |
| `/ah kit <цена> [название]` | Создать лот-набор из предметов основного инвентаря. |
| `/ah notify` | Открыть каталог уведомлений. |
| `/ah delivery` | Открыть персональное хранилище доставок автопокупки. |
| `/ah search [название]` | Найти товар; без названия используется предмет в руке. |
| `/ah show <игрок>` | Посмотреть активные товары игрока. |
| `/pnmarket` | Показать информацию о плагине и обновлении. |
| `/pnmarket reload` | Перезагрузить конфигурацию. |
| `/pnmarket machine` | Открыть редактор разметки GUI. |

Для донат-аукциона команды `sell`, `sell auto`, `kit`, `notify`, `delivery`, `search` и `show` аналогично доступны через `/dah`.

| Право | Назначение |
| --- | --- |
| `pnmarket.admin` | Панель администратора, перезагрузка, Machine и уведомления об обновлении. |
| `pnmarket.sell.auto` | Автоматическая оценка через `sell auto`. |

## Настройка

### Валюты

Обычный и донат-аукцион включаются и настраиваются независимо.

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
```

`excellent.id` нужен только при `type: excellent`: это ID валюты, созданной в
ExcellentEconomy. Для Vault и PlayerPoints этот параметр не используется.

```yml
currency:
  default:
    enabled: true
    type: excellent
    format: "&a{amount} монет"
    excellent:
      id: coins
```

### Формат и ограничения цены

```yml
price:
  mode: short # short: 1M, full: 1000000
  short:
    decimals: 1
    thousand: K
    million: M
    billion: B
    trillion: T
    quadrillion: Q
  limits:
    default: { min: 1, max: "1M" }
    donate: { min: 1, max: "1M" }
```

### Сроки и лимиты лотов

```yml
sell:
  limits:
    default: 3
    vip: 10
  expiration:
    default: 24h
    groups:
      default: 24h
      vip: 48h
      admin: 7d
```

### Комиссии

```yml
commission:
  enabled: true
  groups:
    default: { listing: 2.0, sale: 5.0 }
    vip: { listing: 1.0, sale: 3.0 }
    premium: { listing: 0.0, sale: 1.0 }
```

### Файлы

```text
plugins/pnMarket/
├── config.yml
├── gui.yml
├── messages.yml
├── sounds.yml
└── market.db
```

### Хранилище

```yml
storage:
  type: sqlite # sqlite, mysql, mongo или redis
  sqlite:
    file: market.db
  mysql:
    url: "" # при заполнении имеет приоритет над host/port/database
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ""
    pool-size: 10
  mongo:
    uri: mongodb://localhost:27017
    database: minecraft
    collection: auction
  redis:
    uri: redis://localhost:6379/0
    namespace: pnmarket
```

Все подсистемы pnMarket используют один публичный `DatabaseRouter` из pnLibrary.
Смена `storage.type` требует полного перезапуска. Для Redis должна быть настроена
персистентность на самом Redis-сервере, если он используется как основное хранилище.

## Установка

1. Скачайте актуальный JAR со страницы [последнего релиза](https://github.com/pnFolder/pnMarket/releases/latest).
2. Поместите JAR в папку `plugins/`.
3. Установите выбранный плагин экономики и его зависимости: Vault, PlayerPoints или ExcellentEconomy.
4. Запустите сервер и настройте `config.yml`, `gui.yml`, `messages.yml` и `sounds.yml`.
5. Выполните полный перезапуск сервера. Не используйте PlugMan для первой установки или смены хранилища.

Профиль, скорость и транспорт переходов управляются pnLibrary и не выводятся в
конфигурацию pnMarket. `left_to_right` идёт из левого верхнего угла в правый
нижний, `right_to_left` — обратно. Размер шага вычисляется динамически, чтобы
меню любого размера раскрылось примерно за восемь кадров. Для GUI обязателен ProtocolLib.

Требования: Paper 1.16.5–1.21.x и Java 17 или новее.

## Сборка

Для этой версии нужен соседний клон `pnFolder/pnLibrary` на коммите
`6053231cffd72fec870b72d6410b18f141741f51`: он подключается через `includeBuild('../pnLibrary')`.
Текущая ветка `main` pnLibrary содержит другой API обновлений и несовместима с pnMarket 1.2.0.
Используйте JDK 17–22 для запуска Gradle 8.8; готовый плагин рассчитан на Java 17+.

```powershell
git clone https://github.com/pnFolder/pnLibrary.git ../pnLibrary
git -C ../pnLibrary checkout 6053231cffd72fec870b72d6410b18f141741f51
./gradlew.bat --no-daemon test
./gradlew.bat --no-daemon shadowJar
```

Готовый файл: `build/libs/pnMarket-1.2.0.jar`.

## Поддержка

- [Discord](https://discord.gg/SZxPP9surw)
- [GitHub Issues](https://github.com/Dy6HiLa/pnMarket/issues)

## Лицензия

[MIT](LICENSE)
