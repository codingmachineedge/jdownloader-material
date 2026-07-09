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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Properties;

/**
 * Full-settings backup: export/import of every {@link Settings} property,
 * secrets included, to a single {@code .jdmbackup} file.
 * <p>
 * The whole payload — not just the secret fields — is encrypted with
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
        Properties p = new Properties();
        p.setProperty("downloadFolder", s.downloadFolderProperty().get());
        p.setProperty("maxSimultaneousDownloads", Integer.toString(s.maxSimultaneousDownloadsProperty().get()));
        p.setProperty("maxChunksPerDownload", Integer.toString(s.maxChunksPerDownloadProperty().get()));
        p.setProperty("ifFileExists", s.ifFileExistsProperty().get().name());
        p.setProperty("clipboardMonitoring", Boolean.toString(s.clipboardMonitoringProperty().get()));
        p.setProperty("autoConfirm", Boolean.toString(s.autoConfirmProperty().get()));
        p.setProperty("autoStart", Boolean.toString(s.autoStartProperty().get()));
        p.setProperty("addAtTop", Boolean.toString(s.addAtTopProperty().get()));
        p.setProperty("speedLimitEnabled", Boolean.toString(s.speedLimitEnabledProperty().get()));
        p.setProperty("speedLimitKbps", Integer.toString(s.speedLimitKbpsProperty().get()));
        p.setProperty("maxConnectionsPerHost", Integer.toString(s.maxConnectionsPerHostProperty().get()));
        p.setProperty("autoReconnect", Boolean.toString(s.autoReconnectProperty().get()));
        p.setProperty("reconnectMethod", s.reconnectMethodProperty().get());
        p.setProperty("darkTheme", Boolean.toString(s.darkThemeProperty().get()));
        p.setProperty("speedInTitle", Boolean.toString(s.speedInTitleProperty().get()));
        // Secrets — protected by the whole-file encryption below.
        p.setProperty("myjdEmail", s.myjdEmailProperty().get());
        p.setProperty("myjdPassword", s.myjdPasswordProperty().get());

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
        Files.write(file, out.toByteArray());
    }

    // ---------------------------------------------------------------- Import
    public static void importFrom(Path file, Settings s, char[] passphrase)
            throws IOException, BackupException {
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

        apply(p, "downloadFolder", v -> s.downloadFolderProperty().set(v));
        applyInt(p, "maxSimultaneousDownloads", v -> s.maxSimultaneousDownloadsProperty().set(v));
        applyInt(p, "maxChunksPerDownload", v -> s.maxChunksPerDownloadProperty().set(v));
        apply(p, "ifFileExists", v -> s.ifFileExistsProperty().set(Settings.IfExists.valueOf(v)));
        applyBool(p, "clipboardMonitoring", v -> s.clipboardMonitoringProperty().set(v));
        applyBool(p, "autoConfirm", v -> s.autoConfirmProperty().set(v));
        applyBool(p, "autoStart", v -> s.autoStartProperty().set(v));
        applyBool(p, "addAtTop", v -> s.addAtTopProperty().set(v));
        applyBool(p, "speedLimitEnabled", v -> s.speedLimitEnabledProperty().set(v));
        applyInt(p, "speedLimitKbps", v -> s.speedLimitKbpsProperty().set(v));
        applyInt(p, "maxConnectionsPerHost", v -> s.maxConnectionsPerHostProperty().set(v));
        applyBool(p, "autoReconnect", v -> s.autoReconnectProperty().set(v));
        apply(p, "reconnectMethod", v -> s.reconnectMethodProperty().set(v));
        applyBool(p, "darkTheme", v -> s.darkThemeProperty().set(v));
        applyBool(p, "speedInTitle", v -> s.speedInTitleProperty().set(v));
        apply(p, "myjdEmail", v -> s.myjdEmailProperty().set(v));
        apply(p, "myjdPassword", v -> s.myjdPasswordProperty().set(v));
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

    /** UTF-8 chars of a string as a char[] the caller can zero after use. */
    public static char[] chars(String s) {
        return s == null ? new char[0] : s.toCharArray();
    }
}
