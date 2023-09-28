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
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Backuper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Backuper.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH");
    private final ScheduledExecutorService backupExecutor;
    // critical backup = "the first backup of the day"
    // prevents a user from shutting down the server for the first daily backup
    // shutting down the server while a backup in progress is okay, we can handle it
    // we just need to prevent shutting down for the first backup – so we can guarantee that there will be a backup for each day
    // (well, we can guarantee that only when no players are online and MCS actually runs, but you get the idea)
    // this doesn't override interrupts – that's intentional:
    // when you Ctrl+C the server while a backup's in progress, we assume you know what you're doing
    @Getter private volatile boolean criticalBackupInProgress = false;
    private ScheduledFuture<?> task;

    public Backuper() {
        backupExecutor = new ScheduledThreadPoolExecutor(1);
        ((ScheduledThreadPoolExecutor) backupExecutor).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        autobackup();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    private static Date getNextHour() {
        return getNextHour(new Date());
    }

    private static Date getNextHour(Date from) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        cal.add(Calendar.HOUR, 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        return cal.getTime();
    }

    private void autobackup() {
        backup();
        if (!backupExecutor.isShutdown()) task = backupExecutor.schedule(this::backup, getNextHour().getTime(), TimeUnit.MILLISECONDS);
    }

    public boolean shutdown() throws InterruptedException {
        backupExecutor.shutdown();
        if (!backupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
            LOGGER.info("Pozwalam backupom 5s na dokończenie...");
            if (!backupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
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
                backupExecutor.shutdown();
                return;
            }
        }
        for (Iterator<String> iterator = Bootstrap.getConfig().getBackupInclude().iterator(); iterator.hasNext(); ) {
            String include = iterator.next();
            if (!Paths.get("./", include).normalize().toAbsolutePath().startsWith(Paths.get("./").normalize().toAbsolutePath())) {
                LOGGER.warn("Ścieżka {} jest nieprawidłowa!", include);
                iterator.remove();
            }
        }
        Instant now = Instant.now();
        String backupFileName = sdf.format(new Date(now.toEpochMilli())) + ".zip";
        File[] backupsList = directory.listFiles();
        Arrays.sort(backupsList, (a, b) -> {
            try {
                Date date = sdf.parse(b.getName().substring(0, 13));
                return date.compareTo(sdf.parse(a.getName().substring(0, 13)));
            } catch (Exception e) {
                return -1;
            }
        });
        byte[] lastSha256 = null;
        byte[] currSha256 = null;
        if (backupsList.length > 0) {
            try (ZipFile zf = new ZipFile(backupsList[0], ZipFile.OPEN_READ)) {
                lastSha256 = HexUtil.hexToByte(zf.getComment().split("\n\n")[1]);
            } catch (Exception e) {
                // ignore
            }
        }
        if (lastSha256 != null) {
            try {
                MessageDigest dig = MessageDigest.getInstance("SHA-256");
                for (String include : Bootstrap.getConfig().getBackupInclude()) {
                    checkInterruption();
                    File includeFile = new File(include);
                    if (!includeFile.exists()) {
                        LOGGER.warn("Ścieżka {} nie istnieje!", include);
                        continue;
                    }
                    updateDigest(dig, includeFile);
                }
                currSha256 = dig.digest();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Przerwano.");
                return;
            } catch (Exception e) {
                // ignore
            }
        }
        if (lastSha256 != null && Arrays.equals(lastSha256, currSha256)) {
            LOGGER.info("Nie znaleziono zmian w kopii zapasowej, zmieniam datę poprzedniej");
            try {
                Files.move(backupsList[0].toPath(), backupsList[0].toPath().resolveSibling(backupFileName));
            } catch (IOException e) {
                LOGGER.error("Skill issue", e);
            }
            return;
        }
        if (backupsList.length == 0 || isBackupOlderThan24H(backupsList[0].getName())) criticalBackupInProgress = true;
        File backupFile = new File(directory, backupFileName);
        Set<Path> retainFiles = new HashSet<>();
        retainFiles.add(backupFile.toPath());
        if (!backupFile.equals(backupsList[0]) ){
            File[] temp = new File[backupsList.length + 1];
            System.arraycopy(backupsList, 0, temp, 1, backupsList.length);
            temp[0] = backupFile;
            backupsList = temp;
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            for (String include : Bootstrap.getConfig().getBackupInclude()) {
                checkInterruption();
                File includeFile = new File(include);
                if (!includeFile.exists()) {
                    LOGGER.warn("Ścieżka {} nie istnieje!", include);
                    continue;
                }
                compressZip(zos, dig, includeFile);
            }
            Date end = new Date();
            zos.setComment(String.format("Written by MCS.\nBackup start: %s\nBackup end: %s\nBackup took: %d second(s).\n\n%s",
                    Date.from(now), end, Math.round((end.getTime() - now.toEpochMilli()) / 1000d), HexUtil.byteToHex(dig.digest())));
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
        int dayCounter = 0;
        Set<Date> datesEncountered = new HashSet<>();
        if ((backupsList.length - retainFiles.size()) > Bootstrap.getConfig().getBackupRetention()) {
            while (dayCounter < Bootstrap.getConfig().getBackupRetention()) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_MONTH, -dayCounter);
                for (File file : backupsList) {
                    String parsedDate = sdf.format(cal.getTime());
                    if (file.getName().startsWith(parsedDate.substring(0, 10)) && datesEncountered.add(cal.getTime()))
                        retainFiles.add(file.toPath());
                }
                dayCounter++;
            }
        }
        for (File file : backupsList) {
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

    private boolean isBackupOlderThan24H(String fileName) {
        Date date = parseDate(fileName);
        if (date == null) return true;
        return new Date().getTime() - date.getTime() > TimeUnit.HOURS.toMillis(24);
    }

    private Date parseDate(String fileName) {
        try {
            return sdf.parse(fileName.substring(0, 13));
        } catch (StringIndexOutOfBoundsException | ParseException ex) {
            return null;
        }
    }

    private void updateDigest(MessageDigest dig, File f) throws InterruptedException, IOException {
        if (!f.isDirectory()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > -1) {
                    checkInterruption();
                    dig.update(buffer, 0, read);
                }
            }
        } else {
            for (File file : f.listFiles()) {
                updateDigest(dig, file);
            }
        }
    }

    private void checkInterruption() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
    }

    private void compressZip(ZipOutputStream zos, MessageDigest md, File file) throws InterruptedException, IOException {
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
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) > -1) {
                        checkInterruption();
                        zos.write(buffer, 0, read);
                        md.update(buffer, 0, read);
                    }
                }
            }
        } finally {
            zos.closeEntry();
        }
        if (file.isDirectory()) {
            for (File listFile : file.listFiles()) {
                compressZip(zos, md, listFile);
            }
        }
    }
}
