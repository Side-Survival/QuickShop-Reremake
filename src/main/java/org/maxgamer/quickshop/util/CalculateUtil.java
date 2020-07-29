/*
 * This file is a part of project QuickShop, the name is CalculateUtil.java
 * Copyright (C) sandtechnology <https://github.com/sandtechnology>
 * Copyright (C) Bukkit Commons Studio and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.maxgamer.quickshop.util;

import java.math.BigDecimal;
import java.math.MathContext;

public final class CalculateUtil {
    private static final MathContext mathContext = MathContext.DECIMAL32;

    private CalculateUtil() {
    }

    public static double divide(double number1, double number2) {
        return (BigDecimal.valueOf(number1).divide(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double subtract(double number1, double number2) {
        return (BigDecimal.valueOf(number1).subtract(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double multiply(double number1, double number2) {
        return (BigDecimal.valueOf(number1).multiply(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double add(double number1, double number2) {
        return (BigDecimal.valueOf(number1).add(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }


}
