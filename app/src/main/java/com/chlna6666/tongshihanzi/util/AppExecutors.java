/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.util;
import java.util.concurrent.ExecutorService;import java.util.concurrent.Executors;import java.util.concurrent.ScheduledExecutorService;
public final class AppExecutors {private static final ExecutorService IO=Executors.newFixedThreadPool(Math.max(2,Math.min(4,Runtime.getRuntime().availableProcessors()/2)));private static final ScheduledExecutorService SCHEDULED=Executors.newSingleThreadScheduledExecutor();private AppExecutors(){}public static ExecutorService io(){return IO;}public static ScheduledExecutorService scheduled(){return SCHEDULED;}}
