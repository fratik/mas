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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private int compressFileCount;

    public Backuper() {
        File directory = new File(Bootstrap.getConfig().getBackupDirectory());
        if (!directory.exists()) {
            try {
                Files.createDirectory(directory.toPath());
            } catch (IOException e) {
                throw new IllegalArgumentException("Nie udało się utworzyć folderu z backupami!", e);
            }
        }
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
        if (!backupExecutor.isShutdown())
            backupExecutor.schedule(this::backup,
                    getNextHour().getTime() - new Date().getTime(), TimeUnit.MILLISECONDS);
    }

    public boolean shutdown() throws InterruptedException {
        backupExecutor.shutdown();
        if (!backupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
            LOGGER.info("Pozwalam backupom 5s na dokończenie...");
            if (!backupExecutor.awaitTermination(4, TimeUnit.SECONDS)) {
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
        checkIncludeDirectories();
        if (Bootstrap.getConfig().getBackupInclude().isEmpty()) {
            LOGGER.error("Brak ścieżek do zrobienia backupu!");
            return;
        }
        Instant now = Instant.now();
        String backupFileName = sdf.format(new Date(now.toEpochMilli())) + ".zip";
        File[] backupsList = directory.listFiles();
        Arrays.sort(backupsList, new BackupsComparator());
        byte[] lastSha256 = null;
        byte[] currSha256 = null;
        if (backupsList.length > 0) lastSha256 = getSha256FromZip(backupsList[0]).orElse(null);
        LOGGER.debug("Obliczam sumę kontrolną");
        int fileCount = 0;
        int tempFC = compressFileCount;
        try {
            compressFileCount = 0;
            currSha256 = calculateSha256();
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Suma kontrolna obecnego backupu: {}", HexUtil.byteToHex(currSha256));
            fileCount = compressFileCount;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Przerwano.");
            return;
        } catch (Exception e) {
            // ignore
        } finally {
            compressFileCount = tempFC;
        }
        if (lastSha256 != null && Arrays.equals(lastSha256, currSha256)) {
            LOGGER.info("Nie znaleziono zmian w kopii zapasowej, zmieniam datę poprzedniej");
            changeLastBackupDate(backupsList[0].toPath(), backupFileName);
            LOGGER.info("Gotowe!");
            return;
        }
        if (backupsList.length == 0 || isBackupOlderThan24H(backupsList[0].getName())) setCriticalBackupInProgress(true);
        File backupFile = new File(directory, backupFileName);
        Set<Path> retainFiles = new HashSet<>();
        retainFiles.add(backupFile.toPath());
        if (!backupFile.equals(backupsList[0]) ){
            File[] temp = new File[backupsList.length + 1];
            System.arraycopy(backupsList, 0, temp, 1, backupsList.length);
            temp[0] = backupFile;
            backupsList = temp;
        }
        compressFileCount = 0;
        try {
            byte[] savedSha = createBackup(backupFile, Date.from(now), fileCount);
            if (!Arrays.equals(currSha256, savedSha)) throw new IllegalStateException("Backup data mismatch");
        } catch (InterruptedException e) {
            LOGGER.error("Tworzenie backupu zostało przerwane!");
            Thread.currentThread().interrupt();
            // we don't care if deleting failed or not, remove if possible in case to get rid of the corrupted backup
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete(); // NOSONAR
            return;
        } catch (Exception e) {
            LOGGER.error("Nie udało się utworzyć backupu!", e);
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete(); // NOSONAR
            return;
        } finally {
            setCriticalBackupInProgress(false);
        }
        LOGGER.info("Backup ukończony! {}", backupFile);
        LOGGER.info("Usuwam stare backupy...");
        int i = pruneOldBackups(backupsList, retainFiles);
        LOGGER.info("Gotowe! (usunięto {} backupów)", i);
    }

    private static int pruneOldBackups(File[] backupsList, Set<Path> retainFiles) {
        int i = 0;
        int dayCounter = 0;
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
                if (file.getName().startsWith(parsedDate.substring(0, 10))) {
                    retainFiles.add(file.toPath());
                    break;
                }
            }
            dayCounter++;
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
        return i;
    }

    private byte[] createBackup(File backupFile, Date startDate, int fileCount) throws IOException, NoSuchAlgorithmException, InterruptedException {
        Thread t = null;
        if (fileCount == -1) {
            t = startReportingThread(fileCount);
            t.start();
        }
        LOGGER.debug("Rozpoczynam zapis");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            for (String include : Bootstrap.getConfig().getBackupInclude()) {
                checkInterruption();
                File includeFile = new File(include);
                compressZip(zos, dig, includeFile);
            }
            if (t != null) t.interrupt();
            if (fileCount != -1) report(fileCount);
            LOGGER.debug("Finalizuję plik zip");
            byte[] savedSha = dig.digest();
            Date end = new Date();
            zos.setComment(String.format("Written by MCS.\nBackup start: %s\nBackup end: %s\nBackup took: %d second(s).\n\n%s",
                    startDate, end, Math.round((end.getTime() - startDate.getTime()) / 1000d), HexUtil.byteToHex(savedSha)));
            return savedSha;
        } finally {
            if (t != null && t.isAlive()) t.interrupt();
        }
    }

    private static void changeLastBackupDate(Path backup, String newName) {
        try {
            Files.move(backup, backup.resolveSibling(newName));
        } catch (IOException e) {
            LOGGER.error("Skill issue", e);
        }
    }

    private byte[] calculateSha256() throws NoSuchAlgorithmException, InterruptedException, IOException {
        MessageDigest dig = MessageDigest.getInstance("SHA-256");
        for (String include : Bootstrap.getConfig().getBackupInclude()) {
            checkInterruption();
            compressFileCount = updateDigest(dig, new File(include));
        }
        return dig.digest();
    }

    private static Optional<byte[]> getSha256FromZip(File zipFile) {
        byte[] lastSha256;
        try (ZipFile zf = new ZipFile(zipFile, ZipFile.OPEN_READ)) {
            lastSha256 = HexUtil.hexToByte(zf.getComment().split("\n\n")[1]);
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Suma kontrolna poprzedniego backupu: {}", HexUtil.byteToHex(lastSha256));
            return Optional.of(lastSha256);
        } catch (Exception e) {
            // ignore
            return Optional.empty();
        }
    }

    private void checkIncludeDirectories() {
        for (Iterator<String> iterator = Bootstrap.getConfig().getBackupInclude().iterator(); iterator.hasNext(); ) {
            String include = iterator.next();
            if (!Paths.get("./", include).normalize().toAbsolutePath().startsWith(Paths.get("./").normalize().toAbsolutePath())) {
                LOGGER.warn("Ścieżka {} jest nieprawidłowa!", include);
                iterator.remove();
            } else {
                File includeFile = new File(include);
                if (!includeFile.exists()) {
                    LOGGER.warn("Ścieżka {} nie istnieje!", include);
                    iterator.remove();
                }
            }
        }
    }

    private Thread startReportingThread(int expectedMaxFileCount) {
        Thread t = new Thread(() -> {
            do {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                report(expectedMaxFileCount);
            } while (compressFileCount < expectedMaxFileCount);
        });
        t.setName("BackupStatus");
        t.setDaemon(true);
        return t;
    }

    private void report(int expectedMaxFileCount) {
        LOGGER.info("Postęp: {} / {} ({}%)", compressFileCount, expectedMaxFileCount,
                (int) Math.floor((double) compressFileCount / expectedMaxFileCount * 100d));
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

    private int updateDigest(MessageDigest dig, File f) throws InterruptedException, IOException {
        String fileName = getZipFileName(f);
        int fileCount = 0;
        if (!f.isDirectory()) {
            dig.update(fileName.getBytes(StandardCharsets.UTF_8));
            try (FileInputStream fis = new FileInputStream(f)) {
                fileCount++;
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > -1) {
                    checkInterruption();
                    dig.update(buffer, 0, read);
                }
            }
        } else {
            for (File file : f.listFiles()) {
                fileCount += updateDigest(dig, file);
            }
        }
        return fileCount;
    }

    @NotNull
    private static String getZipFileName(File f) {
        return Paths.get("./").normalize().relativize(f.toPath().normalize()).toString().replace('\\', '/');
    }

    private void checkInterruption() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
    }

    private void compressZip(ZipOutputStream zos, MessageDigest md, File file) throws InterruptedException, IOException {
        checkInterruption();
        String fileName = getZipFileName(file);
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
                md.update(fileName.getBytes(StandardCharsets.UTF_8));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) > -1) {
                        checkInterruption();
                        zos.write(buffer, 0, read);
                        md.update(buffer, 0, read);
                    }
                    compressFileCount++;
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

    private void setCriticalBackupInProgress(boolean newVal) {
        if (this.criticalBackupInProgress != newVal) LOGGER.debug("criticalBackupInProgress: {}", newVal);
        this.criticalBackupInProgress = newVal;
    }

    private static class BackupsComparator implements Comparator<File> {
        @Override
        public int compare(File a, File b) {
            try {
                Date date = sdf.parse(b.getName().substring(0, 13));
                return date.compareTo(sdf.parse(a.getName().substring(0, 13)));
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
