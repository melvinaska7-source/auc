# GUI decorations

`pnMarket` supports reusable decorative items in `gui.yml`. A decoration is configured exactly like a normal GUI item and can then be placed into slots of one or more menus.

```yml
decorations:
  dark-background:
    material: BLACK_STAINED_GLASS_PANE
    name: " "
    lore: []

  accent:
    material: ORANGE_STAINED_GLASS_PANE
    name: " "
    lore: []

  custom-frame:
    material: PAPER
    name: "&6Кастомная рамка"
    lore:
      - "&7Обычный предмет используется как декор"
    custom-model-data: 1204
    glow: false

  custom-head:
    base64: "<texture>"
    name: " "
    lore: []

decoration-layouts:
  auction:
    dark-background: [0, 2, 3, 4, 5, 6, 8]
    accent: [1, 7, 9, 17]
    custom-frame: [18, 26]

  favorites:
    dark-background: [0, 8, 9, 17]
    custom-frame: [4, 13]

  notification-catalog:
    custom-head: [45, 53]

  delivery:
    dark-background: [0, 8, 45, 53]
    custom-frame: [4]
```

Supported item properties:

- `material`
- `base64`
- `name`
- `lore`
- `custom-model-data`
- `glow`

Supported layout names:

- `auction`
- `purchase`
- `seller`
- `my-items`
- `bundle-preview`
- `bundle-create`
- `favorites`
- `notification-catalog`
- `delivery`
- `all` - decoration is applied to every pnMarket GUI

A menu-specific layout is applied after `all`, so it can override a global decoration in the same slot.

The old `auction.layout.decor` + `auction.decor` format remains supported as a compatibility fallback for the auction until `decoration-layouts.auction` is configured.
