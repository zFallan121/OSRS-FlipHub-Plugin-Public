/*
 * Copyright (c) 2026, zFallan121
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.osrsfliphub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class GeHistoryWidgetParser {
    private static final int WIDGET_GROUP_SIZE = 6;
    private static final Pattern COINS_PATTERN = Pattern.compile("([\\d,]+)[^\\d]*coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern EACH_PATTERN = Pattern.compile("=\\s*([\\d,]+)[^\\d]*each", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROSS_PATTERN = Pattern.compile("\\(([\\d,]+)\\s*[-+\\u2212]\\s*[\\d,]+\\)");
    private static final Pattern STATE_QUANTITY_PATTERN = Pattern.compile("\\bx\\s*([\\d,]+)\\b", Pattern.CASE_INSENSITIVE);

    private GeHistoryWidgetParser() {
    }

    static List<GeHistoryTrade> parse(Widget[] widgets) {
        List<GeHistoryTrade> trades = new ArrayList<>();
        if (widgets == null || widgets.length < WIDGET_GROUP_SIZE) {
            return trades;
        }
        for (int start = 0; start + (WIDGET_GROUP_SIZE - 1) < widgets.length; start += WIDGET_GROUP_SIZE) {
            Widget stateWidget = widgets[start + 2];
            Widget itemWidget = widgets[start + 4];
            Widget detailsWidget = widgets[start + 5];
            if (itemWidget == null) {
                continue;
            }
            GeHistoryTrade trade = parseTrade(
                stateWidget != null ? stateWidget.getText() : null,
                itemWidget.getItemId(),
                itemWidget.getItemQuantity(),
                detailsWidget != null ? detailsWidget.getText() : null
            );
            if (trade != null && trade.isValid()) {
                trades.add(trade);
            }
        }
        return trades;
    }

    static GeHistoryTrade parseTrade(String stateText, int itemId, int quantity, String detailsText) {
        if (itemId <= 0) {
            return null;
        }
        String state = normalizeText(stateText);
        if (state == null || state.trim().isEmpty()) {
            return null;
        }
        String lower = state.trim().toLowerCase(Locale.US);
        boolean isBuy;
        if (lower.startsWith("bought")) {
            isBuy = true;
        } else if (lower.startsWith("sold")) {
            isBuy = false;
        } else {
            return null;
        }

        String details = normalizeText(detailsText);
        long totalCoins = parseCoins(details);
        int eachPrice = parseEachPrice(details);
        int resolvedQuantity = resolveQuantity(quantity, state, totalCoins, eachPrice);
        if (resolvedQuantity <= 0) {
            return null;
        }
        if (isBuy) {
            long totalGp = totalCoins > 0L
                ? totalCoins
                : (eachPrice > 0 ? (long) eachPrice * (long) resolvedQuantity : 0L);
            int unitPrice = eachPrice > 0
                ? eachPrice
                : (resolvedQuantity > 0 && totalGp > 0L ? (int) Math.max(1L, totalGp / resolvedQuantity) : 0);
            if (totalGp <= 0L || unitPrice <= 0) {
                return null;
            }
            return new GeHistoryTrade(itemId, true, resolvedQuantity, unitPrice, totalGp);
        }

        long netTotal = totalCoins > 0L
            ? totalCoins
            : (eachPrice > 0 ? (long) eachPrice * (long) resolvedQuantity : 0L);
        if (netTotal <= 0L) {
            return null;
        }
        int netUnit = eachPrice > 0
            ? eachPrice
            : (int) Math.max(1L, netTotal / resolvedQuantity);
        long grossFromBreakdown = parseGrossCoins(details);
        int grossUnitPrice;
        if (grossFromBreakdown > 0L && resolvedQuantity > 0) {
            grossUnitPrice = (int) Math.max(1L, Math.round((double) grossFromBreakdown / (double) resolvedQuantity));
        } else {
            grossUnitPrice = inferGrossUnitPrice(netUnit, resolvedQuantity, netTotal);
        }
        if (grossUnitPrice <= 0) {
            grossUnitPrice = Math.max(1, netUnit);
        }
        return new GeHistoryTrade(itemId, false, resolvedQuantity, grossUnitPrice, netTotal);
    }

    static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n");
        return Text.removeTags(normalized);
    }

    static long parseCoins(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }
        Matcher matcher = COINS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0L;
        }
        return parseLongDigits(matcher.group(1));
    }

    static int parseEachPrice(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        Matcher matcher = EACH_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0;
        }
        long parsed = parseLongDigits(matcher.group(1));
        if (parsed <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, parsed);
    }

    static long parseGrossCoins(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }
        Matcher matcher = GROSS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0L;
        }
        return parseLongDigits(matcher.group(1));
    }

    static int parseStateQuantity(String stateText) {
        if (stateText == null || stateText.trim().isEmpty()) {
            return 0;
        }
        Matcher matcher = STATE_QUANTITY_PATTERN.matcher(stateText);
        if (!matcher.find()) {
            return 0;
        }
        long parsed = parseLongDigits(matcher.group(1));
        if (parsed <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, parsed);
    }

    static int inferGrossUnitPrice(int netUnitPrice, int quantity, long netTotal) {
        if (netUnitPrice <= 0 || quantity <= 0) {
            return 0;
        }
        if (netUnitPrice < 50) {
            return netUnitPrice;
        }
        int approx = (int) Math.ceil((double) netUnitPrice * 50.0d / 49.0d);
        int start = Math.max(netUnitPrice, approx - 20);
        int end = Math.max(start, approx + 200);
        int best = approx;
        long bestError = Long.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate = start; candidate <= end; candidate++) {
            long netPerItem = candidate - (candidate / 50L);
            long impliedNetTotal = netPerItem * (long) quantity;
            long error = netTotal > 0L
                ? Math.abs(impliedNetTotal - netTotal)
                : Math.abs(netPerItem - (long) netUnitPrice);
            int distance = Math.abs(candidate - approx);
            if (error < bestError
                || (error == bestError && distance < bestDistance)
                || (error == bestError && distance == bestDistance && candidate > best)) {
                best = candidate;
                bestError = error;
                bestDistance = distance;
                if (bestError == 0L && bestDistance == 0) {
                    break;
                }
            }
        }
        return Math.max(netUnitPrice, best);
    }

    private static long parseLongDigits(String input) {
        if (input == null || input.isEmpty()) {
            return 0L;
        }
        StringBuilder digits = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() == 0) {
            return 0L;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int resolveQuantity(int widgetQuantity,
                                       String normalizedStateText,
                                       long totalCoins,
                                       int eachPrice) {
        int stateQuantity = parseStateQuantity(normalizedStateText);
        if (stateQuantity > 0) {
            return stateQuantity;
        }
        int inferredQuantity = inferQuantityFromDetails(totalCoins, eachPrice);
        if (inferredQuantity > 0) {
            return inferredQuantity;
        }
        return Math.max(0, widgetQuantity);
    }

    private static int inferQuantityFromDetails(long totalCoins, int eachPrice) {
        if (totalCoins <= 0L || eachPrice <= 0) {
            return 0;
        }
        if (totalCoins < eachPrice) {
            return 0;
        }
        if ((totalCoins % eachPrice) != 0L) {
            return 0;
        }
        long quantity = totalCoins / eachPrice;
        if (quantity <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, quantity);
    }
}
