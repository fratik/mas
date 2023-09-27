/*
 * Copyright (c) 2023 fratik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.fratik.mcs;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Backuper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Backuper.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH");
    private final ScheduledExecutorService backupExecutor;
    private final ScheduledFuture<?> backupTask;
    // critical backup = "the first backup of the day"
    // prevents a user from shutting down the server for the first daily backup
    // shutting down the server while a backup in progress is okay, we can handle it
    // we just need to prevent shutting down for the first backup – so we can guarantee that there will be a backup for each day
    // (well, we can guarantee that only when no players are online and MCS actually runs, but you get the idea)
    // this doesn't override interrupts – that's intentional:
    // when you Ctrl+C the server while a backup's in progress, we assume you know what you're doing
    @Getter private volatile boolean criticalBackupInProgress = false;

    public Backuper() {
        backupExecutor = Executors.newSingleThreadScheduledExecutor();
        backupTask = backupExecutor.scheduleWithFixedDelay(this::backup, 0, 5, TimeUnit.MINUTES);
    }

    public boolean shutdown() throws InterruptedException {
        backupExecutor.shutdown();
        if (!backupExecutor.isTerminated()) {
            LOGGER.info("Pozwalam backupom 15s na dokończenie...");
            if (!backupExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                LOGGER.warn("Wymuszam zakończenie.");
                backupExecutor.shutdownNow();
                if (backupExecutor.awaitTermination(10, TimeUnit.SECONDS)) LOGGER.info("Backup przerwany.");
                else {
                    LOGGER.error("Nie udało się przerwać backupu w czasie 10s. Mogą wystąpić problemy z nim – usuń manualnie.");
                    return false;
                }
            }
        }
        return true;
    }

    private void backup() {
        LOGGER.info("Rozpoczynam backup!");
        File directory = new File(Bootstrap.getConfig().getBackupDirectory());
        if (!directory.exists()) {
            try {
                Files.createDirectory(directory.toPath());
            } catch (IOException e) {
                LOGGER.error("Nie udało się utworzyć folderu z backupami!");
                backupTask.cancel(false);
                return;
            }
        }
        Instant now = Instant.now();
        String sdfFormatted = sdf.format(Date.from(now));
        File backupFile = new File(directory, sdfFormatted + ".zip");
        if (backupFile.exists()) {
            LOGGER.info("Backup na obecną godzinę już istnieje, nie duplikuję!");
            return;
        }
        File[] oldBackups = directory.listFiles((dir, name) -> name.startsWith(sdfFormatted.substring(0, 10)));
        if (oldBackups == null || oldBackups.length == 0) criticalBackupInProgress = true;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            for (String include : Bootstrap.getConfig().getBackupInclude()) {
                checkInterruption();
                if (!Paths.get("./", include).normalize().toAbsolutePath().startsWith(Paths.get("./").normalize().toAbsolutePath())) {
                    LOGGER.warn("Ścieżka {} jest nieprawidłowa!", include);
                    continue;
                }
                File includeFile = new File(include);
                if (!includeFile.exists()) {
                    LOGGER.warn("Ścieżka {} nie istnieje!", include);
                } else {
                    compressZip(zos, includeFile);
                }
            }
            Date end = new Date();
            zos.setComment(String.format("Written by MCS.\nBackup start: %s\nBackup end: %s\nBackup took: %d second(s).",
                    Date.from(now), end, Math.round((end.getTime() - now.toEpochMilli()) / 1000d)));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                LOGGER.error("Tworzenie backupu zostało przerwane!");
                Thread.currentThread().interrupt();
            } else LOGGER.error("Nie udało się utworzyć backupu!", e);
            // we don't care if deleting failed or not, remove if possible in case to get rid of the corrupted backup
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete(); // NOSONAR
            return;
        } finally {
            criticalBackupInProgress = false;
        }
        LOGGER.info("Backup ukończony! {}", backupFile);
        int i = 0;
        LOGGER.info("Usuwam stare backupy...");
        if (oldBackups != null) {
            for (File oldBackup : oldBackups) {
                try {
                    Files.delete(oldBackup.toPath());
                    i++;
                } catch (IOException e) {
                    LOGGER.error("Nie udało się usunąć!", e);
                }
            }
        }
        int dayCounter = 0;
        List<Path> retainFiles = new ArrayList<>();
        while (dayCounter < Bootstrap.getConfig().getBackupRetention()) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DAY_OF_MONTH, -dayCounter);
            Instant instant = Instant.ofEpochMilli(cal.getTimeInMillis());
            for (File file : directory.listFiles((dir, name) -> name.startsWith(sdf.format(Date.from(instant)).substring(0, 10)))) {
                retainFiles.add(file.toPath());
            }
            dayCounter++;
        }
        for (File file : directory.listFiles()) {
            if (retainFiles.contains(file.toPath()) || !file.getName().endsWith(".zip")) continue;
            try {
                Files.delete(file.toPath());
                i++;
            } catch (IOException e) {
                LOGGER.error("Nie udało się usunąć!", e);
            }
        }
        LOGGER.info("Gotowe! (usunięto {} backupów)", i);
    }

    private void checkInterruption() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
    }

    private void compressZip(ZipOutputStream zos, File file) throws InterruptedException, IOException {
        checkInterruption();
        String fileName = Paths.get("./").normalize().relativize(file.toPath().normalize()).toString().replace('\\', '/');
        ZipEntry zipEntry = new ZipEntry(file.isDirectory() ? (fileName.endsWith("/") ? fileName : fileName + "/") : fileName);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            zipEntry.setCreationTime(attrs.creationTime());
            zipEntry.setLastModifiedTime(attrs.lastModifiedTime());
            if (!file.isDirectory()) zipEntry.setSize(attrs.size());
        } catch (Exception e) {
            // ignore
        }
        zos.putNextEntry(zipEntry);
        try {
            if (!file.isDirectory()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[2048];
                    int read;
                    while ((read = fis.read(buffer)) > -1) {
                        checkInterruption();
                        zos.write(buffer, 0, read);
                    }
                }
            }
        } finally {
            zos.closeEntry();
        }
        if (file.isDirectory()) {
            for (File listFile : file.listFiles()) {
                compressZip(zos, listFile);
            }
        }
    }
}
