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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class GeLifecyclePluginBackfillSchedulerTest
{
    @Test
    public void requestBackfillAttemptPrefersSoonestScheduledFuture() throws Exception
    {
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        try
        {
            setField(plugin, "scheduler", executor);

            invoke(plugin, "requestBackfillAttempt", 30L, true);
            Object retryScheduler = getField(plugin, "backfillRetryScheduler");
            ScheduledFuture<?> first = (ScheduledFuture<?>) getInternalField(retryScheduler, "retryFuture");
            assertNotNull(first);

            invoke(plugin, "requestBackfillAttempt", 60L, false);
            ScheduledFuture<?> second = (ScheduledFuture<?>) getInternalField(retryScheduler, "retryFuture");
            assertSame(first, second);

            invoke(plugin, "requestBackfillAttempt", 5L, false);
            ScheduledFuture<?> third = (ScheduledFuture<?>) getInternalField(retryScheduler, "retryFuture");
            assertNotSame(first, third);
            assertTrue(first.isCancelled());
        }
        finally
        {
            invoke(plugin, "resetBackfillRetryState");
            executor.shutdownNow();
        }
    }

    @Test
    public void scheduleBackfillRetryBacksOffAndResetClearsState() throws Exception
    {
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        try
        {
            setField(plugin, "scheduler", executor);

            invoke(plugin, "scheduleBackfillRetry");
            Object retryScheduler = getField(plugin, "backfillRetryScheduler");

            @SuppressWarnings("unchecked")
            ScheduledFuture<?> scheduled = (ScheduledFuture<?>) getInternalField(retryScheduler, "retryFuture");
            assertNotNull(scheduled);
            long delaySec = Math.max(0L, scheduled.getDelay(TimeUnit.SECONDS));
            assertTrue(delaySec >= 1L && delaySec <= 90L);

            AtomicLong backoff = (AtomicLong) getInternalField(retryScheduler, "retryDelaySeconds");
            assertEquals(180L, backoff.get());

            invoke(plugin, "resetBackfillRetryState");
            assertNull(getInternalField(retryScheduler, "retryFuture"));
            assertEquals(90L, backoff.get());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static void invoke(Object target, String methodName, Object... args) throws Exception
    {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
        {
            Object arg = args[i];
            if (arg instanceof Long)
            {
                argTypes[i] = long.class;
            }
            else if (arg instanceof Boolean)
            {
                argTypes[i] = boolean.class;
            }
            else
            {
                argTypes[i] = arg != null ? arg.getClass() : Object.class;
            }
        }
        Method method = GeLifecyclePlugin.class.getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static Object getField(Object target, String fieldName) throws Exception
    {
        Field field = GeLifecyclePlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception
    {
        Field field = GeLifecyclePlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getInternalField(Object target, String fieldName) throws Exception
    {
        if (target == null)
        {
            return null;
        }
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
