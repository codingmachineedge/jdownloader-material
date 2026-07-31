package org.jdownloader.material.engine;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Properties;

/**
 * Full-settings backup: export/import of every supported {@link Settings}
 * property to a single {@code .jdmbackup} file.
 * <p>
 * The whole payload is encrypted with
 * AES-256-GCM under a key derived from the user's passphrase via
 * PBKDF2-HmacSHA256 (210k iterations, random salt), so nothing about the
 * configuration leaks from the file and tampering fails authentication.
 * File layout: magic {@code JDM1} · 16-byte salt · 12-byte IV · ciphertext.
 */
public final class SettingsIO {

    private static final byte[] MAGIC = {'J', 'D', 'M', '1'};
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 210_000;
    private static final long MAX_BACKUP_BYTES = 8L * 1024 * 1024;

    private SettingsIO() {
    }

    /** Thrown when a backup can't be read: wrong passphrase, corrupt or foreign file. */
    public static final class BackupException extends Exception {
        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------------------------------- Export
    public static void exportTo(Path file, Settings s, char[] passphrase)
            throws IOException, GeneralSecurityException {
        exportTo(file, snapshot(s), passphrase);
    }

    /** Captures settings on the JavaFX thread before background encryption begins. */
    public static Properties snapshot(Settings s) {
        Properties p = new Properties();
        p.setProperty("downloadFolder", s.downloadFolderProperty().get());
        p.setProperty("maxSimultaneousDownloads", Integer.toString(s.maxSimultaneousDownloadsProperty().get()));
        p.setProperty("ifFileExists", s.ifFileExistsProperty().get().name());
        p.setProperty("clipboardMonitoring", Boolean.toString(s.clipboardMonitoringProperty().get()));
        p.setProperty("autoConfirm", Boolean.toString(s.autoConfirmProperty().get()));
        p.setProperty("autoStart", Boolean.toString(s.autoStartProperty().get()));
        p.setProperty("addAtTop", Boolean.toString(s.addAtTopProperty().get()));
        p.setProperty("speedLimitEnabled", Boolean.toString(s.speedLimitEnabledProperty().get()));
        p.setProperty("speedLimitKbps", Integer.toString(s.speedLimitKbpsProperty().get()));
        p.setProperty("maxConnectionsPerHost", Integer.toString(s.maxConnectionsPerHostProperty().get()));
        p.setProperty("autoReconnect", Boolean.toString(s.autoReconnectProperty().get()));
        p.setProperty("darkTheme", Boolean.toString(s.darkThemeProperty().get()));
        p.setProperty("speedInTitle", Boolean.toString(s.speedInTitleProperty().get()));
        p.setProperty("language", s.languageProperty().get().name());
        p.setProperty("englishFunnyLevel", Integer.toString(s.englishFunnyLevelProperty().get()));
        p.setProperty("cantoneseFunnyLevel", Integer.toString(s.cantoneseFunnyLevelProperty().get()));
        p.setProperty("funnyLevelDisclosed", Boolean.toString(s.funnyLevelDisclosedProperty().get()));
        p.setProperty("dimSumSurpriseEnabled", Boolean.toString(s.dimSumSurpriseEnabledProperty().get()));
        p.setProperty("firstRunCompleted", Boolean.toString(s.firstRunCompletedProperty().get()));
        p.setProperty("reducedMotion", Boolean.toString(s.reducedMotionProperty().get()));
        p.setProperty("quietHours", Boolean.toString(s.quietHoursProperty().get()));
        p.setProperty("notificationHistoryEnabled", Boolean.toString(s.notificationHistoryEnabledProperty().get()));
        String appearance = s.appearanceProfilePayloadProperty().get();
        if (appearance != null && appearance.length() <= Settings.MAX_APPEARANCE_PROFILE_CHARS) {
            p.setProperty("appearanceProfilePayload", appearance);
        }
        p.setProperty("externalEditorSelection", s.externalEditorSelectionProperty().get());
        p.setProperty("externalEditorCommand", s.externalEditorCommandProperty().get());
        p.setProperty("remoteApiBaseUrl", s.remoteApiBaseUrlProperty().get());
        return p;
    }

    /** Encrypts a previously captured settings snapshot without touching JavaFX state. */
    public static void exportTo(Path file, Properties p, char[] passphrase)
            throws IOException, GeneralSecurityException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        p.store(plain, "JDownloader Material settings backup");

