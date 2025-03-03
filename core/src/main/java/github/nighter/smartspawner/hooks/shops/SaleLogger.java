package github.nighter.smartspawner.hooks.shops;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.ConfigManager;
import github.nighter.smartspawner.utils.LanguageManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SaleLogger {
    private static SaleLogger instance;
    private final BlockingQueue<LogEntry> logQueue;
    private final File logFile;
    private final DateTimeFormatter dateFormatter;
    private volatile boolean isRunning;
    private final Thread loggerThread;
    private final ScheduledExecutorService scheduler;
    private static final int QUEUE_CAPACITY = 1000;
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL = 15000; // 15 seconds

    // Custom LogEntry class for better memory management
    private static class LogEntry {
        private final long timestamp;
        private final String playerName;
        private final String itemName;
        private final int amount;
        private final double price;
        private final String currency;
        private final LanguageManager languageManager;

        LogEntry(String playerName, String itemName, int amount, double price, String currency) {
            this.timestamp = System.currentTimeMillis();
            this.playerName = playerName;
            this.itemName = itemName;
            this.amount = amount;
            this.price = price;
            this.currency = currency;
            SmartSpawner plugin = SmartSpawner.getInstance();
            this.languageManager = plugin.getLanguageManager();
        }

        @Override
        public String toString() {
            return String.format("Time: %s | %s sold %d %s from spawner for %s$ (Currency: %s)%n",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(timestamp),
                                    ZoneId.systemDefault())),
                    playerName,
                    amount,
                    itemName,
                    languageManager.formatNumberTenThousand((long) price),
                    currency);
        }
    }

    private SaleLogger() {
        SmartSpawner plugin = SmartSpawner.getInstance();
        ConfigManager configManager = plugin.getConfigManager();
        this.logQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.logFile = new File("plugins/SmartSpawner/" + configManager.getString("logging-file-path"));
        this.isRunning = true;

        // Ensure directory exists
        logFile.getParentFile().mkdirs();

        // Use single thread executor for scheduling
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SaleLogger-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        // Initialize and start logger thread with periodic flush
        this.loggerThread = new Thread(this::processLogQueue, "SaleLogger-Thread");
        this.loggerThread.setDaemon(true);
        this.loggerThread.start();

        // Schedule periodic flush
        scheduler.scheduleWithFixedDelay(
                this::scheduleFlush,
                FLUSH_INTERVAL,
                FLUSH_INTERVAL,
                TimeUnit.MILLISECONDS
        );
    }

    public static synchronized SaleLogger getInstance() {
        if (instance == null) {
            instance = new SaleLogger();
        }
        return instance;
    }

    public void logSale(String playerName, String itemName, int amount, double price, String currency) {
        if (!isRunning) return;

        LogEntry entry = new LogEntry(playerName, itemName, amount, price, currency);

        // Use offer instead of add to prevent blocking
        if (!logQueue.offer(entry)) {
            // If queue is full, trigger immediate flush
            flushLogs();
            // Try again after flush
            logQueue.offer(entry);
        }
    }

    private void scheduleFlush() {
        if (!logQueue.isEmpty()) {
            flushLogs();
        }
    }

    private void processLogQueue() {
        List<LogEntry> batch = new ArrayList<>(BATCH_SIZE);

        while (isRunning) {
            try {
                LogEntry entry = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    batch.add(entry);

                    // Drain more entries if available, up to batch size
                    logQueue.drainTo(batch, BATCH_SIZE - batch.size());

                    if (!batch.isEmpty()) {
                        writeLogsToFile(batch);
                        batch.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Final flush on shutdown
        if (!batch.isEmpty()) {
            writeLogsToFile(batch);
        }
    }

    private void flushLogs() {
        List<LogEntry> batch = new ArrayList<>(logQueue.size());
        logQueue.drainTo(batch);
        if (!batch.isEmpty()) {
            writeLogsToFile(batch);
        }
    }

    private void writeLogsToFile(List<LogEntry> entries) {
        if (entries.isEmpty()) return;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            for (LogEntry entry : entries) {
                writer.write(entry.toString());
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        isRunning = false;
        scheduler.shutdown();
        loggerThread.interrupt();
        try {
            // Wait for scheduler to finish
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            // Wait for logger thread
            loggerThread.join(5000);
            // Final flush
            flushLogs();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}