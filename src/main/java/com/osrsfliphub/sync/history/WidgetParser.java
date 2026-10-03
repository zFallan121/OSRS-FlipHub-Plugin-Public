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

import java.util.*;
import java.util.regex.*;
import net.runelite.api.widgets.Widget;

final class WidgetParser {
    private static final int WIDGET_GROUP_SIZE = 6;
    private static final Pattern COINS_PATTERN = Pattern.compile("([\\d,]+)[^\\d]*coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern EACH_PATTERN = Pattern.compile("=\\s*([\\d,]+)[^\\d]*each", Pattern.CASE_INSENSITIVE);
    // The game writes "(434,816 - 8,696)" with a non-breaking space after the sign, which \s does
    // not match. EACH_PATTERN misses the same way and is left to: "= N each" is rounded down, so
    // a quantity worked back from it can be wrong, and the total over the icon's quantity gives
    // the same price.
    private static final Pattern GROSS_PATTERN =
        Pattern.compile("\\(([\\d,]+)[\\s\\u00A0]*[-+\\u2212][\\s\\u00A0]*[\\d,]+\\)");
    private static final Pattern STATE_QUANTITY_PATTERN = Pattern.compile("\\bx\\s*([\\d,]+)\\b", Pattern.CASE_INSENSITIVE);

    private WidgetParser() {
    }

    /** A history list can be read once it holds whole rows: six widgets to a trade, at least one. */
    static boolean hasCompleteWidgetGroups(Widget[] widgets) {
        return widgets != null && widgets.length > 0 && widgets.length % WIDGET_GROUP_SIZE == 0;
    }

    /** The trades on screen, or null while the list is still part-drawn. */
    static List<Trade> tryParseReadyTrades(Widget[] widgets) {
        return hasCompleteWidgetGroups(widgets) ? parse(widgets) : null;
    }

    static List<Trade> parse(Widget[] widgets) {
        List<Trade> trades = new ArrayList<>();
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
            Trade trade = parseTrade(
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

    /**
     * One row of the history as a trade.
     *
     * <p>The quantity and coins this returns are what the sync's cursor signatures are
     * built from ({@link GeHistoryCursorService#buildSignature}). A change to how either
     * is read, or to which rows are read at all, makes every stored cursor stop matching,
     * and a cursor that matches nothing is read as a history that rolled over. Bump
     * {@link GeHistoryCursorService#FORMAT_VERSION} with any such change, so stored
     * cursors are retired instead.
     *
     * <p>The price is a long: since 30 Sep 2026 one item can cost more than 2,147,483,647.
     * Held in an int, a purchase at 2,394,000,000 came out negative and the row was dropped;
     * one at 9,199,000,000 came out as 609,065,408.
     */
    static Trade parseTrade(String stateText, int itemId, int quantity, String detailsText) {
        if (itemId <= 0) {
            return null;
        }
        String state = OfferPreviewWidgetParser.normalizeText(stateText);
        if (Str.isBlank(state)) {
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

        String details = OfferPreviewWidgetParser.normalizeText(detailsText);
        long totalCoins = parseCoins(details);
        long eachPrice = parseEachPrice(details);
        int resolvedQuantity = resolveQuantity(quantity, state, totalCoins, eachPrice);
        if (resolvedQuantity <= 0) {
            return null;
        }
        // What the row came to, and what one item came to, are read the same way on both sides.
        // A sale's are what was left after tax: its price before tax is in the brackets, or is
        // worked back from them.
        long totalGp = totalCoins > 0L ? totalCoins : eachPrice * resolvedQuantity;
        if (totalGp <= 0L) {
            return null;
        }
        long unitPrice = eachPrice > 0 ? eachPrice : Math.max(1L, totalGp / resolvedQuantity);
        if (!isBuy) {
            long grossFromBreakdown = parseGrossCoins(details);
            unitPrice = grossFromBreakdown > 0L
                ? Math.max(1L, Math.round((double) grossFromBreakdown / (double) resolvedQuantity))
                : inferGrossUnitPrice(itemId, unitPrice, resolvedQuantity, totalGp);
        }
        return new Trade(itemId, isBuy, resolvedQuantity, unitPrice, totalGp);
    }

    static long parseCoins(String text) {
        if (Str.isBlank(text)) {
            return 0L;
        }
        Matcher matcher = COINS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0L;
        }
        return parseLongDigits(matcher.group(1));
    }

    static long parseEachPrice(String text) {
        if (Str.isBlank(text)) {
            return 0;
        }
        Matcher matcher = EACH_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0;
        }
        return parseLongDigits(matcher.group(1));
    }

    static long parseGrossCoins(String text) {
        if (Str.isBlank(text)) {
            return 0L;
        }
        Matcher matcher = GROSS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0L;
        }
        return parseLongDigits(matcher.group(1));
    }

    static int parseStateQuantity(String stateText) {
        if (Str.isBlank(stateText)) {
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

    static long inferGrossUnitPrice(int itemId, long netUnitPrice, int quantity, long netTotal) {
        if (netUnitPrice <= 0 || quantity <= 0) {
            return 0;
        }
        // Nothing was taken off, so there is nothing to add back. Below fifty coins the tax
        // rounds away to nothing, and an exempt item is never taxed at any price.
        if (netUnitPrice < 50 || GeTax.isExempt(itemId)) {
            return netUnitPrice;
        }
        // From 250,000,000 the tax is its cap and no longer 2%: 2,389,000,000 received was sold
        // at 2,394,000,000, and worked back at 2% it came out 44 million too high.
        if (netUnitPrice >= 49 * GeTax.MAX_TAX_PER_ITEM) {
            return netUnitPrice + GeTax.MAX_TAX_PER_ITEM;
        }
        long approx = (long) Math.ceil(netUnitPrice * 50.0d / 49.0d);
        long start = Math.max(netUnitPrice, approx - 20);
        long end = Math.max(start, approx + 200);
        long best = approx;
        long bestError = Long.MAX_VALUE;
        long bestDistance = Long.MAX_VALUE;
        for (long candidate = start; candidate <= end; candidate++) {
            long netPerItem = candidate - GeTax.perItem(itemId, candidate);
            long impliedNetTotal = netPerItem * quantity;
            long error = netTotal > 0L
                ? Math.abs(impliedNetTotal - netTotal)
                : Math.abs(netPerItem - netUnitPrice);
            long distance = Math.abs(candidate - approx);
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
                                       long eachPrice) {
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

    private static int inferQuantityFromDetails(long totalCoins, long eachPrice) {
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