        SecureRandom rng = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        rng.nextBytes(salt);
        rng.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain.toByteArray());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(salt);
        out.write(iv);
        out.write(encrypted);
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tempParent = parent == null ? Path.of(System.getProperty("java.io.tmpdir")) : parent;
        Path temp = Files.createTempFile(tempParent, "jdmbackup-", ".tmp");
        try {
            Files.write(temp, out.toByteArray());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ---------------------------------------------------------------- Import
    public static void importFrom(Path file, Settings s, char[] passphrase)
            throws IOException, BackupException {
        apply(importFrom(file, passphrase), s);
    }

    /** Decrypts a backup without mutating JavaFX settings from a worker thread. */
    public static Properties importFrom(Path file, char[] passphrase)
            throws IOException, BackupException {
        if (Files.size(file) > MAX_BACKUP_BYTES) {
            throw new IOException("Backup file is too large to import safely.");
        }
        byte[] blob = Files.readAllBytes(file);
        if (blob.length < MAGIC.length + SALT_LEN + IV_LEN + 16
                || !Arrays.equals(Arrays.copyOf(blob, MAGIC.length), MAGIC)) {
            throw new BackupException("Not a JDownloader Material backup file.", null);
        }
        byte[] salt = Arrays.copyOfRange(blob, MAGIC.length, MAGIC.length + SALT_LEN);
        byte[] iv = Arrays.copyOfRange(blob, MAGIC.length + SALT_LEN, MAGIC.length + SALT_LEN + IV_LEN);
        byte[] encrypted = Arrays.copyOfRange(blob, MAGIC.length + SALT_LEN + IV_LEN, blob.length);

        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), new GCMParameterSpec(128, iv));
            plain = cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new BackupException("Wrong passphrase, or the file is corrupted.", e);
        }

        Properties p = new Properties();
        p.load(new ByteArrayInputStream(plain));
        return p;
    }

    /** Applies a decoded backup on the JavaFX application thread. */
    public static void apply(Properties p, Settings s) {
        apply(p, "downloadFolder", v -> s.downloadFolderProperty().set(v));
        applyInt(p, "maxSimultaneousDownloads", v -> s.maxSimultaneousDownloadsProperty().set(clamp(v, 1, 10)));
        apply(p, "ifFileExists", v -> s.ifFileExistsProperty().set(Settings.IfExists.valueOf(v)));
        applyBool(p, "clipboardMonitoring", v -> s.clipboardMonitoringProperty().set(v));
        applyBool(p, "autoConfirm", v -> s.autoConfirmProperty().set(v));
        applyBool(p, "autoStart", v -> s.autoStartProperty().set(v));
        applyBool(p, "addAtTop", v -> s.addAtTopProperty().set(v));
        applyBool(p, "speedLimitEnabled", v -> s.speedLimitEnabledProperty().set(v));
        applyInt(p, "speedLimitKbps", v -> s.speedLimitKbpsProperty().set(clamp(v, 128, 20_000)));
        applyInt(p, "maxConnectionsPerHost", v -> s.maxConnectionsPerHostProperty().set(clamp(v, 1, 20)));
        applyBool(p, "autoReconnect", v -> s.autoReconnectProperty().set(v));
        applyBool(p, "darkTheme", v -> s.darkThemeProperty().set(v));
        applyBool(p, "speedInTitle", v -> s.speedInTitleProperty().set(v));
        apply(p, "language", v -> s.languageProperty().set(LanguageMode.valueOf(v)));
        applyInt(p, "englishFunnyLevel", v -> s.englishFunnyLevelProperty().set(clamp(v, 1, 5)));
        applyInt(p, "cantoneseFunnyLevel", v -> s.cantoneseFunnyLevelProperty().set(clamp(v, 1, 5)));
        applyBool(p, "funnyLevelDisclosed", v -> s.funnyLevelDisclosedProperty().set(v));
        applyBool(p, "dimSumSurpriseEnabled", v -> s.dimSumSurpriseEnabledProperty().set(v));
        applyBool(p, "firstRunCompleted", v -> s.firstRunCompletedProperty().set(v));
        applyBool(p, "reducedMotion", v -> s.reducedMotionProperty().set(v));
        applyBool(p, "quietHours", v -> s.quietHoursProperty().set(v));
        applyBool(p, "notificationHistoryEnabled", v -> s.notificationHistoryEnabledProperty().set(v));
        apply(p, "appearanceProfilePayload", s::setAppearanceProfilePayload);
        apply(p, "externalEditorSelection", v -> s.externalEditorSelectionProperty().set(v));
        apply(p, "externalEditorCommand", v -> s.externalEditorCommandProperty().set(v));
        apply(p, "remoteApiBaseUrl", v -> s.remoteApiBaseUrlProperty().set(v));
        // Older encrypted backups may include retired settings. Unknown entries
        // are deliberately ignored so
        // importing an existing backup still restores every supported setting.
    }

    // ---------------------------------------------------------------- Crypto
    private static SecretKeySpec deriveKey(char[] passphrase, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    // ---------------------------------------------------------------- Helpers
    private interface Setter<T> { void set(T value); }

    private static void apply(Properties p, String key, Setter<String> setter) {
        String v = p.getProperty(key);
        if (v != null) {
            try { setter.set(v); } catch (IllegalArgumentException ignored) { }
        }
    }

    private static void applyInt(Properties p, String key, Setter<Integer> setter) {
        String v = p.getProperty(key);
        if (v != null) {
            try { setter.set(Integer.parseInt(v)); } catch (NumberFormatException ignored) { }
        }
    }

    private static void applyBool(Properties p, String key, Setter<Boolean> setter) {
        String v = p.getProperty(key);
        if (v != null) setter.set(Boolean.parseBoolean(v));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** UTF-8 chars of a string as a char[] the caller can zero after use. */
    public static char[] chars(String s) {
        return s == null ? new char[0] : s.toCharArray();
    }
}
