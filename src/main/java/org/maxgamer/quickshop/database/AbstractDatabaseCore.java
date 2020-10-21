/*
 * This file is a part of project QuickShop, the name is DatabaseCore.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop.database;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractDatabaseCore {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition conditionLock = lock.newCondition();

    void waitForConnection() {
        try {
            lock.lock();
            conditionLock.await();
            lock.unlock();
        } catch (InterruptedException ignored) {
        }
    }

    void signalForNewConnection() {
        lock.lock();
        conditionLock.signal();
        lock.unlock();
    }

    abstract void close();

    abstract DatabaseConnection getConnection();

    abstract public @NotNull String getName();

    abstract public @NotNull Plugin getPlugin();

}