package ru.privatenull.config;

import ru.privatenull.market.MarketFilter.SortType;

import java.util.Locale;

public final class GuiLabels {
    private final GuiConfig gui;

    public GuiLabels(GuiConfig gui) {
        this.gui = gui;
    }

    public String sort(SortType sort) {
        return gui.text("sort." + sort.name().toLowerCase(Locale.ROOT));
    }
}
