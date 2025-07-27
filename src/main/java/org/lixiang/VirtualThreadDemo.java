package org.lixiang;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.lang.reflect.Field;

public class VirtualThreadDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Java虚拟线程演示 (JDK21) ===\n");

        // 1. 基本虚拟线程创建和使用
        basicVirtualThreadExample();

        // 2. 性能对比：虚拟线程 vs 平台线程
        //performanceComparison();

        // 3. 大量虚拟线程创建演示
        //massiveVirtualThreadsExample();

        // 4. 虚拟线程调用链路分析
        virtualThreadCallChainAnalysis();

        // 5. 载体线程（Carrier Thread）分析
        //carrierThreadAnalysis();

        // 6. 虚拟线程状态变化监控
        //virtualThreadStateMonitoring();
    }

    /**
     * 基本虚拟线程创建和使用示例
     */
    private static void basicVirtualThreadExample() throws InterruptedException {
        System.out.println("1. 基本虚拟线程示例");
        System.out.println("-------------------");

        // 方式1: 使用Thread.ofVirtual()创建虚拟线程
        Thread virtualThread1 = Thread.ofVirtual()
                .name("virtual-thread-1")
                .start(() -> {
                    System.out.println("虚拟线程1运行中: " + Thread.currentThread());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("虚拟线程1完成");
                });

        // 方式2: 使用Thread.startVirtualThread()
        Thread virtualThread2 = Thread.startVirtualThread(() -> {
            System.out.println("虚拟线程2运行中: " + Thread.currentThread());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("虚拟线程2完成");
        });

        // 方式3: 使用虚拟线程工厂
        ThreadFactory factory = Thread.ofVirtual().factory();
        Thread virtualThread3 = factory.newThread(() -> {
            System.out.println("虚拟线程3运行中: " + Thread.currentThread());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("虚拟线程3完成");
        });
        virtualThread3.start();

        // 等待所有线程完成
        virtualThread1.join();
        virtualThread2.join();
        virtualThread3.join();

        System.out.println("基本示例完成\n");
    }

    /**
     * 性能对比：虚拟线程 vs 平台线程
     */
    private static void performanceComparison() throws InterruptedException {
        System.out.println("2. 性能对比：虚拟线程 vs 平台线程");
        System.out.println("--------------------------------");

        int taskCount = 10000;

        // 测试虚拟线程性能
        long virtualThreadTime = measureExecutionTime(() -> {
            try {
                runTasksWithVirtualThreads(taskCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 测试平台线程性能（使用线程池）
        long platformThreadTime = measureExecutionTime(() -> {
            try {
                runTasksWithPlatformThreads(taskCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        System.out.printf("任务数量: %d%n", taskCount);
        System.out.printf("虚拟线程执行时间: %d毫秒%n", virtualThreadTime);
        System.out.printf("平台线程执行时间: %d毫秒%n", platformThreadTime);
        System.out.printf("虚拟线程相比平台线程的性能提升: %.2fx%n%n",
                (double) platformThreadTime / virtualThreadTime);
    }

    /**
     * 使用虚拟线程执行任务
     */
    private static void runTasksWithVirtualThreads(int taskCount) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    // 模拟I/O操作
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
    }

    /**
     * 使用平台线程执行任务
     */
    private static void runTasksWithPlatformThreads(int taskCount) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);

        try (var executor = Executors.newFixedThreadPool(100)) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        // 模拟I/O操作
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
    }

    /**
     * 大量虚拟线程创建演示
     */
    private static void massiveVirtualThreadsExample() throws InterruptedException {
        System.out.println("3. 大量虚拟线程创建演示");
        System.out.println("----------------------");

        int threadCount = 100000;
        System.out.printf("创建 %d 个虚拟线程...%n", threadCount);

        Instant start = Instant.now();
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 使用虚拟线程执行器
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, threadCount)
                    .forEach(i -> executor.submit(() -> {
                        try {
                            // 短暂睡眠模拟工作
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    }));
        }

        latch.await();
        Instant end = Instant.now();

        System.out.printf("成功创建并执行了 %d 个虚拟线程%n", threadCount);
        System.out.printf("总执行时间: %d毫秒%n", Duration.between(start, end).toMillis());
        System.out.println("注意：如果使用传统平台线程，这个数量级的线程创建可能会导致内存溢出！");
    }

    /**
     * 测量代码执行时间的工具方法
     */
    private static long measureExecutionTime(Runnable task) {
        Instant start = Instant.now();
        task.run();
        Instant end = Instant.now();
        return Duration.between(start, end).toMillis();
    }

    /**
     * 虚拟线程调用链路分析
     */
    private static void virtualThreadCallChainAnalysis() throws InterruptedException {
        System.out.println("\n4. 虚拟线程调用链路分析");
        System.out.println("======================");

        CountDownLatch latch = new CountDownLatch(1);

        Thread virtualThread = Thread.ofVirtual()
                .name("analysis-virtual-thread")
                .start(() -> {
                    try {
                        System.out.println("=== 应用层信息 ===");
                        Thread currentThread = Thread.currentThread();
                        System.out.println("当前线程: " + currentThread);
                        System.out.println("是否为虚拟线程: " + currentThread.isVirtual());
                        System.out.println("线程ID: " + currentThread.threadId());
                        System.out.println("线程名称: " + currentThread.getName());
                        System.out.println("线程状态: " + currentThread.getState());

                        System.out.println("\n=== JVM层信息 ===");
                        // 打印载体线程信息
                        printCarrierThreadInfo(currentThread);

                        System.out.println("\n=== 调用栈信息 ===");
                        printStackTrace(currentThread);

                        System.out.println("\n=== 模拟I/O阻塞，观察载体线程变化 ===");
                        System.out.println("阻塞前载体线程:");
                        printCarrierThreadInfo(currentThread);

                        // 模拟I/O阻塞
                        Thread.sleep(100);

                        System.out.println("阻塞后载体线程:");
                        printCarrierThreadInfo(currentThread);

                        // 使用LockSupport进行更精确的阻塞控制
                        System.out.println("\n=== 使用LockSupport阻塞 ===");
                        System.out.println("LockSupport.park()前的载体线程:");
                        printCarrierThreadInfo(currentThread);

                        // 短暂park
                        LockSupport.parkNanos(Duration.ofMillis(50).toNanos());

                        System.out.println("LockSupport.park()后的载体线程:");
                        printCarrierThreadInfo(currentThread);

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });

        latch.await();
        System.out.println("虚拟线程调用链路分析完成\n");
    }

    /**
     * 打印载体线程信息
     */
    private static void printCarrierThreadInfo(Thread virtualThread) {
        try {
            // 使用反射获取载体线程信息（这是实现细节，可能在不同JDK版本中变化）
            Field carrierThreadField = virtualThread.getClass().getDeclaredField("carrierThread");
            carrierThreadField.setAccessible(true);
            Thread carrierThread = (Thread) carrierThreadField.get(virtualThread);

            if (carrierThread != null) {
                System.out.println("  载体线程名称: " + carrierThread.getName());
                System.out.println("  载体线程ID: " + carrierThread.threadId());
                System.out.println("  载体线程状态: " + carrierThread.getState());
                System.out.println("  载体线程是否为守护线程: " + carrierThread.isDaemon());
            } else {
                System.out.println("  当前虚拟线程未绑定到载体线程（可能处于挂起状态）");
            }
        } catch (Exception e) {
            // 如果反射失败，使用替代方法
            System.out.println("  无法通过反射获取载体线程信息");
            System.out.println("  当前运行线程信息: " + Thread.currentThread());
        }
    }

    /**
     * 打印调用栈信息
     */
    private static void printStackTrace(Thread thread) {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        System.out.println("调用栈深度: " + stackTrace.length);
        System.out.println("关键调用栈帧:");

        for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
            StackTraceElement element = stackTrace[i];
            System.out.printf("  [%d] %s.%s(%s:%d)%n",
                    i, element.getClassName(), element.getMethodName(),
                    element.getFileName(), element.getLineNumber());
        }

        if (stackTrace.length > 10) {
            System.out.println("  ... 省略其余 " + (stackTrace.length - 10) + " 个栈帧");
        }
    }

    /**
     * 载体线程分析
     */
    private static void carrierThreadAnalysis() throws InterruptedException {
        System.out.println("5. 载体线程（Carrier Thread）分析");
        System.out.println("================================");

        int virtualThreadCount = 20;
        CountDownLatch latch = new CountDownLatch(virtualThreadCount);

        System.out.printf("创建 %d 个虚拟线程，观察载体线程的复用...%n%n", virtualThreadCount);

        for (int i = 0; i < virtualThreadCount; i++) {
            final int threadIndex = i;
            Thread.startVirtualThread(() -> {
                try {
                    System.out.printf("虚拟线程 %d 开始执行%n", threadIndex);

                    // 获取当前的实际执行线程信息
                    Thread actualThread = Thread.currentThread();
                    System.out.printf("  虚拟线程 %d 运行在: %s%n",
                            threadIndex, actualThread.toString());

                    // 模拟一些工作
                    Thread.sleep(100 + (threadIndex % 3) * 50);

                    System.out.printf("虚拟线程 %d 完成执行%n", threadIndex);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            // 稍微错开线程创建时间
            Thread.sleep(10);
        }

        latch.await();

        System.out.println("\n载体线程分析完成");
        System.out.println("观察结果：多个虚拟线程可能会复用同一个载体线程");
        System.out.println("当虚拟线程阻塞时，载体线程可以执行其他虚拟线程\n");
    }

    /**
     * 虚拟线程状态变化监控
     */
    private static void virtualThreadStateMonitoring() throws InterruptedException {
        System.out.println("6. 虚拟线程状态变化监控");
        System.out.println("====================");

        ThreadMXBean threadMX = ManagementFactory.getThreadMXBean();

        // 记录初始线程数量
        long initialThreadCount = threadMX.getThreadCount();
        System.out.println("初始平台线程数量: " + initialThreadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);

        Thread monitoringThread = Thread.ofVirtual()
                .name("monitoring-virtual-thread")
                .start(() -> {
                    try {
                        Thread currentThread = Thread.currentThread();

                        System.out.println("\n=== 虚拟线程生命周期监控 ===");
                        System.out.println("1. 线程创建阶段");
                        System.out.println("   状态: " + currentThread.getState());
                        System.out.println("   是否存活: " + currentThread.isAlive());

                        startLatch.countDown();

                        System.out.println("\n2. 线程运行阶段");
                        System.out.println("   状态: " + currentThread.getState());
                        System.out.println("   是否存活: " + currentThread.isAlive());

                        // 模拟不同的线程状态
                        System.out.println("\n3. 进入TIMED_WAITING状态");
                        Thread.sleep(200);

                        System.out.println("4. 从TIMED_WAITING恢复到RUNNABLE状态");
                        System.out.println("   状态: " + currentThread.getState());

                        // 创建子虚拟线程观察层次结构
                        System.out.println("\n5. 创建子虚拟线程");
                        CountDownLatch childLatch = new CountDownLatch(1);

                        Thread childVirtualThread = Thread.ofVirtual()
                                .name("child-virtual-thread")
                                .start(() -> {
                                    System.out.println("   子虚拟线程执行中: " + Thread.currentThread());
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    childLatch.countDown();
                                });

                        childLatch.await();

                        System.out.println("6. 子虚拟线程执行完成");

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });

        startLatch.await();

        // 监控线程数量变化
        long runningThreadCount = threadMX.getThreadCount();
        System.out.println("\n运行时平台线程数量: " + runningThreadCount);
        System.out.println("线程数量变化: " + (runningThreadCount - initialThreadCount));

        endLatch.await();

        // 等待一下让线程完全结束
        Thread.sleep(100);
        long finalThreadCount = threadMX.getThreadCount();
        System.out.println("结束后平台线程数量: " + finalThreadCount);

        System.out.println("\n=== 关键观察结果 ===");
        System.out.println("• 虚拟线程的创建和销毁对平台线程数量影响很小");
        System.out.println("• 虚拟线程可以嵌套创建，形成层次结构");
        System.out.println("• 虚拟线程在阻塞时会释放载体线程资源");
        System.out.println("• JVM会自动管理虚拟线程与载体线程的映射关系");

        System.out.println("\n虚拟线程调用链路总结:");
        System.out.println("应用层 -> 虚拟线程 -> JVM调度器 -> 载体线程(平台线程) -> OS线程 -> CPU");
    }
}